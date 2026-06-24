package to.kuudere.anisuge

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Standalone crash reporting Activity that runs in a separate process (`:crash`).
 *
 * When the main app process crashes, [CrashReporter] starts this Activity via an
 * explicit Intent with crash data as extras. Because it's in `:crash`, it survives
 * the main process death and can reliably:
 * 1. Display the crash report to the user
 * 2. Send the crash report to the BFF server
 * 3. Allow copying / sharing the stack trace
 * 4. Restart the app
 *
 * Uses plain Android Views — no Compose, no AppComponent, no shared state.
 */
class CrashActivity : Activity() {

    companion object {
        const val EXTRA_STACK_TRACE = "crash_stack_trace"
        const val EXTRA_DEVICE_MODEL = "crash_device_model"
        const val EXTRA_OS_VERSION = "crash_os_version"
        const val EXTRA_APP_VERSION = "crash_app_version"
        const val EXTRA_TIMESTAMP = "crash_timestamp"
        const val EXTRA_CRASH_JSON = "crash_json"

        // Hardcoded to avoid pulling in AppComponent (separate process)
        private const val BFF_CRASH_URL = "https://db.anisurge.qzz.io/v1/crash-report"

        // Colors matching the app's default dark theme
        private const val COLOR_BG = 0xFF000000.toInt()
        private const val COLOR_SURFACE = 0xFF0A0A0A.toInt()
        private const val COLOR_SURFACE_VARIANT = 0xFF161616.toInt()
        private const val COLOR_TEXT = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_MUTED = 0xFF9E9E9E.toInt()
        private const val COLOR_ACCENT = 0xFFBF80FF.toInt()
        private const val COLOR_BORDER = 0x14FFFFFF
        private const val COLOR_ERROR_BG = 0xFF1A0000.toInt()
        private const val COLOR_WARNING = 0xFFFFB74D.toInt()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var sendButton: Button? = null
    private var stackTrace = ""
    private var deviceModel = ""
    private var osVersion = ""
    private var appVersion = ""
    private var timestamp = ""
    private var crashJson = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge dark status/nav bars
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = 0 // dark icons disabled = light icons for dark bg
        }

        // Extract crash data from intent
        stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available"
        deviceModel = intent.getStringExtra(EXTRA_DEVICE_MODEL) ?: Build.MODEL ?: "unknown"
        osVersion = intent.getStringExtra(EXTRA_OS_VERSION) ?: (Build.VERSION.RELEASE ?: "unknown")
        appVersion = intent.getStringExtra(EXTRA_APP_VERSION) ?: getAppVersionFallback()
        timestamp = intent.getStringExtra(EXTRA_TIMESTAMP) ?: isoNow()
        crashJson = intent.getStringExtra(EXTRA_CRASH_JSON) ?: ""

        setContentView(buildUi())

        // Auto-send crash report to BFF in background
        sendCrashToBff(silent = true)
    }

    private fun buildUi(): View {
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding((24 * dp).toInt(), (48 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
        }

        // ⚠ Icon
        root.addView(TextView(this).apply {
            text = "⚠"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
        })

        // Title
        root.addView(TextView(this).apply {
            text = "Something went wrong"
            setTextColor(COLOR_TEXT)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
        })

        // Subtitle
        root.addView(TextView(this).apply {
            text = "The app crashed unexpectedly. The crash report has been sent automatically."
            setTextColor(COLOR_TEXT_MUTED)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * dp).toInt() }
        })

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(COLOR_BORDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply { bottomMargin = (16 * dp).toInt() }
        })

        // Crash details section in a scrollview
        val formattedDetails = buildString {
            appendLine("=== Crash Report ===")
            appendLine("App: $appVersion")
            appendLine("Device: $deviceModel")
            appendLine("OS: Android $osVersion")
            appendLine("Time: $timestamp")
            appendLine()
            appendLine("--- Stack Trace ---")
            append(stackTrace)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { bottomMargin = (16 * dp).toInt() }

            val cardBg = GradientDrawable().apply {
                setColor(COLOR_SURFACE_VARIANT)
                cornerRadius = 12 * dp
            }
            background = cardBg
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }

        scrollView.addView(TextView(this).apply {
            text = formattedDetails
            setTextColor(COLOR_TEXT_MUTED)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setLineSpacing(4 * dp, 1f)
        })

        root.addView(scrollView)

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(COLOR_BORDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply { bottomMargin = (16 * dp).toInt() }
        })

        // Buttons section
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Copy button
        buttonContainer.addView(createButton("Copy Error", COLOR_SURFACE_VARIANT, COLOR_TEXT) {
            copyToClipboard(formattedDetails)
            (it as? Button)?.text = "Copied!"
            Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show()
        })

        // Share button
        buttonContainer.addView(createButton("Share Error", COLOR_SURFACE_VARIANT, COLOR_TEXT) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, formattedDetails)
                putExtra(Intent.EXTRA_SUBJECT, "Anisurge Crash Report")
            }
            startActivity(Intent.createChooser(shareIntent, "Share crash report"))
        })

        // Send to developer button
        sendButton = createButton("Send to Developer", COLOR_SURFACE_VARIANT, COLOR_TEXT) {
            sendCrashToBff(silent = false)
        }
        buttonContainer.addView(sendButton)

        // Restart button (accent colored)
        buttonContainer.addView(createButton("Restart App", COLOR_ACCENT, 0xFF000000.toInt()) {
            restartApp()
        })

        root.addView(buttonContainer)

        return root
    }

    private fun createButton(
        label: String,
        bgColor: Int,
        textColor: Int,
        onClick: (View) -> Unit,
    ): Button {
        val dp = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false

            val bg = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 12 * dp
            }
            background = bg

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * dp).toInt()
            ).apply { bottomMargin = (10 * dp).toInt() }

            setOnClickListener(onClick)
        }
    }

    private fun sendCrashToBff(silent: Boolean) {
        if (!silent) {
            sendButton?.text = "Sending..."
            sendButton?.isEnabled = false
        }

        val body = JSONObject().apply {
            put("body", "Crash report from CrashActivity")
            put("stackTrace", stackTrace)
            put("deviceModel", deviceModel)
            put("osVersion", osVersion)
            put("appVersion", appVersion)
        }.toString()

        // Try to attach auth token from SharedPreferences if available
        val token = readAnisurgeToken()

        val request = Request.Builder()
            .url(BFF_CRASH_URL)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply {
                if (token != null) header("Authorization", "Bearer $token")
            }
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!silent) {
                    runOnUiThread {
                        sendButton?.text = "Failed — tap to retry"
                        sendButton?.isEnabled = true
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!silent) {
                        runOnUiThread {
                            if (it.isSuccessful) {
                                sendButton?.text = "Sent ✓"
                                sendButton?.isEnabled = false
                            } else {
                                sendButton?.text = "Failed — tap to retry"
                                sendButton?.isEnabled = true
                            }
                        }
                    }
                }
            }
        })
    }

    /**
     * Try to read the Anisurge JWT from DataStore/SharedPreferences.
     * DataStore backed by SharedPreferences uses a file named
     * "datastore/settings.preferences_pb" but we can't easily read proto from
     * a different process. Instead, CrashReporter saves the token to a simple
     * shared prefs file for us.
     */
    private fun readAnisurgeToken(): String? {
        return try {
            val prefs = getSharedPreferences("crash_reporter_prefs", Context.MODE_PRIVATE)
            prefs.getString("anisurge_token", null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Report", text))
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (intent != null) {
            startActivity(intent)
        }
        finishAffinity()
        // Kill the crash process after launching restart
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun getAppVersionFallback(): String {
        return runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
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

    override fun onBackPressed() {
        // Don't let back button dismiss the crash screen accidentally
        // User must explicitly tap "Restart App"
        restartApp()
    }
}
