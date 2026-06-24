package to.kuudere.anisuge

import android.content.ComponentName
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
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Global crash handler that installs a [Thread.UncaughtExceptionHandler].
 *
 * On unhandled exceptions it:
 * 1. Builds a JSON crash report
 * 2. Saves it to disk (for offline retry on next launch)
 * 3. **Directly launches [CrashActivity]** — which runs in the separate `:crash` process
 *    so it survives this process dying
 * 4. Kills the main process
 *
 * The old approach (save → relaunch main app → show CrashScreen composable) didn't work
 * because calling `previousHandler.uncaughtException()` would kill the process before the
 * relaunch could complete, and relaunching required the whole Compose tree to be healthy.
 */
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
    private var appContext: Context? = null

    fun init(
        context: Context,
        backendUrl: String,
        appName: String,
        apiKey: String? = null
    ) {
        this.appContext = context.applicationContext
        this.backendUrl = backendUrl.trimEnd('/')
        this.appName = appName
        this.apiKey = apiKey
        this.crashDir = File(this.appContext!!.filesDir, "crash-reporter").apply { mkdirs() }

        retryPendingCrashes()

        if (installed) return
        installed = true
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val ctx = this.appContext ?: return@setDefaultUncaughtExceptionHandler

                val report = buildCrashReport(ctx, throwable)

                // Save crash to disk for retry (in case CrashActivity's network call fails)
                saveCrash(report)

                // Persist auth token to a simple SharedPreferences that the :crash process
                // can read (DataStore proto is not readable across processes easily)
                persistTokenForCrashProcess(ctx)

                // Launch CrashActivity in the :crash process
                val intent = Intent().apply {
                    component = ComponentName(ctx, CrashActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

                    putExtra(CrashActivity.EXTRA_STACK_TRACE, throwable.stackTraceToString())
                    putExtra(CrashActivity.EXTRA_DEVICE_MODEL, Build.MODEL ?: "unknown")
                    putExtra(CrashActivity.EXTRA_OS_VERSION, Build.VERSION.RELEASE ?: "unknown")
                    putExtra(CrashActivity.EXTRA_APP_VERSION, getAppVersion(ctx))
                    putExtra(CrashActivity.EXTRA_TIMESTAMP, isoNow())
                    putExtra(CrashActivity.EXTRA_CRASH_JSON, report)
                }
                ctx.startActivity(intent)
            } catch (_: Exception) {
                // If even launching crash activity fails, die silently
            }

            // Kill the main process — CrashActivity is in :crash and will survive
            Process.killProcess(Process.myPid())
            System.exit(2)
        }
    }

    /**
     * Token persistence is handled by [AnisurgeApplication] which observes the
     * sessionFlow and writes to crash_reporter_prefs continuously. By the time
     * a crash occurs, the token is already persisted. This method exists only
     * as a no-op placeholder for documentation.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun persistTokenForCrashProcess(context: Context) {
        // Already handled by AnisurgeApplication's sessionFlow observer.
        // The crash_reporter_prefs SharedPreferences is kept in sync with the
        // latest Anisurge JWT automatically.
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

    /**
     * Call this from the app's normal flow to persist the Anisurge JWT for future crashes.
     * Should be called after login and whenever the token refreshes.
     */
    fun updateAuthToken(context: Context, token: String?) {
        try {
            val crashPrefs = context.getSharedPreferences("crash_reporter_prefs", Context.MODE_PRIVATE)
            if (token.isNullOrBlank()) {
                crashPrefs.edit().remove("anisurge_token").apply()
            } else {
                crashPrefs.edit().putString("anisurge_token", token).apply()
            }
        } catch (_: Exception) {
            // Best effort
        }
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

    private fun postCrashAsync(body: String, onComplete: (Boolean) -> Unit) {
        val request = try {
            crashRequest(body)
        } catch (_: Exception) {
            onComplete(false)
            return
        }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onComplete(false)

            override fun onResponse(call: Call, response: Response) {
                response.use { onComplete(it.isSuccessful) }
            }
        })
    }

    private fun crashRequest(body: String): Request {
        val url = if (backendUrl.isNotBlank()) {
            if (backendUrl.endsWith("/crash")) {
                // If explicitly configured with suffix, keep it
                backendUrl
            } else if (backendUrl.endsWith("/v1")) {
                "$backendUrl/crash-report"
            } else {
                "$backendUrl/v1/crash-report"
            }
        } else {
            "https://db.anisurge.qzz.io/v1/crash-report"
        }

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))

        appContext?.let { ctx ->
            val token = readAnisurgeToken(ctx)
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("x-api-key", it) }
        return builder.build()
    }

    private fun readAnisurgeToken(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences("crash_reporter_prefs", Context.MODE_PRIVATE)
            prefs.getString("anisurge_token", null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
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
