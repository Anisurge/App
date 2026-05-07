package to.kuudere.anisuge.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Desktop actual of [VideoPlayerSurface].
 *
 * Renders video via JNA libmpv (SW renderer) directly into a Compose ImageBitmap.
 * - No X11/AWT window embedding — pure Compose, no layering hacks.
 * - Full Compose overlay support (controls, subtitles UI, etc.).
 * - Reuses Skia Bitmap across frames — no GC pressure.
 * - Render loop does NOT restart on resize; uses refs for dimensions.
 */
@Composable
actual fun VideoPlayerSurface(
    state:      VideoPlayerState,
    modifier:   Modifier,
    onFinished: (() -> Unit)?,
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    var player       by remember { mutableStateOf<MpvPlayer?>(null) }
    var frameBitmap  by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    // Use refs so the render loop reads the latest values without restarting
    val widthRef  = remember { mutableStateOf(1280) }
    val heightRef = remember { mutableStateOf(720)  }

    // ── Player lifecycle ─────────────────────────────────────────────────────
    DisposableEffect(state.config) {
        val p = MpvPlayer(config = state.config, state = state, onFinished = { currentOnFinished?.invoke() })
        player = p
        onDispose { p.destroy() }
    }

    if (player?.isAvailable != true) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    // ── Resolve URL (resource or absolute path) ──────────────────────────────
    val resolvedUrl = remember(state.config.url) {
        val url = state.config.url
        when {
            // HTTP/HTTPS URLs
            url.startsWith("http://") || url.startsWith("https://") -> url
            // Unix absolute paths
            url.startsWith("/") -> url
            // Windows absolute paths (C:\, D:\, etc.)
            url.matches(Regex("^[A-Za-z]:\\\\.*")) -> url
            // Resource file
            else -> {
                val stream = Thread.currentThread().contextClassLoader
                    ?.getResourceAsStream(url)
                if (stream != null) {
                    val ext = url.substringAfterLast('.', "mp4")
                    val tmp = java.io.File.createTempFile("mpv_res_", ".$ext").also { it.deleteOnExit() }
                    tmp.outputStream().use { out -> stream.copyTo(out) }
                    tmp.absolutePath
                } else ""
            }
        }
    }

    // ── Start playback ───────────────────────────────────────────────────────
    LaunchedEffect(resolvedUrl) {
        if (resolvedUrl.isEmpty()) return@LaunchedEffect
        val p = player ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { p.initAndPlay(resolvedUrl) }
    }

    // ── Global Media Keys (Earphones/Headphones) ─────────────────────────────
    val hasNextEpisode by rememberUpdatedState(state.hasNextEpisode)
    val hasPrevEpisode by rememberUpdatedState(state.hasPrevEpisode)
    DisposableEffect(player) {
        if (!state.config.muted) {
            val onPlayPause = { state.pauseRequested = !state.isPaused }
            val onStop = { state.pauseRequested = true }
            val onNext = {
                if (hasNextEpisode) state.mediaNextEpisodeCount++
            }
            val onPrev = {
                if (hasPrevEpisode) state.mediaPrevEpisodeCount++
            }

            to.kuudere.anisuge.platform.GlobalMediaKeys.register(
                onPlayPause = onPlayPause,
                onStop = onStop,
                onNext = onNext,
                onPrevious = onPrev
            )
            to.kuudere.anisuge.platform.MprisManager.register(
                onPlayPause = onPlayPause,
                onStop = onStop,
                onNext = onNext,
                onPrevious = onPrev
            )
        }
        onDispose {
            to.kuudere.anisuge.platform.GlobalMediaKeys.unregister()
            to.kuudere.anisuge.platform.MprisManager.unregister()
        }
    }

    // ── Render loop — only restarts when URL changes ─────────────────────────
    LaunchedEffect(resolvedUrl, player) {
        if (resolvedUrl.isEmpty()) return@LaunchedEffect
        val p = player ?: return@LaunchedEffect

        // Ensure absolute BGRA color type instead of OS-dependent N32 which guesses RGBA on Windows
        
        while (isActive) {
            val w = widthRef.value
            val h = heightRef.value

            if (w > 0 && h > 0) {
                val bytes = withContext(Dispatchers.IO) { p.renderFrame(w, h) }
                if (bytes != null) {
                    val skiaBitmap = org.jetbrains.skia.Bitmap()
                    val skiaImageInfo = org.jetbrains.skia.ImageInfo(
                        w, h,
                        org.jetbrains.skia.ColorType.BGRA_8888,
                        org.jetbrains.skia.ColorAlphaType.PREMUL
                    )
                    skiaBitmap.allocPixels(skiaImageInfo)
                    skiaBitmap.installPixels(bytes)
                    frameBitmap = skiaBitmap.asComposeImageBitmap()
                }
            }

            delay(16) // ~60 fps cap
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    widthRef.value  = size.width
                    heightRef.value = size.height
                }
            }
    ) {
        frameBitmap?.let { bmp ->
            Image(
                bitmap            = bmp,
                contentDescription = "Video Frame",
                modifier          = Modifier.fillMaxSize()
            )
        }
        // ← Drop your Compose controls/overlay here, on top of the video
    }
}