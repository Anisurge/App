package to.kuudere.anisuge

import android.app.Application
import to.kuudere.anisuge.platform.androidAppContext

class AnisurgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize context for DataStore access before any Activity/Service runs
        androidAppContext = applicationContext
    }
}
