package to.kuudere.anisuge.utils

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
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat

actual fun getDownloadsDirectory(): String {
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Anisug")
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return true

    return ContextCompat.checkSelfPermission(
        androidAppContext,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
actual fun RequestStoragePermission(onResult: (Boolean) -> Unit) {
    if (hasStoragePermission()) {
        onResult(true)
        return
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onResult(isGranted)
    }

    SideEffect {
        launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FAnisug")
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
    try {
        File(outputPath).parentFile?.mkdirs()

        fun urlForHlsExport(original: String): String {
            if (!original.startsWith("http") || inputHeaders.isNullOrEmpty()) return original
            val proxied = StreamProxy.proxyUrl(original, inputHeaders)
            proxiedUrls.add(proxied to original)
            return proxied
        }

        val skipRemoteHlsExport = preferLocalTsRemux ||
            HlsPngTsStrip.isDisguisedTsCdnHost(videoPath) ||
            masterPlaylistUrl?.let { HlsPngTsStrip.isVibeplayerHlsHost(it) } == true

        val masterUrl = masterPlaylistUrl?.takeIf { it.startsWith("http") }
        if (masterUrl != null && !skipRemoteHlsExport) {
            val exported = exportHlsPlaylistToFile(
                context = androidAppContext,
                playlistUrl = urlForHlsExport(masterUrl),
                outputPath = outputPath,
                headers = inputHeaders,
            )
            if (exported) return@withContext true
        }

        if (videoPath.startsWith("http") && !skipRemoteHlsExport) {
            val exportPlaylist = if (videoPath == masterUrl && proxiedUrls.isNotEmpty()) {
                proxiedUrls.last().first
            } else {
                urlForHlsExport(videoPath)
            }
            val exported = exportHlsPlaylistToFile(
                context = androidAppContext,
                playlistUrl = exportPlaylist,
                outputPath = outputPath,
                headers = inputHeaders,
            )
            if (exported) return@withContext true
        }

        if (!videoPath.startsWith("http")) {
            if (remuxToMkvWithRxFfmpeg(videoPath, audioPath, subtitles, outputPath)) {
                return@withContext true
            }
            val exported = exportLocalMediaToFile(
                context = androidAppContext,
                inputPath = videoPath,
                outputPath = outputPath,
            )
            if (exported) return@withContext true
            return@withContext finalizeLocalDownload(videoPath, audioPath, outputPath)
        }

        println("[Download] mux/export failed for remote HLS (host=${masterPlaylistUrl?.substringAfter("://")?.substringBefore('/')})")
        false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        proxiedUrls.forEach { (_, original) -> StreamProxy.release(original) }
    }
}
