package to.kuudere.anisuge

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.services.AnimatedFrameBytesCache
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
            .bitmapPoolPercentage(0.3)
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_disk"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .defaults {
                size(480, 720)
                precision(coil3.size.Precision.INEXACT)
            }
        SingletonImageLoader.setUnsafe(coilImageLoader)

        // React to system memory pressure: drop APNG raw byte cache (decoded frames are per-composable and GC'd on dispose)
        val appCtx = this
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                    level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                ) {
                    AnimatedFrameBytesCache.onLowMemory()
                    SingletonImageLoader.get(appCtx).memoryCache?.clear()
                }
            }
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {
                AnimatedFrameBytesCache.onLowMemory()
                SingletonImageLoader.get(appCtx).memoryCache?.clear()
            }
        })

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
