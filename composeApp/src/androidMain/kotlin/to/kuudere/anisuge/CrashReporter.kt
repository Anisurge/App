package to.kuudere.anisuge

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
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
    private var latestCrashFile: File? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(
        context: Context,
        backendUrl: String,
        appName: String,
        apiKey: String? = null
    ) {
        if (backendUrl.isBlank()) return

        this.appContext = context.applicationContext
        this.backendUrl = backendUrl.trimEnd('/')
        this.appName = appName
        this.apiKey = apiKey
        this.crashDir = File(this.appContext!!.filesDir, "crash-reporter").apply { mkdirs() }
        this.latestCrashFile = File(this.appContext!!.filesDir, "crash-reporter/latest_crash.json")

        retryPendingCrashes()

        if (installed) return
        installed = true
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildCrashReport(this.appContext!!, throwable)
            runCatching { postCrashBlocking(report) }

            // Save for the crash screen and restart the activity
            runCatching { latestCrashFile?.writeText(report) }

            val intent = this.appContext!!.packageManager
                .getLaunchIntentForPackage(this.appContext!!.packageName)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                }
            if (intent != null) {
                this.appContext!!.startActivity(intent)
            }
            Process.killProcess(Process.myPid())
        }
    }

    /** Delete the latest crash file so the crash screen is not shown on next launch. */
    fun clearLatestCrash() {
        runCatching { latestCrashFile?.delete() }
    }

    /** Read the saved latest crash report if present. */
    fun readLatestCrash(): String? = runCatching {
        latestCrashFile?.takeIf { it.exists() }?.readText()
    }.getOrNull()

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
