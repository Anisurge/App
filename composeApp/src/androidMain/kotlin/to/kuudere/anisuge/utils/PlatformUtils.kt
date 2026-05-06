package to.kuudere.anisuge.utils

import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import to.kuudere.anisuge.platform.androidAppContext
import io.microshow.rxffmpeg.RxFFmpegInvoke
import io.microshow.rxffmpeg.RxFFmpegCommandList
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // Android 11+ doesn't need WRITE_EXTERNAL_STORAGE for app-created files in Downloads
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return true
    
    // Android 10 with legacy storage support also might not need it for its own files
    // but better check if we have it requested in manifest.
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

        // Build the content URI relative to external storage root
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
            // Fallback: open the root Anisug folder
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

private val ffmpegMutex = Mutex()

actual suspend fun muxToMkv(
    videoPath: String,
    audioPath: String?,
    subtitles: List<Pair<String, String>>,
    fonts: List<String>,
    metadataPath: String?,
    outputPath: String,
    inputHeaders: Map<String, String>?
): Boolean = withContext(Dispatchers.IO) {
    val cmd = RxFFmpegCommandList()
    val openFds = mutableListOf<android.os.ParcelFileDescriptor>()
    
    fun getFfPath(path: String, mode: String = "r"): String {
        if (path.startsWith("content://")) {
            return try {
                val uri = Uri.parse(path)
                val pfd = androidAppContext.contentResolver.openFileDescriptor(uri, mode)
                if (pfd != null) {
                    openFds.add(pfd)
                    "/proc/self/fd/${pfd.fd}"
                } else path
            } catch (e: Exception) {
                path
            }
        }
        return path
    }

    var success = false
    try {
        cmd.append("-hide_banner")
        cmd.append("-v"); cmd.append("debug")
        cmd.append("-ignore_unknown")

        // Handle HLS/Stream Headers
        // ALL headers must go into -headers to apply to every sub-request
        // (key fetch, segment fetch, etc.), not just the initial request
        inputHeaders?.let { headers ->
            if (headers.isNotEmpty()) {
                val headerStrings = headers.map { "${it.key}: ${it.value}" }.joinToString("\r\n") + "\r\n"
                cmd.append("-headers"); cmd.append(headerStrings)
            }
        }
        
        cmd.append("-fflags"); cmd.append("+genpts")
        cmd.append("-y")
        
        // Inputs
        cmd.append("-i"); cmd.append(getFfPath(videoPath))
        if (audioPath != null) {
            cmd.append("-i"); cmd.append(getFfPath(audioPath))
        }
        subtitles.forEach { (path, _) ->
            cmd.append("-i"); cmd.append(getFfPath(path))
        }
        
        val metadataIndex = if (metadataPath != null) {
            val index = 1 + (if (audioPath != null) 1 else 0) + subtitles.size
            cmd.append("-i"); cmd.append(getFfPath(metadataPath))
            index
        } else -1

        // Mapping
        cmd.append("-map"); cmd.append("0:v")
        if (audioPath != null) {
            cmd.append("-map"); cmd.append("1:a")
        } else {
            cmd.append("-map"); cmd.append("0:a?")
        }

        subtitles.forEachIndexed { i, _ ->
            val index = if (audioPath != null) i + 2 else i + 1
            cmd.append("-map"); cmd.append("$index:s")
        }

        if (metadataIndex != -1) {
            cmd.append("-map_metadata"); cmd.append("0:g:$metadataIndex")
            cmd.append("-map_chapters"); cmd.append("$metadataIndex")
        }

        if (subtitles.isNotEmpty()) {
            fonts.forEachIndexed { i, fontPath ->
                cmd.append("-attach"); cmd.append(getFfPath(fontPath))
                cmd.append("-metadata:s:t:$i"); cmd.append("mimetype=application/x-truetype-font")
            }
        }

        subtitles.forEachIndexed { i, (_, label) ->
            val safeLabel = label.replace("[^A-Za-z0-9]".toRegex(), "_")
            cmd.append("-metadata:s:s:$i"); cmd.append("title=$safeLabel")
        }

        cmd.append("-c:v"); cmd.append("copy")
        cmd.append("-c:a"); cmd.append("copy")
        
        if (subtitles.isNotEmpty()) {
            cmd.append("-c:s"); cmd.append("copy")
        }
        
        cmd.append("-max_muxing_queue_size"); cmd.append("1024")
        cmd.append(getFfPath(outputPath, "rwt"))

        val cmdArray = cmd.build()
        cmdArray.forEachIndexed { i, arg -> println("FFmpeg Arg[$i]: '$arg'") }
        
        success = ffmpegMutex.withLock {
            RxFFmpegInvoke.getInstance().setDebug(true)
            
            val isRemote = videoPath.startsWith("http://") || videoPath.startsWith("https://")
            if (!isRemote) {
                val videoFile = File(videoPath)
                if (!videoFile.exists() || videoFile.length() == 0L) {
                    println("FFmpeg Error: Video input file is missing or empty: $videoPath")
                    return@withContext false
                }
            }

            val dummyListener = object : io.microshow.rxffmpeg.RxFFmpegInvoke.IFFmpegListener {
                override fun onFinish() {}
                override fun onProgress(progress: Int, progressTime: Long) {}
                override fun onCancel() {}
                override fun onError(message: String?) {}
            }
            
            val response = RxFFmpegInvoke.getInstance().runCommand(cmdArray, dummyListener)
            response == 0
        }
        success
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        openFds.forEach { try { it.close() } catch (ex: Exception) {} }
    }
}
