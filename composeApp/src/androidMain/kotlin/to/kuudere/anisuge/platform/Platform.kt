package to.kuudere.anisuge.platform

import okio.Sink
import okio.Source
import okio.sink
import okio.source
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
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import to.kuudere.anisuge.MainActivity
import to.kuudere.anisuge.R
import to.kuudere.anisuge.services.DownloadService
import to.kuudere.anisuge.services.SyncService
import to.kuudere.anisuge.utils.hasNotificationPermission
import android.provider.DocumentsContract
import java.util.UUID

actual val isDesktopPlatform: Boolean = false
actual val isAndroidPlatform: Boolean = true
actual val isAndroidTvPlatform: Boolean
    get() {
        val uiModeManager = androidAppContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val packageManager = androidAppContext.packageManager
        val forcedTvUi = runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    androidAppContext.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(androidAppContext.packageName, PackageManager.GET_META_DATA)
            }
            appInfo.metaData?.getBoolean("to.kuudere.anisuge.FORCE_TV_UI") == true
        }.getOrDefault(false)
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                forcedTvUi ||
                (androidAppContext.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
actual val PlatformName: String = "Android"
actual val UpdatePlatform: String = "android"
actual val UpdateVariant: String
    get() = if (isAndroidTvPlatform) "tv" else "phone"
actual val UpdateFileKey: String
    get() = Build.SUPPORTED_ABIS.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64") } ?: "universal"

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
            insetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

@Composable
actual fun SyncCursorHidden(hidden: Boolean) {
    // No-op on Android.
}


actual fun isFolderWritable(path: String): Boolean {
    if (path.isEmpty()) return true

    // Support SAF URIs for SD Cards/Scoped Storage
    if (path.startsWith("content://")) {
        return isSafFolderWritable(path)
    }

    // Regular file path — on Android 11+ File.canWrite() is unreliable due
    // to scoped storage, so always prefer the test-write approach.
    val file = java.io.File(path)
    val dir = if (file.isDirectory) file else file.parentFile ?: file
    return try {
        val testFile = java.io.File(dir, ".anisug_test_write")
        if (testFile.createNewFile()) {
            testFile.delete()
            true
        } else if (testFile.exists()) {
            // Leftover from a previous crash; try delete + recreate
            testFile.delete()
            val retry = testFile.createNewFile()
            if (retry) testFile.delete()
            retry
        } else {
            // createNewFile returned false without the file existing
            println("[isFolderWritable] test-write blocked: $path")
            
            // On Android 11+, if this is an external storage path but write failed,
            // it might still be writable via SAF. The file picker returns such paths
            // but direct File API access is blocked by scoped storage.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (path.startsWith("/storage/")) {
                    // External storage path where File API write failed
                    // Check if we have ANY write permissions via SAF - if so, assume accessible
                    val hasPersistedSafPermission = androidAppContext.contentResolver.persistedUriPermissions
                        .any { it.isWritePermission }
                    
                    if (hasPersistedSafPermission) {
                        println("[isFolderWritable] SAF permissions exist; treating $path as writable")
                        return true
                    }
                }
            }
            false
        }
    } catch (e: Exception) {
        println("[isFolderWritable] exception for $path: ${e.message}")
        // If we get an exception, it might be due to scoped storage blocking
        // Check if this looks like an external storage path on Android 11+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            path.startsWith("/storage/")) {
            val hasPersistedSafPermission = androidAppContext.contentResolver.persistedUriPermissions
                .any { it.isWritePermission }
            if (hasPersistedSafPermission) {
                println("[isFolderWritable] exception but SAF permissions exist; treating $path as writable")
                return true
            }
        }
        false
    }
}

private fun isSafFolderWritable(path: String): Boolean {
    return try {
        val uri = Uri.parse(path)
        if (DocumentsContract.isTreeUri(uri)) {
            // Compare by normalised URI string — Uri.equals() can differ on encoding
            val uriStr = uri.toString().trimEnd('/')
            val hasPersisted = androidAppContext.contentResolver.persistedUriPermissions.any { perm ->
                perm.isWritePermission && perm.uri.toString().trimEnd('/') == uriStr
            }
            if (hasPersisted) return true

            // Fallback: DocumentFile check
            val document = androidx.documentfile.provider.DocumentFile.fromTreeUri(androidAppContext, uri)
                ?: return false
            if (document.canWrite()) return true

            // Last resort: attempt a real write via DocumentFile
            val testName = ".anisug_test_${System.currentTimeMillis()}"
            val testFile = document.createFile("application/octet-stream", testName)
            if (testFile != null) {
                testFile.delete()
                true
            } else {
                println("[isFolderWritable] SAF write test failed for tree $uriStr")
                false
            }
        } else {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(androidAppContext, uri)
                ?.canWrite() ?: false
        }
    } catch (e: Exception) {
        println("[isFolderWritable] SAF check failed for $path: ${e.message}")
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
        } catch (e: SecurityException) {
            println("[persistFolderPermission] permission denied by system for $path: ${e.message}")
        } catch (e: Exception) {
            println("[persistFolderPermission] failed to persist for $path: ${e.message}")
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

    actual fun source(path: String): okio.Source {
        if (path.startsWith("content://")) {
            val doc = getDocumentFromPath(path)
            val uri = doc?.uri ?: throw java.io.IOException("Could not open $path")
            val pfd = androidAppContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw java.io.IOException("Failed to open file descriptor for $path")
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).source()
        }
        return java.io.File(path).source()
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

    actual fun listDir(path: String): List<String> {
        if (path.startsWith("content://")) {
            val doc = getDocumentFromPath(path)
            return doc?.listFiles()?.map { it.name ?: "" } ?: emptyList()
        }
        return java.io.File(path).listFiles()?.map { it.name } ?: emptyList()
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

private const val SYNC_CHANNEL_ID = "anisurge_sync"
private const val SYNC_NOTIF_ID = 1004

actual fun updateSyncProgressNotification(
    title: String,
    statusText: String,
    progressCurrent: Int,
    progressMax: Int,
) {
    if (!hasNotificationPermission()) return

    val manager = androidAppContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (manager.getNotificationChannel(SYNC_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                SYNC_CHANNEL_ID,
                "Library sync",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress when syncing your list to MAL or AniList"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    try {
        val syncIntent = Intent(androidAppContext, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            androidAppContext.startForegroundService(syncIntent)
        } else {
            androidAppContext.startService(syncIntent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val bigBody = buildString {
        append(statusText.trim())
        append("\n\n")
        append("This may take a while. You can leave this screen — sync continues in the background.")
    }

    val intent = Intent(androidAppContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        androidAppContext,
        SYNC_NOTIF_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
    )

    val shortSummary = statusText.lineSequence().firstOrNull { it.isNotBlank() }?.take(200)
        ?: statusText.take(200)

    val builder = NotificationCompat.Builder(androidAppContext, SYNC_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(shortSummary)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(false)
        .setContentIntent(pendingIntent)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    if (progressMax > 0) {
        val cur = progressCurrent.coerceIn(0, progressMax)
        builder.setProgress(progressMax, cur, false)
    } else {
        builder.setProgress(0, 0, true)
    }

    manager.notify(SYNC_NOTIF_ID, builder.build())
}

actual fun clearSyncProgressNotification() {
    val manager = androidAppContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(SYNC_NOTIF_ID)
    try {
        androidAppContext.stopService(Intent(androidAppContext, SyncService::class.java))
    } catch (_: Exception) {
    }
}

actual fun startNotificationListenerService() {
    to.kuudere.anisuge.notifications.NotificationTopicManager.subscribeToDefaultTopics()
}

actual fun stopNotificationListenerService() {
    to.kuudere.anisuge.notifications.NotificationTopicManager.unsubscribeFromAllTopics()
}

actual fun randomInstallUuid(): String = UUID.randomUUID().toString()

actual fun analyticsPingOs(): String? = Build.VERSION.RELEASE
