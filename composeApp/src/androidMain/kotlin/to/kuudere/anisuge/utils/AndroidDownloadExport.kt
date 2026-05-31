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
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

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
            DefaultDecoderFactory(context),
            Clock.DEFAULT,
            mediaSourceFactory,
        )

        val mediaItem = MediaItem.fromUri(playlistUrl)
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoaderFactory)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val ok = outputFile.exists() && outputFile.length() > 0L
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
            transformer.start(mediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
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

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(inputFile))
            .setMimeType(MimeTypes.VIDEO_MP2T)
            .build()
        val transformer = Transformer.Builder(context).build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val ok = outputFile.exists() && outputFile.length() > 0L
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
            transformer.start(mediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
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
    // Resolve content:// SAF URIs to real filesystem paths so native FFmpeg can read/write.
    val realVideoPath = resolveContentUriToFilePath(videoPath)
    val realAudioPath = audioPath?.let { resolveContentUriToFilePath(it) }
    val realOutputPath = resolveContentUriToFilePath(outputPath)
    val realSubs = subtitles.map { (path, label) -> resolveContentUriToFilePath(path) to label }

    val inputFile = File(realVideoPath)
    if (!inputFile.exists() || inputFile.length() == 0L) {
        println("[RxFFmpeg] missing or empty input: $realVideoPath")
        return false
    }

    val outFile = File(realOutputPath)
    outFile.parentFile?.mkdirs()
    if (outFile.exists()) outFile.delete()

    val cmd = mutableListOf("ffmpeg", "-y", "-i", realVideoPath)
    val audioFile = realAudioPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
    if (audioFile != null) {
        cmd.addAll(listOf("-i", audioFile.absolutePath))
    }

    val validSubs = realSubs.mapNotNull { (path, label) ->
        // WebVTT cannot be stream-copied into MKV; keep as separate file alongside the output.
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext == "vtt") {
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
    cmd.add(realOutputPath)

    return try {
        val rc = RxFFmpegInvoke.getInstance().runCommand(cmd.toTypedArray(), null)
        val ok = outFile.exists() && outFile.length() > 1024L
        if (!ok) {
            println("[RxFFmpeg] remux failed rc=$rc inputBytes=${inputFile.length()} subs=${validSubs.size} outputBytes=${outFile.length()}")
        }
        ok
    } catch (e: Exception) {
        println("[RxFFmpeg] remux error: ${e.message}")
        e.printStackTrace()
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
