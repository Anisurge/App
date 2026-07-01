package to.kuudere.anisuge

import android.app.ActivityManager
import android.app.Application
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.SingletonImageLoader
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

        // Initialize Coil with memory limits to prevent OOM on low-RAM devices (4GB budget phones)
        // This addresses OOM crashes by reducing the default ~25% heap memory cache.
        val activityManager = getSystemService(ActivityManager::class.java)
        val isLowRamDevice = activityManager?.isLowRamDevice ?: false
        val memoryCachePercent = if (isLowRamDevice) 0.12 else 0.20
        val memoryCache = MemoryCache.Builder()
            .maxSizePercent(this, memoryCachePercent)
            .build()
        val coilImageLoader = ImageLoader.Builder(this)
            .memoryCache(memoryCache)
            // Disk cache left at Coil default.
            // Primary OOM mitigation is the reduced memory cache on low-RAM devices.
            .build()
        SingletonImageLoader.setUnsafe(coilImageLoader)

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
