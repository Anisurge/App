package to.kuudere.anisuge.platform
import okio.Sink
import okio.sink
import okio.buffer
import androidx.documentfile.provider.DocumentFile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import to.kuudere.anisuge.MainActivity
import to.kuudere.anisuge.R
import to.kuudere.anisuge.services.DownloadService
import android.provider.DocumentsContract

actual val isDesktopPlatform: Boolean = false
actual val isAndroidTvPlatform: Boolean
    get() {
        val uiModeManager = androidAppContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val packageManager = androidAppContext.packageManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            (androidAppContext.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
actual val PlatformName: String = "Android"

actual val AppVersion: String by lazy {
    val packageInfo = androidAppContext.packageManager.getPackageInfo(androidAppContext.packageName, 0)
    packageInfo.versionName!!
}

actual val AppBuildNumber: Int by lazy {
    val packageInfo = androidAppContext.packageManager.getPackageInfo(androidAppContext.packageName, 0)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode
    }
}

@Composable
actual fun LockScreenOrientation(landscape: Boolean) {
    val context = LocalContext.current
    DisposableEffect(landscape) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)

        val originalOrientation = activity.requestedOrientation
        activity.requestedOrientation = if (landscape) {
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
        onDispose {
            activity.requestedOrientation = originalOrientation
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

internal actual fun internalOpenUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    androidAppContext.startActivity(intent)
}

internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun SyncFullscreen(isFullscreen: Boolean) {
    // Android is mostly handled by LockScreenOrientation's insets controller
}




actual fun isFolderWritable(path: String): Boolean {
    if (path.isEmpty()) return true
    
    // Support SAF URIs for SD Cards/Scoped Storage
    if (path.startsWith("content://")) {
        return try {
            val uri = Uri.parse(path)
            // Check if it's a tree URI
            if (DocumentsContract.isTreeUri(uri)) {
                // Check persisted permissions first (efficient)
                val hasPersisted = androidAppContext.contentResolver.persistedUriPermissions.any { 
                    it.uri == uri && it.isWritePermission 
                }
                if (hasPersisted) return true
                
                // If not persisted, check if we can actually write right now
                // We'll use DocumentFile if available, or try a test write if we had a document URI.
                // For trees, DocumentFile is the standard way.
                val document = androidx.documentfile.provider.DocumentFile.fromTreeUri(androidAppContext, uri)
                document?.canWrite() ?: false
            } else {
                // Single document URI
                val document = androidx.documentfile.provider.DocumentFile.fromSingleUri(androidAppContext, uri)
                document?.canWrite() ?: false
            }
        } catch (e: Exception) {
            // If DocumentFile is missing from classpath, this might throw NoClassDefFoundError
            // but we'll add the dependency next.
            e.printStackTrace()
            false
        }
    }

    val file = java.io.File(path)
    return try {
        val testFile = java.io.File(file, ".anisug_test_write")
        if (testFile.createNewFile()) {
            testFile.delete()
            true
        } else {
             file.canWrite()
        }
    } catch (e: Exception) {
        false
    }
}

actual fun persistFolderPermission(path: String) {
    if (path.startsWith("content://")) {
        try {
            val uri = Uri.parse(path)
            if (DocumentsContract.isTreeUri(uri)) {
                androidAppContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual object KmpFileSystem {
    actual fun exists(path: String): Boolean {
        if (path.startsWith("content://")) {
            val doc = getDocumentFromPath(path)
            return doc?.exists() == true
        }
        return java.io.File(path).exists()
    }

    actual fun createDirectories(path: String, mustCreate: Boolean) {
        if (path.startsWith("content://")) {
           getOrCreateDocumentFromPath(path, isDirectory = true)
           return
        }
        val f = java.io.File(path)
        if (!f.exists()) f.mkdirs()
    }

    actual fun sink(path: String, append: Boolean): Sink {
        if (path.startsWith("content://")) {
            val doc = getOrCreateDocumentFromPath(path, isDirectory = false)
            val uri = doc?.uri ?: throw java.io.IOException("Could not create/open $path")
            val pfd = androidAppContext.contentResolver.openFileDescriptor(uri, if (append) "wa" else "w")
                ?: throw java.io.IOException("Failed to open file descriptor for $path")
            return android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd).sink()
        }
        return java.io.File(path).sink(append)
    }

    actual fun delete(path: String, mustExist: Boolean) {
        if (path.startsWith("content://")) {
            val doc = getDocumentFromPath(path)
            doc?.delete()
            return
        }
        java.io.File(path).delete()
    }

    actual fun write(path: String, data: ByteArray) {
        if (path.startsWith("content://")) {
            val doc = getOrCreateDocumentFromPath(path, isDirectory = false)
            doc?.uri?.let { uri ->
                androidAppContext.contentResolver.openOutputStream(uri)?.use { 
                    it.write(data) 
                }
            }
            return
        }
        java.io.File(path).writeBytes(data)
    }

    private fun getDocumentFromPath(path: String): DocumentFile? {
        val uri = Uri.parse(path)
        return if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(androidAppContext, uri)
        } else {
            DocumentFile.fromSingleUri(androidAppContext, uri)
        }
    }

    // This is a naive implementation: it assumes the path string for URIs was constructed 
    // by appending segments to a base URI, which is what DownloadManager does.
    private fun getOrCreateDocumentFromPath(path: String, isDirectory: Boolean): DocumentFile? {
        if (!path.startsWith("content://")) return null
        
        // 1. Find the base tree URI (the part we have permission for)
        val persisted = androidAppContext.contentResolver.persistedUriPermissions
            .filter { it.isWritePermission }
            .map { it.uri.toString() }
            .find { path.startsWith(it) } ?: return null
            
        var currentDoc = DocumentFile.fromTreeUri(androidAppContext, Uri.parse(persisted)) ?: return null
        
        // 2. Extract relative path and traverse
        val relative = path.removePrefix(persisted).trim('/')
        if (relative.isEmpty()) return currentDoc
        
        val segments = relative.split("/")
        for (i in segments.indices) {
            val name = segments[i]
            if (name.isEmpty()) continue
            
            val nextDoc = currentDoc.findFile(name)
            currentDoc = if (nextDoc != null) {
                nextDoc
            } else {
                if (i == segments.size - 1 && !isDirectory) {
                    currentDoc.createFile("application/octet-stream", name) ?: return null
                } else {
                    currentDoc.createDirectory(name) ?: return null
                }
            }
        }
        return currentDoc
    }
}

private const val DOWNLOAD_CHANNEL_ID = "anisurge_downloads"
private const val DOWNLOAD_NOTIF_ID = 1001

actual fun updateDownloadNotification(
    activeTasksCount: Int,
    totalProgress: Float,
    isInitial: Boolean
) {
    val manager = androidAppContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    if (activeTasksCount > 0) {
        val serviceIntent = Intent(androidAppContext, DownloadService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidAppContext.startForegroundService(serviceIntent)
            } else {
                androidAppContext.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (manager.getNotificationChannel(DOWNLOAD_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Active Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows progress of active anime downloads"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    val progressInt = (totalProgress * 100).toInt()
    val contentTitle = if (activeTasksCount == 1) "Downloading Anime" else "Downloading $activeTasksCount items"
    val contentText = if (progressInt >= 0) "$progressInt% total progress" else "Calculating..."
    
    val intent = Intent(androidAppContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
        androidAppContext, 
        0, 
        intent, 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
    )

    val notificationBuilder = NotificationCompat.Builder(androidAppContext, DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(to.kuudere.anisuge.R.mipmap.ic_launcher_foreground)
        .setContentTitle(contentTitle)
        .setContentText(contentText)
        .setProgress(100, progressInt, progressInt <= 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(false)
        .setContentIntent(pendingIntent)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    manager.notify(DOWNLOAD_NOTIF_ID, notificationBuilder.build())
}

actual fun clearDownloadNotification() {
    val manager = androidAppContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(DOWNLOAD_NOTIF_ID)
    try {
        androidAppContext.stopService(Intent(androidAppContext, DownloadService::class.java))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
