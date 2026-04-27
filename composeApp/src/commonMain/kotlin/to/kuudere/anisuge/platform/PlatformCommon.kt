package to.kuudere.anisuge.platform
import androidx.compose.runtime.Composable
import okio.Sink
import okio.Source

expect val isDesktopPlatform: Boolean
expect val isAndroidTvPlatform: Boolean
expect val PlatformName: String
expect val AppVersion: String
expect val AppBuildNumber: Int
expect val UpdatePlatform: String
expect val UpdateVariant: String
expect val UpdateFileKey: String

/** Requests landscape on Android, ignored/different on desktop */
@Composable
expect fun LockScreenOrientation(landscape: Boolean)

/** Intercepts hardware back button on Android, no-op on desktop */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Syncs app fullscreen state with window-level fullscreen on desktop */
@Composable
expect fun SyncFullscreen(isFullscreen: Boolean)

/** Opens a URL in the platform's default browser */
fun openUrl(url: String) {
    internalOpenUrl(url)
}


/** Returns true if the app has permission to write and create files in the given directory. */
expect fun isFolderWritable(path: String): Boolean

/** Persists terminal-level (scoped) directory permissions on Android, no-op elsewhere. */
expect fun persistFolderPermission(path: String)

/** Updates a system notification with the current download progress (Android only, no-op elsewhere) */
expect fun updateDownloadNotification(
    activeTasksCount: Int,
    totalProgress: Float,
    isInitial: Boolean = false
)

/** Clears the download notification when all tasks are finished or cancelled */
expect fun clearDownloadNotification()

/** Bridge for file operations that handles URIs on Android and standard paths elsewhere. */
expect object KmpFileSystem {
    fun exists(path: String): Boolean
    fun createDirectories(path: String, mustCreate: Boolean = false)
    fun sink(path: String, append: Boolean = false): Sink
    fun delete(path: String, mustExist: Boolean = false)
    fun write(path: String, data: ByteArray)
}

/** Converts a raw technical path into a human-friendly display string. */
fun formatDisplayPath(path: String): String {
    if (path.isBlank()) return "Default Downloads"
    
    var display = path
    
    // URI-aware display for Android SAF
    if (display.startsWith("content://")) {
        val decoded = display.replace("%3A", ":").replace("%2F", "/")
        val segments = decoded.split(":")
        display = if (segments.size > 1) {
            val lastPath = segments.last()
            if (display.contains("com.android.externalstorage.documents")) {
                val root = segments[segments.size - 2].substringAfterLast("/")
                if (root == "primary") "Internal Storage > $lastPath"
                else "SD Card ($root) > $lastPath"
            } else lastPath
        } else {
            decoded.substringAfterLast("/")
        }
    } else {
        // Android common paths
        if (display.startsWith("/storage/emulated/0")) {
            display = display.replace("/storage/emulated/0/Download", "Downloads")
            display = display.replace("/storage/emulated/0", "Internal Storage")
        }
        // SD card paths
        val sdCardPattern = Regex("^/storage/([A-F0-9]{4}-[A-F0-9]{4})")
        sdCardPattern.find(display)?.let { match ->
            display = display.replace(match.value, "SD Card")
        }
    }
    
    // Linux/macOS common paths (standard paths)
    val home = System.getProperty("user.home") ?: ""
    if (home.isNotBlank() && display.startsWith(home)) {
        display = display.replace(home + "/Downloads", "Downloads")
        display = display.replace(home, "Home")
    }

    return display.trim('/').replace("/", " > ")
}

internal expect fun internalOpenUrl(url: String)

/** Subscribes to FCM notification topics (Android only, no-op on desktop). */
expect fun startNotificationListenerService()

/** Unsubscribes from FCM notification topics (Android only, no-op on desktop). */
expect fun stopNotificationListenerService()
