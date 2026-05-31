package to.kuudere.anisuge.utils

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual fun getDownloadsDirectory(): String {
    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val base = paths.firstOrNull()?.path ?: "./"
    val dir = "$base/Downloads"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

@OptIn(ExperimentalForeignApi::class)
actual fun getCacheDirectory(): String {
    val paths = NSFileManager.defaultManager.URLsForDirectory(
        platform.Foundation.NSCachesDirectory, NSUserDomainMask
    )
    return paths.firstOrNull()?.path ?: "./cache"
}

actual fun hasStoragePermission(): Boolean = true

actual fun isSharedExternalStoragePath(path: String): Boolean = false

actual fun downloadPathRequiresSafPicker(path: String): Boolean = false

actual fun publishTempDownloadOutput(tempPath: String, outputPath: String): Boolean = try {
    NSFileManager.defaultManager.createFileAtPath(
        path = outputPath,
        contents = NSFileManager.defaultManager.contentsAtPath(tempPath),
        attributes = null,
    )
    true
} catch (_: Exception) {
    false
}

@Composable
actual fun RequestStoragePermission(onResult: (Boolean) -> Unit) {
    onResult(true)
}

actual fun hasNotificationPermission(): Boolean = true

@Composable
actual fun RequestNotificationPermission(onResult: (Boolean) -> Unit) {
    onResult(true)
}

actual fun openDirectory(path: String) {
    // iOS: no file manager access to open directories externally
}

actual fun buildDownloadOutputPath(epDir: String, title: String, episodeNumber: Int): String {
    val safeTitle = title.replace("[^A-Za-z0-9 ]".toRegex(), "")
    return "$epDir/${safeTitle}_Ep_$episodeNumber.mkv"
}

actual suspend fun muxToMkv(
    videoPath: String,
    audioPath: String?,
    subtitles: List<Pair<String, String>>,
    fonts: List<String>,
    metadataPath: String?,
    outputPath: String,
    inputHeaders: Map<String, String>?,
    masterPlaylistUrl: String?,
    preferLocalTsRemux: Boolean,
): Boolean {
    // TODO: iOS FFmpeg integration
    return false
}
