package to.kuudere.anisuge.utils

import android.content.Context
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
    val inputFile = File(videoPath)
    if (!inputFile.exists() || inputFile.length() == 0L) {
        println("[RxFFmpeg] missing or empty input: $videoPath")
        return false
    }

    val outFile = File(outputPath)
    outFile.parentFile?.mkdirs()
    if (outFile.exists()) outFile.delete()

    val cmd = mutableListOf("ffmpeg", "-y", "-i", videoPath)
    val audioFile = audioPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
    if (audioFile != null) {
        cmd.addAll(listOf("-i", audioFile.absolutePath))
    }

    val validSubs = subtitles.mapNotNull { (path, label) ->
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
    cmd.add(outputPath)

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
