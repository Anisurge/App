package to.kuudere.anisuge

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object CrashReporter {
    @Volatile private var installed = false
    private var backendUrl: String = ""
    private var appName: String = ""
    private var apiKey: String? = null
    private var crashDir: File? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun init(backendUrl: String, appName: String, apiKey: String? = null) {
        if (backendUrl.isBlank()) return

        this.backendUrl = backendUrl.trimEnd('/')
        this.appName = appName
        this.apiKey = apiKey
        this.crashDir = File(System.getProperty("user.home"), ".anisurge/crash-reporter").apply { mkdirs() }

        retryPendingCrashes()

        if (installed) return
        installed = true
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildCrashReport(throwable)
            val file = saveCrash(report)
            runCatching { postCrashBlocking(report) }
                .onSuccess { posted -> if (posted) file?.delete() }

            previousHandler?.uncaughtException(thread, throwable)
                ?: kotlin.system.exitProcess(2)
        }
    }

    fun retryPendingCrashes() {
        val dir = crashDir ?: return
        Thread {
            dir.listFiles { file -> file.extension == "json" }
                ?.sortedBy { it.lastModified() }
                ?.forEach { file ->
                    val body = runCatching { file.readText() }.getOrNull() ?: return@forEach
                    if (postCrashBlocking(body)) file.delete()
                }
        }.start()
    }

    private fun buildCrashReport(throwable: Throwable): String {
        return """
            {
              "appName": ${json(appName)},
              "stackTrace": ${json(throwable.stackTraceToString())},
              "deviceModel": ${json(System.getProperty("os.name") ?: "unknown")},
              "androidVersion": ${json(System.getProperty("os.version") ?: "unknown")},
              "appVersion": ${json("${BuildConfig.APP_VERSION} (${BuildConfig.APP_BUILD_NUMBER})")},
              "timestamp": ${json(isoNow())}
            }
        """.trimIndent()
    }

    private fun saveCrash(body: String): File? {
        val dir = crashDir ?: return null
        return runCatching {
            File(dir, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json").apply {
                writeText(body)
            }
        }.getOrNull()
    }

    private fun postCrashBlocking(body: String): Boolean {
        if (backendUrl.isBlank()) return false
        val connection = (URI("$backendUrl/crash").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            apiKey?.takeIf { it.isNotBlank() }?.let { setRequestProperty("x-api-key", it) }
        }

        return runCatching {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            connection.responseCode in 200..299
        }.getOrDefault(false).also {
            connection.disconnect()
        }
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
