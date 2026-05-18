package to.kuudere.anisuge.utils

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
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
import kotlinx.coroutines.suspendCancellableCoroutine

@UnstableApi
internal suspend fun exportHlsPlaylistToFile(
    context: Context,
    playlistUrl: String,
    outputPath: String,
    headers: Map<String, String>?,
): Boolean = suspendCancellableCoroutine { continuation ->
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
            println("[Media3] export error: ${exportException.message}")
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

internal suspend fun exportLocalMediaToFile(
    context: Context,
    inputPath: String,
    outputPath: String,
): Boolean = suspendCancellableCoroutine { continuation ->
    val inputFile = File(inputPath)
    if (!inputFile.exists() || inputFile.length() == 0L) {
        if (continuation.isActive) continuation.resume(false)
        return@suspendCancellableCoroutine
    }

    val outputFile = File(outputPath)
    outputFile.parentFile?.mkdirs()

    val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(inputFile))
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
