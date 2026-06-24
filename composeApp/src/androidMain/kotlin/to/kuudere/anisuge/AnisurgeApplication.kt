package to.kuudere.anisuge

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import to.kuudere.anisuge.platform.androidAppContext

class AnisurgeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialize context for DataStore access before any Activity/Service runs
        androidAppContext = applicationContext

        CrashReporter.init(
            context = this,
            backendUrl = BuildConfig.CRASH_REPORTER_URL,
            appName = BuildConfig.CRASH_REPORTER_APP_NAME,
            apiKey = BuildConfig.CRASH_REPORTER_API_KEY.ifBlank { null }
        )

        // Keep the crash reporter's auth token in sync with the session store.
        // This allows the CrashActivity (running in :crash process) to send
        // authenticated crash reports even though it can't access DataStore.
        applicationScope.launch {
            try {
                AppComponent.sessionStore.sessionFlow.collect { session ->
                    CrashReporter.updateAuthToken(
                        this@AnisurgeApplication,
                        session?.anisurgeToken,
                    )
                }
            } catch (_: Exception) {
                // Best effort — crash reports still work without auth
            }
        }
    }
}
