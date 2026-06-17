package to.kuudere.anisuge.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import io.microshow.rxffmpeg.RxFFmpegInvoke
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.Transformer
import to.kuudere.anisuge.platform.androidAppContext
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@UnstableApi
internal suspend fun exportHlsPlaylistToFile(
    context: Context,
    playlistUrl: String,
    outputPath: String,
    headers: Map<String, String>?,
): Boolean = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine { continuation ->
        val tempFile = File(context.cacheDir, "hls_export_${System.currentTimeMillis()}.mp4")
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        val requestHeaders = (headers ?: emptyMap()).toMutableMap()
        val userAgent = requestHeaders.remove("User-Agent")
            ?: requestHeaders.remove("user-agent")
            ?: "Anisuge/1.0 (Android)"

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
            context,
            DefaultDecoderFactory.Builder(context).build(),
            Clock.DEFAULT,
            mediaSourceFactory,
        )

        val mediaItem = MediaItem.fromUri(playlistUrl)
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoaderFactory)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val ok = tempFile.exists() && tempFile.length() > 0L &&
                    publishTempDownloadOutput(tempFile.absolutePath, outputPath)
                tempFile.delete()
                if (continuation.isActive) continuation.resume(ok)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                println("[Media3] export error: ${exportException.errorCodeName} ${exportException.message}")
                exportException.cause?.let { println("[Media3] export cause: ${it.message}") }
                if (continuation.isActive) continuation.resume(false)
            }
        }

        transformer.addListener(listener)
        continuation.invokeOnCancellation {
            try {
                transformer.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            transformer.start(mediaItem, tempFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            if (continuation.isActive) continuation.resume(false)
        }
    }
}

internal suspend fun exportLocalMediaToFile(
    context: Context,
    inputPath: String,
    outputPath: String,
): Boolean = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine { continuation ->
        val inputFile = File(inputPath)
        if (!inputFile.exists() || inputFile.length() == 0L) {
            if (continuation.isActive) continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val tempFile = File(context.cacheDir, "media_export_${System.currentTimeMillis()}.mp4")
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(inputFile))
            .setMimeType(MimeTypes.VIDEO_MP2T)
            .build()
        val transformer = Transformer.Builder(context).build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val ok = tempFile.exists() && tempFile.length() > 0L &&
                    publishTempDownloadOutput(tempFile.absolutePath, outputPath)
                tempFile.delete()
                if (continuation.isActive) continuation.resume(ok)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                println("[Media3] local export error: ${exportException.message}")
                if (continuation.isActive) continuation.resume(false)
            }
        }

        transformer.addListener(listener)
        continuation.invokeOnCancellation {
            try {
                transformer.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            transformer.start(mediaItem, tempFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            if (continuation.isActive) continuation.resume(false)
        }
    }
}

internal fun remuxToMkvWithRxFfmpeg(
    videoPath: String,
    audioPath: String?,
    subtitles: List<Pair<String, String>>,
    outputPath: String,
): Boolean {
    val context = androidAppContext

    // Resolve input paths (content:// → real fs path for FFmpeg to read)
    val realVideoPath = resolveContentUriToFilePath(videoPath)
    val realAudioPath = audioPath?.let { resolveContentUriToFilePath(it) }
    val realSubs = subtitles.map { (path, label) -> resolveContentUriToFilePath(path) to label }

    val inputFile = File(realVideoPath)
    if (!inputFile.exists() || inputFile.length() == 0L) {
        println("[RxFFmpeg] missing or empty input: $realVideoPath")
        return false
    }

    // ALWAYS mux into app cache dir — native FFmpeg has no scoped storage bypass
    val ext = if (outputPath.endsWith(".mkv", ignoreCase = true)) "mkv" else "mp4"
    val tempFile = File(context.cacheDir, "mux_temp_${System.currentTimeMillis()}.$ext")
    tempFile.parentFile?.mkdirs()
    if (tempFile.exists()) tempFile.delete()

    val cmd = mutableListOf("ffmpeg", "-y", "-i", realVideoPath)
    val audioFile = realAudioPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
    if (audioFile != null) {
        cmd.addAll(listOf("-i", audioFile.absolutePath))
    }

    val validSubs = realSubs.mapNotNull { (path, label) ->
        val ext2 = path.substringAfterLast('.', "").lowercase()
        if (ext2 == "vtt") {
            println("[RxFFmpeg] skipping VTT subtitle (saved alongside MKV): $label")
            return@mapNotNull null
        }
        val f = File(path)
        if (f.exists() && f.length() > 0L) f to label else null
    }
    validSubs.forEach { (file, _) ->
        cmd.addAll(listOf("-i", file.absolutePath))
    }

    cmd.addAll(listOf("-map", "0:v:0"))
    if (audioFile != null) {
        cmd.addAll(listOf("-map", "1:a:0"))
    } else {
        cmd.addAll(listOf("-map", "0:a?"))
    }

    var nextInputIndex = if (audioFile != null) 2 else 1
    validSubs.forEach { _ ->
        cmd.addAll(listOf("-map", "$nextInputIndex:s:0"))
        nextInputIndex++
    }

    cmd.add("-c")
    cmd.add("copy")
    if (audioFile == null) {
        cmd.addAll(listOf("-bsf:a", "aac_adtstoasc"))
    }
    validSubs.forEachIndexed { index, (_, label) ->
        cmd.addAll(listOf("-metadata:s:s:$index", "title=$label"))
    }

    // Output goes to cache, NOT to the final path
    cmd.add(tempFile.absolutePath)

    return try {
        val rc = RxFFmpegInvoke.getInstance().runCommand(cmd.toTypedArray(), null)
        val muxOk = tempFile.exists() && tempFile.length() > 1024L
        if (!muxOk) {
            println("[RxFFmpeg] remux failed rc=$rc inputBytes=${inputFile.length()} subs=${validSubs.size} tempBytes=${tempFile.length()}")
            tempFile.delete()
            return false
        }

        // Now copy from cache to the final destination via publishTempDownloadOutput
        val copyOk = publishTempDownloadOutput(tempFile.absolutePath, outputPath)
        tempFile.delete()

        if (!copyOk) {
            println("[RxFFmpeg] failed to copy temp mux result to final path: $outputPath")
        }
        copyOk
    } catch (e: Exception) {
        println("[RxFFmpeg] remux error: ${e.message}")
        e.printStackTrace()
        tempFile.delete()
        false
    }
}

internal fun remuxRemoteHlsToMkvWithRxFfmpeg(
    playlistUrl: String,
    headers: Map<String, String>?,
    subtitles: List<Pair<String, String>>,
    outputPath: String,
): Boolean {
    val context = androidAppContext
    if (!playlistUrl.startsWith("http", ignoreCase = true)) return false

    val ext = if (outputPath.endsWith(".mkv", ignoreCase = true)) "mkv" else "mp4"
    val tempFile = File(context.cacheDir, "hls_mux_temp_${System.currentTimeMillis()}.$ext")
    tempFile.parentFile?.mkdirs()
    if (tempFile.exists()) tempFile.delete()

    val headerText = headers
        ?.filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
        ?.entries
        ?.joinToString(separator = "\r\n", postfix = "\r\n") { (key, value) -> "$key: $value" }
        .orEmpty()

    val cmd = mutableListOf("ffmpeg", "-y")
    if (headerText.isNotBlank()) {
        cmd.addAll(listOf("-headers", headerText))
    }
    cmd.addAll(
        listOf(
            "-allowed_extensions",
            "ALL",
            "-protocol_whitelist",
            "file,http,https,tcp,tls,crypto",
            "-i",
            playlistUrl,
        )
    )

    val validSubs = subtitles.mapNotNull { (path, label) ->
        val realPath = resolveContentUriToFilePath(path)
        val ext2 = realPath.substringAfterLast('.', "").lowercase()
        if (ext2 == "vtt") {
            println("[RxFFmpeg] skipping VTT subtitle (saved alongside MKV): $label")
            return@mapNotNull null
        }
        val f = File(realPath)
        if (f.exists() && f.length() > 0L) f to label else null
    }
    validSubs.forEach { (file, _) ->
        cmd.addAll(listOf("-i", file.absolutePath))
    }

    cmd.addAll(listOf("-map", "0:v:0", "-map", "0:a?"))
    validSubs.forEachIndexed { index, _ ->
        cmd.addAll(listOf("-map", "${index + 1}:s:0"))
    }
    cmd.addAll(listOf("-c", "copy", "-bsf:a", "aac_adtstoasc"))
    validSubs.forEachIndexed { index, (_, label) ->
        cmd.addAll(listOf("-metadata:s:s:$index", "title=$label"))
    }
    cmd.add(tempFile.absolutePath)

    return try {
        val rc = RxFFmpegInvoke.getInstance().runCommand(cmd.toTypedArray(), null)
        val muxOk = tempFile.exists() && tempFile.length() > 1024L
        if (!muxOk) {
            println("[RxFFmpeg] remote HLS remux failed rc=$rc url=${playlistUrl.substringAfter("://").substringBefore('/')} tempBytes=${tempFile.length()}")
            tempFile.delete()
            return false
        }

        val copyOk = publishTempDownloadOutput(tempFile.absolutePath, outputPath)
        tempFile.delete()
        if (!copyOk) {
            println("[RxFFmpeg] failed to copy remote HLS mux result to final path: $outputPath")
        }
        copyOk
    } catch (e: Exception) {
        println("[RxFFmpeg] remote HLS remux error: ${e.message}")
        e.printStackTrace()
        tempFile.delete()
        false
    }
}

/** Maps a content:// SAF tree URI back to its real filesystem path so native
 *  tools (RxFFmpeg / ffmpeg) can read and write the file directly.
 *  Falls back to the original string for non-content paths or if resolution fails. */
internal fun resolveContentUriToFilePath(path: String): String {
    if (!path.startsWith("content://")) return path
    return try {
        val persistedUri = androidAppContext.contentResolver.persistedUriPermissions
            .filter { it.isWritePermission }
            .map { it.uri.toString() }
            .find { tree -> path.startsWith(tree) }
            ?: return path // can't resolve without the base tree URI

        val treeUri = Uri.parse(persistedUri)
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val colonIdx = treeDocId.indexOf(':')
        if (colonIdx <= 0) return path

        val storageType = treeDocId.substring(0, colonIdx)   // "primary"
        val subRoot = treeDocId.substring(colonIdx + 1)       // "Anisurge"

        val basePath = when (storageType) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            else -> "/storage/$storageType"
        }

        val relative = path.removePrefix(persistedUri).trimStart('/')
        val resolved = if (relative.isEmpty()) "$basePath/$subRoot" else "$basePath/$subRoot/$relative"
        println("[Download] resolved content URI → $resolved")
        resolved
    } catch (e: Exception) {
        println("[Download] failed to resolve content URI: ${e.message}")
        path
    }
}

internal fun finalizeLocalDownload(
    videoPath: String,
    audioPath: String?,
    outputPath: String,
): Boolean {
    val videoFile = File(videoPath)
    if (!videoFile.exists() || videoFile.length() == 0L) return false

    val outFile = File(outputPath)
    outFile.parentFile?.mkdirs()

    return try {
        videoFile.copyTo(outFile, overwrite = true)
        outFile.length() > 0L
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
