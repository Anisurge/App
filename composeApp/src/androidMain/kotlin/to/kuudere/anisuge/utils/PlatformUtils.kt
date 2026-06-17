package to.kuudere.anisuge.utils

import android.content.ContentValues
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import to.kuudere.anisuge.platform.androidAppContext
import to.kuudere.anisuge.player.StreamProxy
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import okio.buffer
import okio.source
import to.kuudere.anisuge.platform.KmpFileSystem
import android.provider.MediaStore

actual fun getDownloadsDirectory(): String {
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Anisurge")
    if (!dir.exists()) {
        try {
            dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return dir.absolutePath
}

actual fun getCacheDirectory(): String {
    return androidAppContext.filesDir.absolutePath
}

actual fun hasStoragePermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true

    val hasRead = ContextCompat.checkSelfPermission(
        androidAppContext,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    val hasWrite = ContextCompat.checkSelfPermission(
        androidAppContext,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    return hasRead && hasWrite
}

actual fun downloadPathRequiresSafPicker(path: String): Boolean {
    return false
}

actual fun isSharedExternalStoragePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith("content://")) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

    val extRoot = Environment.getExternalStorageDirectory().absolutePath
    val appExternal = androidAppContext.getExternalFilesDir(null)?.absolutePath
    return path.startsWith(extRoot) && (appExternal == null || !path.startsWith(appExternal))
}

actual fun publishTempDownloadOutput(tempPath: String, outputPath: String): Boolean {
    val tempFile = File(tempPath)
    if (!tempFile.exists() || tempFile.length() == 0L) return false

    return try {
        when {
            outputPath.startsWith("content://") -> {
                KmpFileSystem.sink(outputPath).buffer().use { sink ->
                    tempFile.inputStream().source().use { source ->
                        sink.writeAll(source)
                    }
                }
                true
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isSharedExternalStoragePath(outputPath) -> {
                val mimeType = when {
                    outputPath.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
                    outputPath.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                    else -> "application/octet-stream"
                }
                val displayName = outputPath.substringAfterLast('/').substringAfterLast('\\')
                val relativePath = deriveRelativePath(outputPath)
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = androidAppContext.contentResolver.insert(collection, values) ?: return false
                androidAppContext.contentResolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                } ?: return false

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                androidAppContext.contentResolver.update(uri, values, null, null)
                true
            }

            else -> {
                val destFile = File(outputPath)
                destFile.parentFile?.mkdirs()
                tempFile.copyTo(destFile, overwrite = true)
                destFile.exists() && destFile.length() > 0L
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

actual fun getDownloadWorkDirectory(taskId: String): String {
    val safeId = taskId.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
    val dir = File(androidAppContext.filesDir, "download_work/$safeId")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun publishCompletedDownloadFile(
    tempPath: String,
    fileName: String,
    mimeType: String,
    animeId: String,
    episodeNumber: Int,
    downloadRoot: String,
): String? {
    val tempFile = File(tempPath)
    if (!tempFile.exists() || tempFile.length() == 0L) return null
    val safeId = animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
    val relativeDir = "$safeId/ep_$episodeNumber"

    return try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                publishToMediaStoreDownloads(tempFile, fileName, mimeType, safeId, episodeNumber)
            }

            downloadRoot.startsWith("content://") -> {
                null
            }

            downloadRoot.isBlank() || isSharedExternalStoragePath(downloadRoot) -> {
                val destFile = File(getDownloadsDirectory(), "$relativeDir/$fileName")
                destFile.parentFile?.mkdirs()
                tempFile.copyTo(destFile, overwrite = true)
                destFile.takeIf { it.exists() && it.length() > 0L }?.absolutePath
            }

            else -> {
                val root = downloadRoot.ifBlank { getDownloadsDirectory() }
                val destFile = File(root, "$relativeDir/$fileName")
                destFile.parentFile?.mkdirs()
                tempFile.copyTo(destFile, overwrite = true)
                destFile.takeIf { it.exists() && it.length() > 0L }?.absolutePath
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun publishToMediaStoreDownloads(
    tempFile: File,
    fileName: String,
    mimeType: String,
    safeAnimeId: String,
    episodeNumber: Int,
): String? {
    val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Anisurge/$safeAnimeId/ep_$episodeNumber"
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = androidAppContext.contentResolver.insert(collection, values) ?: return null
    return try {
        androidAppContext.contentResolver.openOutputStream(uri)?.use { out ->
            tempFile.inputStream().use { input -> input.copyTo(out) }
        } ?: run {
            androidAppContext.contentResolver.delete(uri, null, null)
            return null
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        androidAppContext.contentResolver.update(uri, values, null, null)
        uri.toString()
    } catch (e: Exception) {
        runCatching { androidAppContext.contentResolver.delete(uri, null, null) }
        e.printStackTrace()
        null
    }
}

actual fun deleteDownloadedFile(path: String): Boolean {
    if (path.isBlank()) return true
    return try {
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            val deleted = if (DocumentsContract.isTreeUri(uri)) {
                KmpFileSystem.delete(path, mustExist = false)
                true
            } else {
                androidAppContext.contentResolver.delete(uri, null, null) >= 0
            }
            deleted
        } else {
            val file = File(path)
            !file.exists() || file.delete()
        }
    } catch (e: Exception) {
        println("[Download] delete failed for $path: ${e.message}")
        false
    }
}

actual fun deleteDownloadWorkDirectory(path: String): Boolean {
    if (path.isBlank() || path.startsWith("content://")) return true
    return try {
        val root = File(path)
        if (!root.exists()) return true
        root.deleteRecursively()
    } catch (e: Exception) {
        println("[Download] work cleanup failed for $path: ${e.message}")
        false
    }
}

actual fun fileSize(path: String): Long {
    if (path.isBlank()) return 0L
    return try {
        if (path.startsWith("content://")) {
            androidAppContext.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use {
                it.statSize.takeIf { size -> size >= 0L } ?: 0L
            } ?: 0L
        } else {
            File(path).length()
        }
    } catch (_: Exception) {
        0L
    }
}

@Composable
actual fun rememberDownloadDirectoryPicker(onPicked: (String?) -> Unit): () -> Unit {
    return remember { { onPicked("") } }
}

@Composable
actual fun RequestStoragePermission(onResult: (Boolean) -> Unit) {
    if (hasStoragePermission()) {
        onResult(true)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onResult(true)
        return
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.entries.all { it.value }
        onResult(allGranted)
    }

    SideEffect {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        )
    }
}

actual fun hasNotificationPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

    return ContextCompat.checkSelfPermission(
        androidAppContext,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
actual fun RequestNotificationPermission(onResult: (Boolean) -> Unit) {
    if (hasNotificationPermission()) {
        onResult(true)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            onResult(isGranted)
        }

        SideEffect {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    } else {
        onResult(true)
    }
}

actual fun openDirectory(path: String) {
    try {
        if (path.startsWith("content://")) {
            val persistedRoot = androidAppContext.contentResolver.persistedUriPermissions
                .map { it.uri.toString() }
                .filter { path.startsWith(it) }
                .maxByOrNull { it.length }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(persistedRoot ?: path)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            androidAppContext.startActivity(intent)
            return
        }

        val file = File(path)
        val dir = if (file.isDirectory) file else file.parentFile ?: file

        val extRoot = Environment.getExternalStorageDirectory().absolutePath
        val relativePath = dir.absolutePath.removePrefix(extRoot).removePrefix("/")
        val encodedPath = relativePath.replace("/", "%2F")
        val documentUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encodedPath")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        androidAppContext.startActivity(intent)
    } catch (e: Exception) {
        try {
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FAnisurge")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            androidAppContext.startActivity(intent)
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

actual fun buildDownloadOutputPath(epDir: String, title: String, episodeNumber: Int): String {
    val safeTitle = title.replace("[^A-Za-z0-9 ]".toRegex(), "")
    return "$epDir/${safeTitle}_Ep_$episodeNumber.mkv"
}

private fun deriveRelativePath(outputPath: String): String {
    val extStorage = Environment.getExternalStorageDirectory().absolutePath
    val relative = outputPath.removePrefix(extStorage).trimStart('/')
    val dir = relative.substringBeforeLast('/', "Download/Anisurge")
    return if (dir.isBlank()) "Download/Anisurge" else dir
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
): Boolean = withContext(Dispatchers.IO) {
    val proxiedUrls = mutableListOf<Pair<String, String>>() // proxied -> original (for release)
    val context = androidAppContext
    try {
        // Use a temp file in cache dir for ALL export operations, then copy to final
        // destination via MediaStore. This avoids native-permission issues with RxFFmpeg
        // and eliminates the need for MANAGE_EXTERNAL_STORAGE on API 30+.
        val tempExt = if (outputPath.endsWith(".mkv", ignoreCase = true)) "mkv" else "mp4"
        val tempFile = File(context.cacheDir, "mux_temp_${System.currentTimeMillis()}.$tempExt")
        tempFile.parentFile?.mkdirs()
        val tempOutputPath = tempFile.absolutePath

        fun urlForHlsExport(original: String): String {
            if (!original.startsWith("http") || inputHeaders.isNullOrEmpty()) return original
            val proxied = StreamProxy.proxyUrl(original, inputHeaders)
            proxiedUrls.add(proxied to original)
            return proxied
        }

        val skipRemoteHlsExport = preferLocalTsRemux ||
                HlsPngTsStrip.isDisguisedTsCdnHost(videoPath) ||
                masterPlaylistUrl?.let { HlsPngTsStrip.isVibeplayerHlsHost(it) } == true

        var exportOk = false
        val masterUrl = masterPlaylistUrl?.takeIf { it.startsWith("http") }
        if (masterUrl != null) {
            exportOk = remuxRemoteHlsToMkvWithRxFfmpeg(
                playlistUrl = masterUrl,
                headers = inputHeaders,
                subtitles = subtitles,
                outputPath = tempOutputPath,
            )
        }

        if (masterUrl != null && !skipRemoteHlsExport) {
            if (!exportOk) {
                exportOk = exportHlsPlaylistToFile(
                    context = context,
                    playlistUrl = urlForHlsExport(masterUrl),
                    outputPath = tempOutputPath,
                    headers = inputHeaders,
                )
            }
        }

        if (!exportOk && videoPath.startsWith("http") && !skipRemoteHlsExport) {
            val exportPlaylist = if (videoPath == masterUrl && proxiedUrls.isNotEmpty()) {
                proxiedUrls.last().first
            } else {
                urlForHlsExport(videoPath)
            }
            exportOk = exportHlsPlaylistToFile(
                context = context,
                playlistUrl = exportPlaylist,
                outputPath = tempOutputPath,
                headers = inputHeaders,
            )
        }

        if (!exportOk && !videoPath.startsWith("http")) {
            val realVideo = resolveContentUriToFilePath(videoPath)
            val realAudio = audioPath?.let { resolveContentUriToFilePath(it) }
            val realSubs = subtitles.map { (p, l) -> resolveContentUriToFilePath(p) to l }

            // Local file: just copy directly. Media3 Transformer re-demuxes via
            // MediaCodec which is very slow for already-downloaded TS segments.
            // The raw TS plays fine in any video player.
            exportOk = finalizeLocalDownload(realVideo, realAudio, tempOutputPath)
        }

        if (!exportOk) {
            println("[Download] mux/export failed for host=${
                masterPlaylistUrl?.substringAfter("://")?.substringBefore('/')
            }")
            tempFile.delete()
            return@withContext false
        }

        val copyOk = publishTempDownloadOutput(tempFile.absolutePath, outputPath)
        tempFile.delete()
        if (!copyOk) {
            println("[Download] failed to copy temp result to final path: $outputPath")
        }
        copyOk
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        proxiedUrls.forEach { (_, original) -> StreamProxy.release(original) }
    }
}
