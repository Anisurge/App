package to.kuudere.anisuge.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

actual fun getDownloadWorkDirectory(taskId: String): String {
    val safeId = taskId.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
    val dir = "${getCacheDirectory()}/download_work/$safeId"
    NSFileManager.defaultManager.createDirectoryAtPath(
        dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return dir
}

actual fun publishCompletedDownloadFile(
    tempPath: String,
    fileName: String,
    mimeType: String,
    animeId: String,
    episodeNumber: Int,
    downloadRoot: String,
): String? {
    val safeId = animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
    val root = downloadRoot.ifBlank { getDownloadsDirectory() }
    val dir = "$root/$safeId/ep_$episodeNumber"
    val dest = "$dir/$fileName"
    return try {
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (publishTempDownloadOutput(tempPath, dest)) dest else null
    } catch (_: Exception) {
        null
    }
}

actual fun deleteDownloadedFile(path: String): Boolean = try {
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    true
} catch (_: Exception) {
    false
}

actual fun deleteDownloadWorkDirectory(path: String): Boolean = deleteDownloadedFile(path)

actual fun fileSize(path: String): Long {
    return try {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        (attrs?.get(platform.Foundation.NSFileSize) as? Number)?.toLong() ?: 0L
    } catch (_: Exception) {
        0L
    }
}

@Composable
actual fun rememberDownloadDirectoryPicker(onPicked: (String?) -> Unit): () -> Unit {
    return remember { { onPicked(null) } }
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
    remoteHlsDurationSeconds: Double?,
    onRemoteHlsProgress: ((progressTimeMs: Long, durationSeconds: Double?) -> Unit)?,
): Boolean {
    // TODO: iOS FFmpeg integration
    return false
}
