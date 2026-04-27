package to.kuudere.anisuge

import android.app.Application
import to.kuudere.anisuge.platform.androidAppContext

class AnisurgeApplication : Application() {
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
    }
}
