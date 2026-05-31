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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }

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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // MANAGE_EXTERNAL_STORAGE is a special permission that requires navigating
        // to app settings, not a runtime permission that can be requested via dialog
        SideEffect {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${androidAppContext.packageName}")
                )
                androidAppContext.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    } else {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            onResult(isGranted)
        }

        SideEffect {
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
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
            // Resolve content:// SAF URIs to real filesystem paths so native tools
            // (RxFFmpeg) and Media3 Transformer can read/write the files directly.
            val realVideo = resolveContentUriToFilePath(videoPath)
            val realOutput = resolveContentUriToFilePath(outputPath)
            val realAudio = audioPath?.let { resolveContentUriToFilePath(it) }
            val realSubs = subtitles.map { (p, l) -> resolveContentUriToFilePath(p) to l }

            // Try Media3 Transformer first (more stable than RxFFmpeg on some devices).
            // RxFFmpeg native code can SIGSEGV on certain phones; Media3 uses the platform's
            // software codecs which are better tested.
            val exported = exportLocalMediaToFile(
                context = androidAppContext,
                inputPath = realVideo,
                outputPath = realOutput,
            )
            if (exported) return@withContext true

            // Media3 failed — skip RxFFmpeg (can SIGSEGV natively) and fall back to
            // a plain file copy. The output will be a raw TS stream with a .mkv extension;
            // most video players detect the container from content, not extension.
            return@withContext finalizeLocalDownload(realVideo, realAudio, realOutput)
        }

        println(
            "[Download] mux/export failed for remote HLS (host=${
                masterPlaylistUrl?.substringAfter("://")?.substringBefore('/')
            })"
        )
        false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        proxiedUrls.forEach { (_, original) -> StreamProxy.release(original) }
    }
}
