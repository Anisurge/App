package to.kuudere.anisuge.player

import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kotlin wrapper around LibMpv.
 *
 * Lifecycle:
 *   1. MpvPlayer(config) — loads libmpv only (no create/init yet).
 *   2. initAndPlay(wid, url) — creates handle, sets ALL options (incl. wid), initialises, loads file.
 *   3. destroy() — must be called in DisposableEffect.onDispose.
 */
internal class MpvPlayer(
    private val config: VideoPlayerConfig,
    private val state:  VideoPlayerState,
    private val onFinished: (() -> Unit)? = null,
) {
    private val lib: LibMpv? = LibMpv.load()
    private var ctx: Pointer? = null
    private var currentUrl: String? = null
    @Volatile private var isSeeking = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Track the in-flight sub-add job so we can cancel it before starting another.
    // This prevents SIGSEGV when rapid episode changes fire concurrent sub-add commands on a dying handle.
    @Volatile private var subAddJob: kotlinx.coroutines.Job? = null
    // The temp file currently loaded into MPV — delete it only after the next one is ready.
    @Volatile private var activeTempFile: java.io.File? = null

    val isAvailable: Boolean get() = lib != null

    private var renderCtx: com.sun.jna.ptr.PointerByReference? = null

    /**
     * Create the mpv handle, set ALL options BEFORE initialising,
     * then start playback. This is the correct libmpv lifecycle.
     */
    fun initAndPlay(url: String) {
        println("[MpvPlayer] initAndPlay: url=$url")
        val mpv = lib ?: run {
            println("[MpvPlayer] ERROR: lib is null (libmpv not loaded)")
            return
        }
        val handle = mpv.mpv_create() ?: run {
            println("[MpvPlayer] ERROR: mpv_create() returned null")
            return
        }
        ctx = handle
        println("[MpvPlayer] mpv_create() OK")

        // ── Pre-init options ─────────────────────────────────────────────────
        var r: Int
        r = mpv.mpv_set_option_string(handle, "vo", "libmpv")
        println("[MpvPlayer] set vo=libmpv → $r")
        r = mpv.mpv_set_option_string(handle, "hwdec", config.hwdec)
        println("[MpvPlayer] set hwdec=${config.hwdec} → $r")
        r = mpv.mpv_set_option_string(handle, "mute", if (config.muted) "yes" else "no")
        println("[MpvPlayer] set mute → $r")
        r = mpv.mpv_set_option_string(handle, "loop-file", if (config.loop) "inf" else "no")
        println("[MpvPlayer] set loop-file → $r")
        
        // Reduce terminal noise — suppress ffmpeg PPS/SPS spam
        mpv.mpv_set_option_string(handle, "terminal", "yes")
        mpv.mpv_set_option_string(handle, "msg-level", "all=error,ffmpeg=fatal,osc=warn")

        // Subtitle options
        mpv.mpv_set_option_string(handle, "sub-auto", "fuzzy")
        mpv.mpv_set_option_string(handle, "embeddedfonts", if (config.embeddedFonts) "yes" else "no")
        if (config.fontsDir != null) {
            mpv.mpv_set_option_string(handle, "sub-fonts-dir", config.fontsDir)
        }
        // Use auto font provider - Windows uses DirectWrite, Linux uses fontconfig
        mpv.mpv_set_option_string(handle, "sub-font-provider", "auto")
        mpv.mpv_set_option_string(handle, "sub-ass", "yes")
        mpv.mpv_set_option_string(handle, "sub-ass-override", "scale")

        // UI: We disabled mpv's built-in OSC because we now render the 
        // Compose PlayerControls UI overlay directly
        mpv.mpv_set_option_string(handle, "osc", "no")
        mpv.mpv_set_option_string(handle, "osd-level", "0")
        mpv.mpv_set_option_string(handle, "osd-bar", "no")
        mpv.mpv_set_option_string(handle, "input-cursor", "no")
        mpv.mpv_set_option_string(handle, "input-default-bindings", if (config.showControls) "yes" else "no")
        mpv.mpv_set_option_string(handle, "input-vo-keyboard", if (config.showControls) "yes" else "no")

        // Always keep last frame visible to prevent white flash during navigation
        mpv.mpv_set_option_string(handle, "keep-open", "yes")

        // Cache settings for faster streaming (Aggressive but stable)
        mpv.mpv_set_option_string(handle, "cache", "yes")
        mpv.mpv_set_option_string(handle, "cache-secs", "300") // Buffer more once started
        mpv.mpv_set_option_string(handle, "demuxer-readahead-secs", "6") // Reduce initial wait
        mpv.mpv_set_option_string(handle, "demuxer-max-bytes", "150M")
        mpv.mpv_set_option_string(handle, "demuxer-max-back-bytes", "50M")

        // Network optimizations for HTTP/HLS streaming
        mpv.mpv_set_option_string(handle, "network-timeout", "15")
        mpv.mpv_set_option_string(handle, "http-persistent", "yes")
        mpv.mpv_set_option_string(handle, "http-keepalive", "yes")
        mpv.mpv_set_option_string(handle, "hls-bitrate", "max") // Skip quality probing
        mpv.mpv_set_option_string(handle, "stream-buffer-size", "512k") // Smaller segments
        mpv.mpv_set_option_string(handle, "prefetch-playlist", "yes")
        
        // demuxer-lavf-format is intentionally NEVER SET — even for HLS.
        // Setting it globally forces ALL files MPV opens in this session (including .ass subtitle
        // temp files via sub-add) to be demuxed as HLS → avformat_open_input() fails on text files.
        // libavformat auto-detects HLS fine from the .m3u8 URL — no hint needed.
        //
        // demuxer-lavf-o is safe to set globally: for local files (subtitles), probesize and
        // analyzeduration are no-ops on text files, tcp_nodelay and reconnect only apply to HTTP.
        val isRemoteStream = url.startsWith("http://") || url.startsWith("https://")
        if (isRemoteStream) {
            mpv.mpv_set_option_string(handle, "demuxer-lavf-o",
                "probesize=32768,analyzeduration=0,tcp_nodelay=1,reconnect=1")
        }
        mpv.mpv_set_option_string(handle, "cache-pause", "no") // Don't pause on small cache gaps
        mpv.mpv_set_option_string(handle, "vd-lavc-fast", "yes") // Fast decoding bits
        mpv.mpv_set_option_string(handle, "vd-lavc-skipframedrop", "nonref")

        // Disable ytdl probing for raw m3u8 URLs to save a process fork/network call
        mpv.mpv_set_option_string(handle, "ytdl", "no")

        // Fix for Cloudflare/Anti-bot
        val headers = config.headers ?: emptyMap()
        val ua = headers["User-Agent"] ?: headers["user-agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        mpv.mpv_set_option_string(handle, "user-agent", ua)

        val referer = headers["Referer"] ?: headers["referer"]
        if (referer != null) {
            mpv.mpv_set_option_string(handle, "referrer", referer)
        }

        // Apply other headers (like X-Requested-With, etc)
        val headerStrings = headers.filterKeys { k -> !listOf("user-agent", "referer").contains(k.lowercase()) }
            .map { "${it.key}: ${it.value}" }
            .joinToString(",")
        if (headerStrings.isNotEmpty()) {
            mpv.mpv_set_option_string(handle, "http-header-fields", headerStrings)
        }

        mpv.mpv_set_option_string(handle, "ytdl-raw-options", "extractor-args=generic:impersonate")

        // Use safe decoding threads
        mpv.mpv_set_option_string(handle, "vd-lavc-threads", "0")

        val initResult = mpv.mpv_initialize(handle)
        println("[MpvPlayer] mpv_initialize() → $initResult")
        if (initResult != 0) {
            println("[MpvPlayer] ERROR: mpv_initialize() failed")
            mpv.mpv_terminate_destroy(handle)
            ctx = null
            return
        }

        // Setup SW Render Context
        val params = mpv_render_param().toArray(2) as Array<mpv_render_param>
        params[0].type = 1 // MPV_RENDER_PARAM_API_TYPE
        val strMem = com.sun.jna.Memory(3)
        strMem.setString(0, "sw")
        params[0].data = strMem
        params[1].type = 0 // terminate array
        params[1].data = null

        val ctxRef = com.sun.jna.ptr.PointerByReference()
        val rCtx = mpv.mpv_render_context_create(ctxRef, handle, params[0])
        println("[MpvPlayer] render_context_create -> $rCtx")
        renderCtx = ctxRef

        if (config.startPosition > 0.0) {
            mpv.mpv_set_property_string(handle, "start", config.startPosition.toString())
        }

        // Apply auto-play parameter
        mpv.mpv_set_property_string(handle, "pause", if (config.autoPlay) "no" else "yes")

        // Initial speed
        if (config.speed != 1.0) {
            mpv.mpv_set_property_string(handle, "speed", config.speed.toString())
        }

        startEventLoop()

        // Load file
        currentUrl = url
        r = mpv.mpv_command(handle, arrayOf("loadfile", url, null))
        println("[MpvPlayer] loadfile '$url' → $r")
    }

    fun pause()  { ctx?.let { lib?.mpv_set_property_string(it, "pause", "yes") } }
    fun resume() { ctx?.let { lib?.mpv_set_property_string(it, "pause", "no")  } }

    fun seekTo(seconds: Double) {
        val handle = ctx ?: return
        val mpv = lib ?: return
        isSeeking = true
        val safeTarget = seconds.coerceAtLeast(0.1)
        println("[MpvPlayer] seekTo($seconds → $safeTarget) — isSeeking=true, current position=${state.position}")
        // Snap UI to target immediately so slider doesn't jump
        scope.launch(Dispatchers.Main) { state.position = safeTarget }
        val r = mpv.mpv_command(handle, arrayOf("seek", safeTarget.toString(), "absolute", null))
        println("[MpvPlayer] seek command result=$r")
        // Wait for mpv's position to stabilize (two consecutive reads within 1s of each other)
        scope.launch {
            var lastPos = -1.0
            var waited = 0
            while (waited < 3000) {
                kotlinx.coroutines.delay(100)
                waited += 100
                val posPtr = mpv.mpv_get_property_string(handle, "time-pos")
                val pos = posPtr?.let { it.getString(0).toDoubleOrNull() } ?: continue
                mpv.mpv_free(posPtr)
                if (lastPos >= 0 && kotlin.math.abs(pos - lastPos) < 1.0) {
                    // Position stabilized — update UI to where mpv actually landed
                    withContext(Dispatchers.Main) { state.position = pos }
                    break
                }
                lastPos = pos
            }
            isSeeking = false
            println("[MpvPlayer] isSeeking=false after ${waited}ms, position now=${state.position}")
        }
    }
    fun setSubFile(url: String) {
        // Cancel any in-flight sub-add before starting a new one.
        // This prevents concurrent sub-add calls on the same (or a dying) MPV handle.
        subAddJob?.cancel()
        subAddJob = scope.launch(Dispatchers.IO) {
            val handle = ctx ?: return@launch  // handle may have been destroyed by now
            val newFile = try {
                to.kuudere.anisuge.utils.SubtitleUtils.prepareSubtitleFile(url)
            } catch (e: Exception) {
                println("[MpvPlayer] setSubFile: prepareSubtitle failed: ${e.message}")
                return@launch
            } ?: return@launch

            // Double-check handle is still valid before issuing command
            if (ctx == null) {
                newFile.delete()
                return@launch
            }

            println("[MpvPlayer] sub-add -> ${newFile.absolutePath}")
            val r = lib?.mpv_command(handle, arrayOf("sub-add", newFile.absolutePath, "select", null))
            println("[MpvPlayer] sub-add result: $r")

            // Clean up previous temp file now that the new one is loaded
            val old = activeTempFile
            activeTempFile = newFile
            old?.delete()
        }
    }

    // ── Internal event loop ─────────────────────────────────────────────────

    private fun startEventLoop() {
        val handle = ctx ?: return
        val mpv = lib ?: return

        // Dedicated position polling loop since MPV_EVENT_TICK can be unreliable
        scope.launch {
            while (isActive && ctx != null) {
                if (state.isPlaying) {
                    val posPtr = mpv.mpv_get_property_string(handle, "time-pos")
                    if (posPtr != null) {
                        val pos = posPtr.getString(0).toDoubleOrNull() ?: 0.0
                        mpv.mpv_free(posPtr)
                        if (!isSeeking) {
                            val oldPos = state.position
                            if (kotlin.math.abs(pos - oldPos) > 3.0) {
                                println("[MpvPlayer] POLL jump: %.2f → %.2f (delta=%.2f)".format(oldPos, pos, pos - oldPos))
                            }
                            withContext(Dispatchers.Main) { state.position = pos }
                        }
                    }

                    val bufPtr = mpv.mpv_get_property_string(handle, "paused-for-cache")
                    // "seeking" is true when mpv is actively jumping on the timeline
                    val seekPtr = mpv.mpv_get_property_string(handle, "seeking")

                    var isBuf = false
                    if (bufPtr != null) { isBuf = isBuf || bufPtr.getString(0) == "yes"; mpv.mpv_free(bufPtr) }
                    if (seekPtr != null) { isBuf = isBuf || seekPtr.getString(0) == "yes"; mpv.mpv_free(seekPtr) }

                    // Only show buffering if we are NOT intentionally paused, OR if we are seeking
                    // Actually, if we are paused, paused-for-cache might still be true if it was buffering when paused
                    withContext(Dispatchers.Main) { state.isBuffering = isBuf }

                    // Poll buffered position for progress bar (YouTube-style buffer display)
                    val cacheTimePtr = mpv.mpv_get_property_string(handle, "demuxer-cache-time")
                    if (cacheTimePtr != null) {
                        val cacheTime = cacheTimePtr.getString(0).toDoubleOrNull()
                        mpv.mpv_free(cacheTimePtr)
                        val pos = state.position
                        val bufferedPos = if (cacheTime != null && cacheTime > 0) pos + cacheTime else pos
                        withContext(Dispatchers.Main) { state.bufferedPosition = bufferedPos }
                    }

                    val pausePtr = mpv.mpv_get_property_string(handle, "pause")
                    if (pausePtr != null) {
                        val isPaused = pausePtr.getString(0) == "yes"
                        mpv.mpv_free(pausePtr)
                        withContext(Dispatchers.Main) { state.isPaused = isPaused }
                    }
                }
                kotlinx.coroutines.delay(50)
            }
        }

        scope.launch {
            var lastSentPause = false
            var pendingSub: String? = null
            var pendingAllSubs: List<Triple<String, String, Boolean>>? = null
            var lastSentAudioTrack: Int? = null
            var lastSentSubTrack: Int? = null
            var lastSentVolume: Double? = null
            var lastSentBrightness: Double? = null
            var lastSentMute: Boolean? = null
            var lastSentAspectRatio: String? = null
            var lastSentSpeed: Double? = null
            while (isActive && ctx != null) {
                val event = mpv.mpv_wait_event(handle, 0.05)
                if (event != null) {
                    when (event.mpvEventId()) {
                        LibMpv.MPV_EVENT_END_FILE  -> {
                            withContext(Dispatchers.Main) { state.isPlaying = false }
                            if (!config.loop) onFinished?.invoke()
                        }
                        LibMpv.MPV_EVENT_FILE_LOADED -> {
                            isSeeking = false
                            withContext(Dispatchers.Main) {
                                state.isPlaying = true
                                state.isBuffering = false
                                val durPtr = mpv.mpv_get_property_string(handle, "duration")
                                if (durPtr != null) {
                                    state.duration = durPtr.getString(0).toDoubleOrNull() ?: 0.0
                                    mpv.mpv_free(durPtr)
                                }
                            }

                            // Extract Tracks
                            try {
                                val countPtr = mpv.mpv_get_property_string(handle, "track-list/count")
                                if (countPtr != null) {
                                    val count = countPtr.getString(0).toIntOrNull() ?: 0
                                    mpv.mpv_free(countPtr)
                                    val aTracks = mutableListOf<Pair<Int, String>>()
                                    val sTracks = mutableListOf<Pair<Int, String>>()
                                    for (i in 0 until count) {
                                        val typePtr = mpv.mpv_get_property_string(handle, "track-list/$i/type")
                                        if (typePtr != null) {
                                            val type = typePtr.getString(0)
                                            mpv.mpv_free(typePtr)
                                            
                                            val idPtr = mpv.mpv_get_property_string(handle, "track-list/$i/id")
                                            val id = idPtr?.getString(0)?.toIntOrNull() ?: -1
                                            if (idPtr != null) mpv.mpv_free(idPtr)
                                            if (id == -1) continue

                                            val langPtr = mpv.mpv_get_property_string(handle, "track-list/$i/lang")
                                            val lang = langPtr?.getString(0) ?: (if (type == "audio") "Audio $id" else "Subtitle $id")
                                            if (langPtr != null) mpv.mpv_free(langPtr)
                                            
                                            val titlePtr = mpv.mpv_get_property_string(handle, "track-list/$i/title")
                                            val title = titlePtr?.getString(0)
                                            if (titlePtr != null) mpv.mpv_free(titlePtr)
                                            
                                            val label = if (title != null) "$lang - $title" else lang
                                            
                                            if (type == "audio") aTracks.add(id to label)
                                            else if (type == "sub") sTracks.add(id to label)
                                        }
                                    }
                                    withContext(Dispatchers.Main) { 
                                        state.audioTracks = aTracks 
                                        state.subtitleTracks = sTracks
                                    }
                                }
                            } catch (e: Exception) {
                                println("[MpvPlayer] Error extracting tracks: ${e.message}")
                            }

                            // Load all subtitles — default selected, rest available via OSC.
                            // Cancel any previous in-flight sub load before starting fresh.
                            val subsToLoad = pendingAllSubs ?: state.allSubUrls
                            if (!subsToLoad.isNullOrEmpty()) {
                                println("[MpvPlayer] FILE_LOADED: loading ${subsToLoad.size} subtitle(s)")
                                subAddJob?.cancel()
                                activeTempFile?.delete()
                                activeTempFile = null
                                subAddJob = launch(Dispatchers.IO) {
                                    subsToLoad.forEach { (url, name, isDefault) ->
                                        if (ctx == null) return@forEach  // handle destroyed mid-load
                                        val newFile = to.kuudere.anisuge.utils.SubtitleUtils.prepareSubtitleFile(url)
                                        if (newFile != null) {
                                            val flag = if (isDefault) "select" else "auto"
                                            lib?.mpv_command(handle, arrayOf("sub-add", newFile.absolutePath, flag, null))
                                            println("[MpvPlayer] sub-add [$flag] -> ${newFile.absolutePath}")
                                            if (isDefault) {
                                                val old = activeTempFile
                                                activeTempFile = newFile
                                                old?.delete()
                                            }
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) { state.allSubUrls = null }
                                pendingAllSubs = null
                            }
                            
                            val singleSub = pendingSub
                            if (singleSub != null) {
                                if (singleSub == "NONE") {
                                    mpv.mpv_set_option_string(handle, "sid", "no")
                                } else {
                                    launch(Dispatchers.IO) { setSubFile(singleSub) }
                                }
                                pendingSub = null
                            }
                        }
                        LibMpv.MPV_EVENT_TICK -> {
                            if (!isSeeking) {
                                val posPtr = mpv.mpv_get_property_string(handle, "time-pos")
                                if (posPtr != null) {
                                    val pos = posPtr.getString(0).toDoubleOrNull() ?: 0.0
                                    mpv.mpv_free(posPtr)
                                    val oldPos = state.position
                                    if (kotlin.math.abs(pos - oldPos) > 3.0) {
                                        println("[MpvPlayer] TICK jump: %.2f → %.2f (delta=%.2f)".format(oldPos, pos, pos - oldPos))
                                    }
                                    withContext(Dispatchers.Main) { state.position = pos }
                                }
                            }
                        }
                        LibMpv.MPV_EVENT_SHUTDOWN -> break
                    }
                }

                // React to UI-driven pause toggle
                val wantPause = state.pauseRequested
                if (wantPause != lastSentPause) {
                    if (wantPause) pause() else resume()
                    withContext(Dispatchers.Main) { state.isPaused = wantPause } // Instant UI snap
                    lastSentPause = wantPause
                }

                // Handle seek
                state.seekTarget?.let { target ->
                    seekTo(target)
                    withContext(Dispatchers.Main) { state.seekTarget = null }
                }

                // Handle runtime sub switch (single track, e.g. user picks from UI)
                state.subFileUrl?.let { sub ->
                    if (state.isPlaying) {
                        println("[MpvPlayer] Runtime sub change -> $sub")
                        if (sub == "NONE") {
                            subAddJob?.cancel()
                            mpv.mpv_set_option_string(handle, "sid", "no")
                        } else {
                            // setSubFile already cancels the previous job internally
                            setSubFile(sub)
                        }
                        withContext(Dispatchers.Main) { state.subFileUrl = null }
                    } else {
                        pendingSub = sub
                        withContext(Dispatchers.Main) { state.subFileUrl = null }
                    }
                }

                if (state.cycleAudio) {
                    lib?.mpv_command(handle, arrayOf("cycle", "audio", null))
                    withContext(Dispatchers.Main) { state.cycleAudio = false }
                    lastSentAudioTrack = null // reset so next explicit selection forces update
                }

                state.selectedAudioTrack?.let { aid ->
                    if (aid != lastSentAudioTrack) {
                        mpv.mpv_set_option_string(handle, "aid", aid.toString())
                        lastSentAudioTrack = aid
                    }
                }

                state.selectedSubtitleTrack?.let { sid ->
                    if (sid != lastSentSubTrack) {
                        mpv.mpv_set_option_string(handle, "sid", sid.toString())
                        lastSentSubTrack = sid
                    }
                }

                if (state.volume != lastSentVolume) {
                    mpv.mpv_set_property_string(handle, "volume", state.volume.toInt().toString())
                    lastSentVolume = state.volume
                }

                if (state.brightness != lastSentBrightness) {
                    mpv.mpv_set_property_string(handle, "brightness", state.brightness.toInt().toString())
                    lastSentBrightness = state.brightness
                }

                if (state.isMuted != lastSentMute) {
                    mpv.mpv_set_option_string(handle, "mute", if (state.isMuted) "yes" else "no")
                    lastSentMute = state.isMuted
                }

                if (state.aspectRatio != lastSentAspectRatio) {
                    when (state.aspectRatio) {
                        "Fit" -> {
                            mpv.mpv_set_option_string(handle, "video-aspect-override", "-1")
                            mpv.mpv_set_option_string(handle, "panscan", "0")
                        }
                        "Stretch" -> {
                            mpv.mpv_set_option_string(handle, "video-aspect-override", "16:9")
                            mpv.mpv_set_option_string(handle, "panscan", "0")
                        }
                        "Zoom" -> {
                            mpv.mpv_set_option_string(handle, "video-aspect-override", "-1")
                            mpv.mpv_set_option_string(handle, "panscan", "1.0")
                        }
                    }
                    lastSentAspectRatio = state.aspectRatio
                }
                
                if (state.playbackSpeed != lastSentSpeed) {
                    mpv.mpv_set_property_string(handle, "speed", state.playbackSpeed.toString())
                    lastSentSpeed = state.playbackSpeed
                }

                // Handle all-subs load (on new episode/server change before file ready)
                state.allSubUrls?.let { subs ->
                    if (state.isPlaying) {
                        println("[MpvPlayer] Runtime: loading ${subs.size} subtitle(s)")
                        subAddJob?.cancel()
                        activeTempFile?.delete()
                        activeTempFile = null
                        subAddJob = launch(Dispatchers.IO) {
                            subs.forEach { (url, name, isDefault) ->
                                if (ctx == null) return@forEach
                                val newFile = to.kuudere.anisuge.utils.SubtitleUtils.prepareSubtitleFile(url)
                                if (newFile != null) {
                                    val flag = if (isDefault) "select" else "auto"
                                    lib?.mpv_command(handle, arrayOf("sub-add", newFile.absolutePath, flag, null))
                                    if (isDefault) {
                                        val old = activeTempFile
                                        activeTempFile = newFile
                                        old?.delete()
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) { state.allSubUrls = null }
                    } else {
                        pendingAllSubs = subs
                        withContext(Dispatchers.Main) { state.allSubUrls = null }
                    }
                }
            }
        }
    }

    fun destroy() {
        // Cancel in-flight sub-add job BEFORE destroying context.
        // If the job is mid-download it will null-check ctx and abort cleanly.
        subAddJob?.cancel()
        subAddJob = null
        scope.cancel()
        val c = ctx
        val rc = renderCtx
        // Null ctx first so any still-running IO job sees null and stops
        ctx = null
        renderCtx = null
        lib?.let { mpv ->
            if (rc != null) mpv.mpv_render_context_free(rc.value)
            if (c != null) mpv.mpv_terminate_destroy(c)
        }
        // Clean up the last subtitle temp file
        activeTempFile?.delete()
        activeTempFile = null
    }

    fun renderFrame(width: Int, height: Int): ByteArray? {
        val rc = renderCtx?.value ?: return null
        val mpv = lib ?: return null

        val stride = width * 4L  // 4 bytes per pixel for "bgra"
        val buffer = com.sun.jna.Memory(stride * height)

        val sizeMem = com.sun.jna.Memory(8)
        sizeMem.setInt(0, width)
        sizeMem.setInt(4, height)

        val strideMem = com.sun.jna.Memory(8)
        strideMem.setLong(0, stride)

        val fmtMem = com.sun.jna.Memory(5)
        fmtMem.setString(0, "bgra")

        val params = mpv_render_param().toArray(6) as Array<mpv_render_param>
        params[0].type = 17; params[0].data = sizeMem
        params[1].type = 18; params[1].data = fmtMem
        params[2].type = 19; params[2].data = strideMem
        params[3].type = 20; params[3].data = buffer
        params[4].type = 0; params[4].data = null  // terminator

        mpv.mpv_render_context_update(rc)
        val r = mpv.mpv_render_context_render(rc, params[0])
        if (r < 0) {
            println("[MpvPlayer] render failed: $r")
            return null
        }

        return buffer.getByteArray(0, (stride * height).toInt())
    }
}
