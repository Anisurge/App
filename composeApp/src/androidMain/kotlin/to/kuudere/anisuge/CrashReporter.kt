package to.kuudere.anisuge

import android.content.Context
import android.os.Build
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

object CrashReporter {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile private var installed = false
    private var backendUrl: String = ""
    private var appName: String = ""
    private var apiKey: String? = null
    private var crashDir: File? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun init(
        context: Context,
        backendUrl: String,
        appName: String,
        apiKey: String? = null
    ) {
        if (backendUrl.isBlank()) return

        val appContext = context.applicationContext
        this.backendUrl = backendUrl.trimEnd('/')
        this.appName = appName
        this.apiKey = apiKey
        this.crashDir = File(appContext.filesDir, "crash-reporter").apply { mkdirs() }

        retryPendingCrashes()

        if (installed) return
        installed = true
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildCrashReport(appContext, throwable)
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
                    postCrashAsync(body) { posted -> if (posted) file.delete() }
                }
        }.start()
    }

    private fun buildCrashReport(context: Context, throwable: Throwable): String {
        return JSONObject()
            .put("appName", appName)
            .put("stackTrace", throwable.stackTraceToString())
            .put("deviceModel", Build.MODEL ?: "unknown")
            .put("androidVersion", Build.VERSION.RELEASE ?: "unknown")
            .put("appVersion", getAppVersion(context))
            .put("timestamp", isoNow())
            .toString()
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
        val request = crashRequest(body)
        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    private fun postCrashAsync(body: String, onComplete: (Boolean) -> Unit) {
        client.newCall(crashRequest(body)).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onComplete(false)

            override fun onResponse(call: Call, response: Response) {
                response.use { onComplete(it.isSuccessful) }
            }
        })
    }

    private fun crashRequest(body: String): Request {
        val builder = Request.Builder()
            .url("$backendUrl/crash")
            .post(body.toRequestBody(jsonMediaType))

        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("x-api-key", it) }
        return builder.build()
    }

    private fun getAppVersion(context: Context): String {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toString()
            }
            "${info.versionName ?: "unknown"} ($code)"
        }.getOrDefault("unknown")
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
