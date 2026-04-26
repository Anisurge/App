package to.kuudere.anisuge.platform
import to.kuudere.anisuge.BuildConfig
import okio.Sink
import okio.sink
import okio.buffer
import java.io.File

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/** Desktop-only: provides the WindowState so composables can drive window placement. */
val LocalWindowState = staticCompositionLocalOf<WindowState> {
    error("LocalWindowState not provided — did you forget to provide it in main.kt?")
}

actual val isDesktopPlatform: Boolean = true
actual val isAndroidTvPlatform: Boolean = false
actual val PlatformName: String = System.getProperty("os.name").let { os ->
    val lower = os.lowercase()
    when {
        "windows" in lower -> "Windows"
        "linux" in lower   -> "Linux"
        "mac" in lower     -> "macOS"
        else               -> "Desktop"
    }
}

actual val AppVersion: String by lazy {
    // Priority 1: System property set by jpackage launcher (Windows/Linux/macOS)
    System.getProperty("jpackage.app-version") 
        // Priority 2: Linux AppImage metadata
        ?: System.getenv("APPIMAGE_VERSION")
        // Fallback: Internal build metadata (IDE/Dev)
        ?: BuildConfig.APP_VERSION
}

actual val AppBuildNumber: Int by lazy {
    // If we have a jpackage version, it might be in format 1.0.0+1
    val fullVersion = System.getProperty("jpackage.app-version") ?: ""
    if ("+" in fullVersion) {
        fullVersion.substringAfterLast("+").toIntOrNull() ?: BuildConfig.APP_BUILD_NUMBER
    } else {
        BuildConfig.APP_BUILD_NUMBER
    }
}

@Composable
actual fun LockScreenOrientation(landscape: Boolean) {
    // Desktop manages its own window fullscreen elsewhere
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no native back gesture that requires interception like Android
}

@Composable
actual fun SyncFullscreen(isFullscreen: Boolean) {
    val windowState = LocalWindowState.current
    androidx.compose.runtime.LaunchedEffect(isFullscreen) {
        windowState.placement = if (isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }
}

internal actual fun internalOpenUrl(url: String) {
    val os = System.getProperty("os.name").lowercase()
    try {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        } else if ("linux" in os) {
            Runtime.getRuntime().exec(arrayOf("xdg-open", url))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}




actual fun isFolderWritable(path: String): Boolean {
    if (path.isBlank()) return true
    val file = java.io.File(path)
    return try {
        if (!file.exists()) {
            file.parentFile?.canWrite() ?: true
        } else {
            file.isDirectory && file.canWrite()
        }
    } catch (e: Exception) {
        false
    }
}

actual fun updateDownloadNotification(activeTasksCount: Int, totalProgress: Float, isInitial: Boolean) {
    // Desktop notifications could be added here if needed, but for now no-op
}

actual fun clearDownloadNotification() {
    // No-op for desktop
}

actual fun persistFolderPermission(path: String) {
    // No-op for desktop
}

actual object KmpFileSystem {
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun createDirectories(path: String, mustCreate: Boolean) {
        val f = File(path)
        if (!f.exists()) f.mkdirs()
    }
    actual fun sink(path: String, append: Boolean): Sink = File(path).sink(append)
    actual fun delete(path: String, mustExist: Boolean) {
        File(path).delete()
    }
    actual fun write(path: String, data: ByteArray) {
        File(path).writeBytes(data)
    }
}

actual fun startNotificationListenerService() {
    // No-op on desktop
}

actual fun stopNotificationListenerService() {
    // No-op on desktop
}
