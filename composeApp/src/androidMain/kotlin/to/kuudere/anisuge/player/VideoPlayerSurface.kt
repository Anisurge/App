package to.kuudere.anisuge.player

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.util.Log
import java.io.File
import kotlin.coroutines.resume

private const val SUB_TAG = "AnisugeSubs"

private fun ensureAndroidShaderFiles(context: android.content.Context, preset: ShaderPreset): List<String> {
    if (preset.files.isEmpty()) return emptyList()
    val shaderDir = File(context.filesDir, "mpv/Shaders").apply { mkdirs() }
    return preset.files.mapNotNull { name ->
        runCatching {
            val output = File(shaderDir, name)
            if (!output.exists() || output.length() == 0L) {
                context.assets.open("shaders/$name").use { input ->
                    output.outputStream().use(input::copyTo)
                }
            }
            output.absolutePath
        }.onFailure {
            Log.e("AnisugeShaders", "Failed to install $name", it)
        }.getOrNull()
    }
}

private fun applyAndroidEnhancements(context: android.content.Context, settings: PlayerEnhancementSettings) {
    val safe = settings.sanitized()
    safe.mpvProperties().forEach { (property, value) ->
        runCatching { MPVLib.setPropertyString(property, value) }
            .onFailure { Log.w("AnisugeShaders", "mpv rejected $property=$value", it) }
    }
    val preset = ShaderPreset.fromId(safe.shaderPreset)
    val paths = ensureAndroidShaderFiles(context, preset)
    runCatching { MPVLib.setPropertyString("glsl-shaders", paths.joinToString(":")) }
        .onFailure { Log.e("AnisugeShaders", "Failed to apply ${preset.label}", it) }
}

private fun applyAndroidUtilities(settings: PlayerUtilitySettings) {
    settings.sanitized().mpvProperties(defaultBufferMb = 64).forEach { (property, value) ->
        runCatching { MPVLib.setPropertyString(property, value) }
            .onFailure { Log.w("AnisugePlayer", "mpv rejected $property=$value", it) }
    }
}

private suspend fun captureAndroidScreenshot(
    context: android.content.Context,
    surfaceView: SurfaceView,
    fileName: String,
): String {
    val tempFile = File(context.cacheDir, fileName)
    val width = surfaceView.width.takeIf { it > 0 } ?: error("Video surface is not ready")
    val height = surfaceView.height.takeIf { it > 0 } ?: error("Video surface is not ready")
    val bitmap = android.graphics.Bitmap.createBitmap(
        width,
        height,
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        android.view.PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    continuation.resume(Unit)
                } else {
                    bitmap.recycle()
                    continuation.resumeWith(
                        Result.failure(IllegalStateException("PixelCopy failed ($result)")),
                    )
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }
    tempFile.outputStream().use { output ->
        if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
            bitmap.recycle()
            error("PNG encoding failed")
        }
    }
    bitmap.recycle()

    val resolver = context.contentResolver
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(
            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
            "${android.os.Environment.DIRECTORY_DOWNLOADS}/Anisurge/Screenshots",
        )
        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(
        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        values,
    ) ?: error("Could not create screenshot")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            tempFile.inputStream().use { it.copyTo(output) }
        } ?: error("Could not write screenshot")
        values.clear()
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    } finally {
        tempFile.delete()
    }
    return "Saved to Downloads/Anisurge/Screenshots/$fileName"
}

@Composable
@UnstableApi
actual fun VideoPlayerSurface(
    state: VideoPlayerState,
    modifier: Modifier,
    onFinished: (() -> Unit)?
) {
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val coroutineScope = rememberCoroutineScope()

    val resolvedUrl = remember(state.config.url) {
        val url = state.config.url
        when {
            url.startsWith("http://") || url.startsWith("https://") ||
                    url.startsWith("file://") || url.startsWith("content://") ||
                    url.startsWith("/") -> url
            else -> {
                // Copy composeResources to temp file
                try {
                    val ext = url.substringAfterLast('.', "mp4")
                    val tmp = File(context.cacheDir, "cmp_res_${url.hashCode()}.$ext")
                    if (!tmp.exists()) {
                        context.assets.open(url).use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    tmp.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    if (resolvedUrl == null) {
        LaunchedEffect(Unit) { currentOnFinished?.invoke() }
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    val isSeeking = remember { mutableStateOf(false) }
    val surfaceView = remember(resolvedUrl) {
        val view = SurfaceView(context).apply {
            // SurfaceView is more stable than TextureView for hardware-accelerated video
            // specifically avoiding the "destroyed mutex" crashes in libhwui.
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    MPVLib.attachSurface(holder.surface)
                    MPVLib.setOptionString("force-window", "yes")
                    // Restore Video Output immediately — must be synchronous so mpv
                    // has the surface pointer before any internal reconfig fires.
                    // Previously this was inside a Dispatchers.IO coroutine, which
                    // created a race on orientation handoff (the surface was attached
                    // but vo=gpu hadn't been applied yet, causing "Missing surface pointer").
                    MPVLib.setPropertyString("vo", "gpu")

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Check if the file is already loaded to avoid unnecessary reloads
                            val currentPath = try {
                                MPVLib.getPropertyString("path")
                            } catch (e: Exception) {
                                null
                            }

                            val sameLoadedOrOpeningUrl = currentPath == resolvedUrl || lastMpvPlaybackUrl == resolvedUrl

                            if (!sameLoadedOrOpeningUrl) {
                                // New file — only use the API-provided start position.
                                // Never use state.position here: it belongs to the PREVIOUS
                                // file and would seek the new episode to the wrong timestamp.
                                val startPos = state.config.startPosition
                                if (startPos > 0.0) {
                                    MPVLib.setOptionString("start", startPos.toString())
                                } else {
                                    MPVLib.setOptionString("start", "0")
                                }
                                lastMpvPlaybackUrl = resolvedUrl
                                MPVLib.command(arrayOf<String>("loadfile", resolvedUrl))
                            } else {
                                // Already loaded correct file, just force a redraw.
                                // The fullscreen/inline handoff creates a fresh Compose
                                // VideoPlayerState, but mpv does not emit FILE_LOADED again
                                // for an already-loaded URL. Hydrate the new state from mpv
                                // so controls do not sit on the loading spinner forever.
                                hydrateFromLoadedMpv(state, isSeeking.value)
                                if (!state.isPaused) {
                                    MPVLib.setPropertyString("pause", "no")
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
                    // While paused, mpv caches the last rendered frame at the previous surface
                    // dimensions — a bare video-redraw shows it stretched/cropped after a size
                    // change (e.g. fullscreen → inline). Force a fresh render by issuing a
                    // no-op exact seek to the current position.
                    coroutineScope.launch(Dispatchers.IO) {
                        if (state.isPaused) {
                            val pos = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()
                            if (pos != null && pos > 0.0) {
                                MPVLib.command(arrayOf("seek", pos.toString(), "absolute+exact"))
                            }
                        }
                    }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.detachSurface()
                }
            })

            setOnTouchListener { _, event ->
                val x = event.x.toInt().toString()
                val y = event.y.toInt().toString()
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> MPVLib.command(arrayOf("mouse", x, y, "0", "down"))
                    android.view.MotionEvent.ACTION_MOVE -> MPVLib.command(arrayOf("mouse", x, y))
                    android.view.MotionEvent.ACTION_UP -> MPVLib.command(arrayOf("mouse", x, y, "0", "up"))
                }
                true
            }
        }
        view
    }

    LaunchedEffect(state.isPlaying, state.isPaused, state.isBuffering) {
        // Keep the screen awake only while playing or buffering and not paused.
        // This ensures the screen doesn't timeout during watching/loading but allows
        // it to timeout if the user pauses and walks away.
        surfaceView.keepScreenOn = (state.isPlaying || state.isBuffering) && !state.isPaused
    }

    val retriedWithSoftwareDecode = remember(resolvedUrl) { mutableStateOf(false) }

    DisposableEffect(resolvedUrl) {
        cancelPendingMpvStop()

        val configDir = context.filesDir.absolutePath
        val subfontFile = File(configDir, "subfont.ttf")
        if (!subfontFile.exists()) {
            try {
                val systemFont = File("/system/fonts/Roboto-Regular.ttf")
                if (systemFont.exists()) {
                    systemFont.copyTo(subfontFile)
                } else {
                    val fallbackFont = File("/system/fonts/DroidSans.ttf")
                    if (fallbackFont.exists()) fallbackFont.copyTo(subfontFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        synchronized(MPVLibLock) {
            if (!isMPVInitialized) {
                MPVLib.create(context)
                MPVLib.setOptionString("config", "yes")
                MPVLib.setOptionString("config-dir", configDir)
                isMPVInitialized = true
            }
        }

        // mpv core already initialized — vo is managed via setPropertyString in
        // surfaceCreated's onAttach, which ensures the GPU surface is valid.
        // Only set the default vo option before mpv_init (first-time setup).
        if (!isMPVInited) {
            MPVLib.setOptionString("vo", "gpu")
        }

        // Keep Android decoder setup close to Aniyomi/mpv-android defaults.
        // Forcing mediacodec-copy can produce audio-only black video on some
        // MediaTek/Helio devices; mpv's auto path plus a software retry is safer.
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", state.config.hwdec)
        MPVLib.setOptionString("vf", "format=yuv420p")
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

        // Fallback to software if hardware fails (crucial for problematic devices)
        MPVLib.setOptionString("vd-lavc-software-fallback", "yes")

        val showOsc = if (state.config.showControls) "yes" else "no"
        MPVLib.setOptionString("osc", showOsc)
        MPVLib.setOptionString("osd-bar", showOsc)
        MPVLib.setOptionString("osd-level", if (state.config.showControls) "1" else "0")
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("hr-seek", "no")
        MPVLib.setOptionString("input-default-bindings", showOsc)
        MPVLib.setOptionString("input-vo-keyboard", showOsc)

        // Cache: start fast, buffer in background
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", "120")           // 2min buffer is plenty on mobile
        MPVLib.setOptionString("demuxer-readahead-secs", "3") // Start in ~1-2s, pipeline does the rest
        MPVLib.setOptionString("demuxer-max-bytes", "64M")    // Match Aniyomi's mobile cap
        MPVLib.setOptionString("demuxer-max-back-bytes", "64M")

        // Fix video freeze/desync - prevent frame dropping that causes video to fall behind audio
        MPVLib.setOptionString("framedrop", "no")             // Never drop frames
        MPVLib.setOptionString("video-latency-hacks", "no")   // Don't sacrifice sync for latency
        MPVLib.setOptionString("interpolation", "no")         // Disable interpolation that can cause stutter
        MPVLib.setOptionString("video-sync", "audio")         // Sync video to audio (default but explicit)

        // Network optimizations for HTTP/HLS streaming
        MPVLib.setOptionString("network-timeout", "10")       // Fail fast → retry faster
        MPVLib.setOptionString("http-persistent", "yes")
        MPVLib.setOptionString("http-keepalive", "yes")
        MPVLib.setOptionString("hls-bitrate", "max")          // Skip quality probing
        MPVLib.setOptionString("stream-buffer-size", "256k")  // HLS chunks are small
        MPVLib.setOptionString("prefetch-playlist", "yes")
        MPVLib.setOptionString("ytdl", "no")

        // Fast-start: tell FFmpeg to stop over-analyzing the stream
        // Only apply to remote streams — demuxer-lavf-format is intentionally NOT set
        // (it would force HLS mode on subtitle files loaded via sub-add → breaks them)
        val isRemoteStream = resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")
        if (isRemoteStream) {
            MPVLib.setOptionString(
                "demuxer-lavf-o",
                "probesize=1048576,analyzeduration=1000000,tcp_nodelay=1,reconnect=1"
            )
        }
        MPVLib.setOptionString("cache-pause", "no")           // Never stall on micro-gaps
        MPVLib.setOptionString("vd-lavc-fast", "yes")         // Skip unnecessary decode precision


        // Fix for Cloudflare/Anti-bot
        val headers = state.config.headers ?: emptyMap()
        val ua = headers["User-Agent"] ?: headers["user-agent"]
        ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        MPVLib.setOptionString("user-agent", ua)

        val referer = headers["Referer"] ?: headers["referer"]
        if (referer != null) {
            MPVLib.setOptionString("referrer", referer)
        }

        // Apply other headers
        val headerStrings = headers.filterKeys { k -> !listOf("user-agent", "referer").contains(k.lowercase()) }
            .map { "${it.key}: ${it.value}" }
            .joinToString(",")
        if (headerStrings.isNotEmpty()) {
            MPVLib.setOptionString("http-header-fields", headerStrings)
        }

        MPVLib.setOptionString("ytdl-raw-options", "extractor-args=generic:impersonate")

        // Use safe decoding threads (auto usually handles this best with hwdec)
        MPVLib.setOptionString("vd-lavc-threads", "0")

        if (state.config.muted) {
            MPVLib.setOptionString("mute", "yes")
        }
        if (state.config.loop) {
            MPVLib.setOptionString("loop-file", "yes")
        }
        if (state.config.speed != 1.0) {
            MPVLib.setOptionString("speed", state.config.speed.toString())
        }

        // Subtitle options
        // Use "no" for sub-auto when external subs are provided by the API (matching desktop behaviour).
        // "fuzzy" would cause mpv to also pick up embedded container subs which we don't want
        // when we are explicitly loading API-supplied subtitles via sub-add.
        MPVLib.setOptionString("sub-auto", "no")
        MPVLib.setOptionString("embeddedfonts", if (state.config.embeddedFonts) "yes" else "no")
        state.config.fontsDir?.let {
            MPVLib.setOptionString("sub-fonts-dir", it)
        }
        MPVLib.setOptionString("sub-ass", "yes")
        MPVLib.setOptionString("sub-ass-override", "scale")
        MPVLib.setOptionString("sub-scale", (state.config.subtitleSize / 100.0).toString())

        // Only init once
        synchronized(MPVLibLock) {
            if (!isMPVInited) {
                MPVLib.init()
                isMPVInited = true
            }
        }
        applyAndroidEnhancements(context, state.enhancements)
        applyAndroidUtilities(state.utilities)

        var isPausedForCache = false
        val observer = object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {}
            override fun eventProperty(property: String, value: String) {}

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "paused-for-cache" -> {
                        isPausedForCache = value
                        state.isBuffering = isPausedForCache || isSeeking.value
                    }

                    "seeking" -> {
                        isSeeking.value = value
                        state.isBuffering = isPausedForCache || isSeeking.value
                    }

                    "pause" -> {
                        state.isPaused = value
                    }
                }
            }

            override fun eventProperty(property: String, value: Double) {
                if (property == "time-pos") {
                    if (!isSeeking.value) {
                        state.position = value
                    }
                } else if (property == "duration") {
                    state.duration = value
                    if (value > state.peakPlaybackDuration) {
                        state.peakPlaybackDuration = value
                    }
                }
            }

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.MPV_EVENT_FILE_LOADED -> {
                        state.hasLoadedMedia = true
                        state.isPlaying = true
                        state.error = null

                        try {
                            val count = MPVLib.getPropertyInt("track-list/count") ?: 0
                            val aTracks = mutableListOf<Pair<Int, String>>()
                            val sTracks = mutableListOf<Pair<Int, String>>()
                            for (i in 0 until count) {
                                val type = MPVLib.getPropertyString("track-list/$i/type")
                                val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                                val lang = MPVLib.getPropertyString("track-list/$i/lang")
                                    ?: (if (type == "audio") "Audio $id" else "Subtitle $id")
                                val title = MPVLib.getPropertyString("track-list/$i/title")
                                val label = if (title != null) "$lang - $title" else lang

                                if (type == "audio") aTracks.add(id to label)
                                else if (type == "sub") sTracks.add(id to label)
                            }
                            state.audioTracks = aTracks
                            // Decide where to pull subtitle labels from.
                            // For offline files we still use mpv's track list, but
                            // for online streams we want the names provided by the API
                            // (stored earlier in state.allSubUrls) because the container
                            // track names vanish after load.
                            val isOffline = state.config.url.startsWith("file://") ||
                                    state.config.url.startsWith("/")
                            if (isOffline) {
                                state.subtitleTracks = sTracks
                                if (state.selectedSubtitleTrack == null && sTracks.isNotEmpty()) {
                                    state.selectedSubtitleTrack = sTracks.first().first
                                }
                            } else {
                                // External subs (embed captions) are added below; refresh track ids after sub-add.
                                state.subtitleTracks = emptyList()
                                state.selectedSubtitleTrack = null
                            }
                        } catch (e: Exception) {
                            println("[VideoPlayerSurface] Error extracting tracks: ${e.message}")
                        }

                        val pendingSubs = state.allSubUrls
                        if (!pendingSubs.isNullOrEmpty()) {
                            val subHeaders = state.config.headers ?: emptyMap()
                            coroutineScope.launch(Dispatchers.IO) {
                                addExternalSubtitles(state, pendingSubs, subHeaders)
                            }
                        }
                        state.allSubUrls = null

                        if (state.config.hwdec != "no" && !retriedWithSoftwareDecode.value) {
                            coroutineScope.launch(Dispatchers.IO) {
                                delay(3500)
                                if (!isActive || retriedWithSoftwareDecode.value) return@launch

                                val currentPath = runCatching { MPVLib.getPropertyString("path") }.getOrNull()
                                if (currentPath != resolvedUrl) return@launch

                                val before = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull() ?: 0.0
                                delay(1200)
                                val after = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull() ?: before
                                val videoHeight =
                                    runCatching { MPVLib.getPropertyInt("video-params/h") }.getOrNull() ?: 0
                                val videoAspect =
                                    runCatching { MPVLib.getPropertyDouble("video-params/aspect") }.getOrNull() ?: 0.0
                                val voConfigured = runCatching { MPVLib.getPropertyString("vo-configured") }.getOrNull()
                                val playbackAdvanced = after > before + 0.5
                                val noVideoOutput = videoHeight <= 0 && videoAspect <= 0.0 && voConfigured != "yes"

                                if (playbackAdvanced && noVideoOutput) {
                                    retriedWithSoftwareDecode.value = true
                                    val resumeAt = after.coerceAtLeast(state.position).coerceAtLeast(0.0)
                                    println("[VideoPlayerSurface] Video output missing while audio advances; retrying with hwdec=no")
                                    MPVLib.command(arrayOf<String>("stop"))
                                    MPVLib.setOptionString("hwdec", "no")
                                    MPVLib.setOptionString("vf", "format=yuv420p")
                                    MPVLib.setOptionString("start", resumeAt.toString())
                                    MPVLib.command(arrayOf<String>("loadfile", resolvedUrl))
                                }
                            }
                        }
                    }

                    MPVLib.MPV_EVENT_END_FILE -> {
                        state.isPlaying = false
                        val pos = runCatching { MPVLib.getPropertyDouble("time-pos") }
                            .getOrNull()?.coerceAtLeast(0.0) ?: state.position
                        val dur = runCatching { MPVLib.getPropertyDouble("duration") }
                            .getOrNull()?.coerceAtLeast(0.0) ?: state.duration
                        if (!state.hasLoadedMedia) {
                            state.error = "Stream failed to start — trying another server"
                            state.isBuffering = false
                            println("[VideoPlayerSurface] END_FILE before FILE_LOADED (pos=$pos dur=$dur)")
                            return
                        }
                        state.position = pos
                        state.duration = dur
                        val stableDuration = maxOf(dur, state.peakPlaybackDuration)
                        val naturalEnd = stableDuration >= 45.0 && pos >= 20.0 && pos >= stableDuration - 2.5
                        if (naturalEnd) {
                            state.error = null
                            if (!state.config.loop) currentOnFinished?.invoke()
                        } else if (state.isBuffering || isSeeking.value) {
                            // Slow HLS / server switch — mpv often ends the demuxer briefly while buffering.
                            println("[VideoPlayerSurface] END_FILE during buffer/seek ignored (pos=$pos dur=$dur)")
                        } else if (dur <= 0.0 && pos <= 0.5) {
                            state.error = "Stream failed to start — trying another server"
                            println("[VideoPlayerSurface] END_FILE failed start before metadata (pos=$pos dur=$dur)")
                        } else if (dur >= 90.0 && pos < 1.0 && state.peakPlaybackPosition < 1.0) {
                            state.error = "Stream failed to start — try another server in Settings"
                            println("[VideoPlayerSurface] END_FILE failed start (pos=$pos dur=$dur)")
                        } else {
                            state.error = null
                            println("[VideoPlayerSurface] END_FILE transient (pos=$pos dur=$dur)")
                        }
                    }
                }
            }
        }

        MPVLib.addObserver(observer)
        MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("seeking", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)

        onDispose {
            MPVLib.removeObserver(observer)
            try {
                // Compose tears down this SurfaceView during Android fullscreen/orientation
                // handoff, then immediately attaches a new one for the same loaded URL.
                // Stopping mpv here races that handoff and can leave fullscreen stuck on
                // the loading UI. URL/episode/server changes are still handled by the
                // next surfaceCreated() loadfile call when current path differs.
                MPVLib.detachSurface()
                scheduleMpvStopIfSurfaceNotReattached(resolvedUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // We NO LONGER call MPVLib.destroy() here.
            // Destroying the MPVLib singleton while its native threads (e.g. decoder, event loop)
            // are active causes the app to natively crash with SIGABRT (pthread_mutex_lock on destroyed mutex).
        }
    }

    // ── MediaSession for earphone/headphone media button support ──
    val mediaSessionManager = remember(resolvedUrl) { MediaSessionManager(context) }
    DisposableEffect(resolvedUrl) {
        mediaSessionManager.start(
            state = state,
            onPlayPauseToggle = { shouldPause ->
                state.pauseRequested = shouldPause
            }
        )
        onDispose {
            mediaSessionManager.release()
        }
    }

    LaunchedEffect(state.pauseRequested) {
        state.isPaused = state.pauseRequested
        withContext(Dispatchers.IO) {
            MPVLib.setOptionString("pause", if (state.pauseRequested) "yes" else "no")
        }
        // Tell the MediaSession about the new state immediately, so Android knows
        // the player is paused and the earphone "play" button fires instantly next time.
        mediaSessionManager.notifyStateChanged()
    }

    LaunchedEffect(state.isMuted) {
        withContext(Dispatchers.IO) {
            MPVLib.setOptionString("mute", if (state.isMuted) "yes" else "no")
        }
    }

    LaunchedEffect(state.aspectRatio) {
        withContext(Dispatchers.IO) {
            when (state.aspectRatio) {
                "Fit" -> {
                    MPVLib.setOptionString("video-aspect-override", "-1")
                    MPVLib.setOptionString("panscan", "0")
                    MPVLib.setOptionString("keepaspect", "yes")
                }

                "Stretch" -> {
                    MPVLib.setOptionString("video-aspect-override", "-1")
                    MPVLib.setOptionString("panscan", "0")
                    MPVLib.setOptionString("keepaspect", "no")
                }

                "Zoom" -> {
                    MPVLib.setOptionString("video-aspect-override", "-1")
                    MPVLib.setOptionString("panscan", "1.0")
                    MPVLib.setOptionString("keepaspect", "yes")
                }
            }
        }
    }

    LaunchedEffect(state.seekTarget) {
        val target = state.seekTarget ?: return@LaunchedEffect
        state.lastUserSeekAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        state.seekTarget = null
        isSeeking.value = true
        val safeTarget = target.coerceAtLeast(0.1)
        state.position = safeTarget

        withContext(Dispatchers.IO) {
            MPVLib.command(arrayOf("seek", safeTarget.toString(), "absolute"))
        }

        var lastPos = -1.0
        var waited = 0
        while (waited < 3000) {
            delay(100)
            waited += 100
            val pos = withContext(Dispatchers.IO) {
                try {
                    MPVLib.getPropertyDouble("time-pos")
                } catch (_: Exception) {
                    null
                }
            } ?: continue
            if (lastPos >= 0 && kotlin.math.abs(pos - lastPos) < 1.0) {
                state.position = pos
                break
            }
            lastPos = pos
        }
        isSeeking.value = false
    }

    // Poll buffered position for progress bar indicator (YouTube-style buffer display)
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            val cacheTime = withContext(Dispatchers.IO) {
                try {
                    MPVLib.getPropertyDouble("demuxer-cache-time")
                } catch (_: Exception) {
                    null
                }
            }
            // demuxer-cache-time is relative to current position (how much ahead is buffered)
            if (cacheTime != null && cacheTime > 0) {
                state.bufferedPosition = state.position + cacheTime
            } else {
                state.bufferedPosition = state.position
            }
            delay(500)
        }
    }

    // Reset seek target when URL changes to prevent seeking to previous video's position
    LaunchedEffect(state.config.url) {
        state.seekTarget = null
    }

    // Reactively update sub-fonts-dir when the API fonts dir becomes available (may arrive
    // after the player is already initialised since font download happens in the ViewModel).
    LaunchedEffect(state.config.fontsDir) {
        state.config.fontsDir?.let { dir ->
            withContext(Dispatchers.IO) {
                MPVLib.setOptionString("sub-fonts-dir", dir)
                // Also disable embedded fonts now that we have API fonts
                MPVLib.setOptionString("embeddedfonts", "no")
            }
            println("[VideoPlayerSurface] Updated sub-fonts-dir: $dir")
        }
    }

    // Runtime sub change
    LaunchedEffect(state.subFileUrl) {
        state.subFileUrl?.let { sub ->
            if (sub == "NONE") {
                withContext(Dispatchers.IO) {
                    MPVLib.setPropertyInt("sid", 0)
                }
            } else {
                withContext(Dispatchers.IO) {
                    val subHeaders = state.config.headers ?: emptyMap()
                    if (subAddExternal(sub, "select", subHeaders)) {
                        MPVLib.setPropertyString("sub-visibility", "yes")
                        refreshMpvSubtitleTracks(state)
                    }
                }
            }
            state.subFileUrl = null
        }
    }
    // Runtime all-subs load — load API-provided subtitles regardless of isPlaying state.
    // These subs come from the API (not the MKV container), so we must add them explicitly.
    // We no longer guard on isPlaying because the ViewModel may push subs before mpv
    // fires FILE_LOADED (especially on fast connections). Sub-add is safe to call anytime
    // after MPVLib.init().
    LaunchedEffect(state.allSubUrls) {
        state.allSubUrls?.let { subs ->
            val subHeaders = state.config.headers ?: emptyMap()
            withContext(Dispatchers.IO) {
                addExternalSubtitles(state, subs, subHeaders)
            }
            state.allSubUrls = null
        }
    }
    // Cycle audio tracks
    LaunchedEffect(state.cycleAudio) {
        if (state.cycleAudio) {
            withContext(Dispatchers.IO) {
                MPVLib.command(arrayOf<String>("cycle", "audio"))
            }
            state.cycleAudio = false
        }
    }

    LaunchedEffect(state.selectedAudioTrack) {
        state.selectedAudioTrack?.let { aid ->
            withContext(Dispatchers.IO) {
                MPVLib.setPropertyInt("aid", aid)
            }
        }
    }

    LaunchedEffect(state.selectedSubtitleTrack) {
        state.selectedSubtitleTrack?.let { sid ->
            // mpv sid=0 means subtitles off — never apply list index 0 as sid.
            if (sid > 0) {
                withContext(Dispatchers.IO) {
                    MPVLib.setPropertyInt("sid", sid)
                }
            }
        }
    }

    LaunchedEffect(state.playbackSpeed) {
        withContext(Dispatchers.IO) {
            MPVLib.setPropertyDouble("speed", state.playbackSpeed)
        }
    }

    LaunchedEffect(state.subtitleSize) {
        withContext(Dispatchers.IO) {
            MPVLib.setPropertyString("sub-scale", (state.subtitleSize / 100.0).toString())
        }
    }

    LaunchedEffect(state.enhancements) {
        withContext(Dispatchers.IO) {
            applyAndroidEnhancements(context, state.enhancements)
        }
    }

    LaunchedEffect(state.utilities) {
        withContext(Dispatchers.IO) {
            applyAndroidUtilities(state.utilities)
        }
    }

    LaunchedEffect(state.screenshotRequestCount) {
        if (state.screenshotRequestCount <= 0) return@LaunchedEffect
        state.screenshotResult = withContext(Dispatchers.IO) {
            runCatching {
                withContext(Dispatchers.Main) {
                    captureAndroidScreenshot(
                        context = context,
                        surfaceView = surfaceView,
                        fileName = screenshotFileName(
                            animeTitle = state.config.screenshotAnimeTitle,
                            episodeNumber = state.config.screenshotEpisodeNumber,
                            playbackSeconds = state.position,
                        ),
                    )
                }
            }
                .getOrElse { "Screenshot failed: ${it.message ?: "unknown error"}" }
        }
    }

    key(resolvedUrl) {
        AndroidView(
            factory = {
                surfaceView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = modifier.background(Color.Black)
        )
    }
}

// Ensure MPV natively creates only once for app stability
private var isMPVInitialized = false
private var isMPVInited = false
private var lastMpvPlaybackUrl: String? = null
private val mpvStopHandler = Handler(Looper.getMainLooper())
private var pendingMpvStop: Runnable? = null

private fun cancelPendingMpvStop() {
    pendingMpvStop?.let(mpvStopHandler::removeCallbacks)
    pendingMpvStop = null
}

private fun scheduleMpvStopIfSurfaceNotReattached(url: String) {
    cancelPendingMpvStop()
    pendingMpvStop = Runnable {
        pendingMpvStop = null
        try {
            val currentPath = runCatching { MPVLib.getPropertyString("path") }.getOrNull()
            if (currentPath == url) {
                MPVLib.command(arrayOf<String>("stop"))
                MPVLib.setPropertyString("force-window", "no")
                if (lastMpvPlaybackUrl == url) {
                    lastMpvPlaybackUrl = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.also { mpvStopHandler.postDelayed(it, 1500L) }
}

private fun hydrateFromLoadedMpv(state: VideoPlayerState, seeking: Boolean) {
    val pos = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()
    val dur = runCatching { MPVLib.getPropertyDouble("duration") }.getOrNull()
    val paused = runCatching { MPVLib.getPropertyString("pause") }.getOrNull()
        ?.equals("yes", ignoreCase = true) == true
    val pausedForCache = runCatching { MPVLib.getPropertyString("paused-for-cache") }.getOrNull()
        ?.equals("yes", ignoreCase = true) == true

    state.isPlaying = true
    state.isPaused = paused
    state.isBuffering = pausedForCache || seeking
    state.error = null

    if (pos != null && pos >= 0.0 && !seeking) {
        state.position = pos
        if (pos > state.peakPlaybackPosition) {
            state.peakPlaybackPosition = pos
        }
    }
    if (dur != null && dur > 0.0) {
        state.duration = dur
        if (dur > state.peakPlaybackDuration) {
            state.peakPlaybackDuration = dur
        }
    }

    refreshMpvAudioTracks(state)
    refreshMpvSubtitleTracks(state)
}

private fun refreshMpvAudioTracks(state: VideoPlayerState) {
    try {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val aTracks = mutableListOf<Pair<Int, String>>()
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (type != "audio") continue
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: "Audio $id"
            val title = MPVLib.getPropertyString("track-list/$i/title")
            val label = if (title != null) "$lang - $title" else lang
            aTracks.add(id to label)
        }
        state.audioTracks = aTracks
    } catch (e: Exception) {
        println("[VideoPlayerSurface] refreshMpvAudioTracks: ${e.message}")
    }
}

private fun subAddExternal(url: String, flag: String, headers: Map<String, String>): Boolean {
    val preferLocalText = url.contains(".vtt", ignoreCase = true) ||
            url.contains(".srt", ignoreCase = true) ||
            url.contains(".ass", ignoreCase = true)
    if (url.startsWith("http://") || url.startsWith("https://")) {
        if (preferLocalText) {
            val localPath = to.kuudere.anisuge.utils.SubtitleUtils.prepareSubtitle(url, headers)
            if (localPath != null) {
                try {
                    MPVLib.command(arrayOf("sub-add", localPath, flag))
                    Log.i(SUB_TAG, "sub-add cached text [$flag] $localPath")
                    return true
                } catch (e: Exception) {
                    Log.w(SUB_TAG, "cached sub-add failed: ${e.message}")
                }
            }
        }
        return try {
            MPVLib.command(arrayOf("sub-add", url, flag))
            Log.i(SUB_TAG, "sub-add remote [$flag] $url")
            true
        } catch (e: Exception) {
            Log.w(SUB_TAG, "remote sub-add failed, downloading: ${e.message}")
            val localPath = to.kuudere.anisuge.utils.SubtitleUtils.prepareSubtitle(url, headers)
                ?: return false
            MPVLib.command(arrayOf("sub-add", localPath, flag))
            Log.i(SUB_TAG, "sub-add local [$flag] $localPath")
            true
        }
    }
    val localPath = to.kuudere.anisuge.utils.SubtitleUtils.prepareSubtitle(url, headers) ?: return false
    return try {
        MPVLib.command(arrayOf("sub-add", localPath, flag))
        Log.i(SUB_TAG, "sub-add file [$flag] $localPath")
        true
    } catch (e: Exception) {
        Log.e(SUB_TAG, "sub-add file failed: ${e.message}")
        false
    }
}

private fun addExternalSubtitles(
    state: VideoPlayerState,
    subs: List<Triple<String, String, Boolean>>,
    headers: Map<String, String>,
) {
    if (subs.isEmpty()) return
    Log.i(SUB_TAG, "Loading ${subs.size} embed/API subtitle(s)")
    var anyAdded = false
    subs.forEach { (url, label, isDefault) ->
        val flag = if (isDefault) "select" else "auto"
        if (subAddExternal(url, flag, headers)) {
            anyAdded = true
        } else {
            Log.e(SUB_TAG, "failed: $label $url")
        }
    }
    if (anyAdded) {
        try {
            MPVLib.setPropertyString("sub-visibility", "yes")
        } catch (_: Exception) {
        }
    }
    refreshMpvSubtitleTracks(state)
}

private fun refreshMpvSubtitleTracks(state: VideoPlayerState) {
    try {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val sTracks = mutableListOf<Pair<Int, String>>()
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (type != "sub") continue
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
                ?: MPVLib.getPropertyString("track-list/$i/title")
                ?: "Subtitle $id"
            sTracks.add(id to lang)
        }
        state.subtitleTracks = sTracks
        if (sTracks.isNotEmpty()) {
            val currentSid = MPVLib.getPropertyInt("sid") ?: 0
            state.selectedSubtitleTrack = when {
                currentSid > 0 && sTracks.any { it.first == currentSid } -> currentSid
                else -> sTracks.first().first
            }
        }
    } catch (e: Exception) {
        println("[VideoPlayerSurface] refreshMpvSubtitleTracks: ${e.message}")
    }
}

private val MPVLibLock = Any()
