package to.kuudere.anisuge.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Observable playback state exposed to the UI.
 */
@Stable
class VideoPlayerState(config: VideoPlayerConfig) {
    var config by mutableStateOf(config)

    // Read-only playback state (updated by platform player impl)
    var isPlaying   by mutableStateOf(false)
    var isPaused    by mutableStateOf(false)
    var position    by mutableStateOf(0.0)   // seconds
    var duration    by mutableStateOf(0.0)   // seconds
    var isBuffering by mutableStateOf(false)
    var error       by mutableStateOf<String?>(null)

    // Buffered position for progress bar indicator (YouTube-style light gray bar)
    var bufferedPosition by mutableStateOf(0.0) // seconds, how far ahead is buffered

    // Commands from UI → player (platform impl watches these)
    var pauseRequested  by mutableStateOf(false)
    var seekTarget      by mutableStateOf<Double?>(null)
    var subFileUrl      by mutableStateOf<String?>(null)  // single sub change at runtime
    var subFileName     by mutableStateOf<String?>(null)
    var allSubUrls      by mutableStateOf<List<Triple<String, String, Boolean>>?>(null) // (url, lang_name, isDefault)
    var cycleAudio      by mutableStateOf(false)
    var selectedAudioTrack by mutableStateOf<Int?>(null)
    
    var volume          by mutableStateOf(100.0) // 0 to 130
    var brightness      by mutableStateOf(0.0)   // -100 to 100
    var indicatorText   by mutableStateOf<String?>(null)
    
    // New states for the revamped UI
    var isLocked        by mutableStateOf(false)
    var isMuted         by mutableStateOf(false)
    var aspectRatio     by mutableStateOf("Fit") // Fit, Stretch, Zoom, 16:9, 4:3
    
    // Availability for next/prev buttons (controlled by screen logic)
    var hasNextEpisode  by mutableStateOf(false)
    var hasPrevEpisode  by mutableStateOf(false)
    
    // Extracted tracks (populated by player)
    var audioTracks     by mutableStateOf<List<Pair<Int, String>>>(emptyList())
    var subtitleTracks  by mutableStateOf<List<Pair<Int, String>>>(emptyList())
    var selectedSubtitleTrack by mutableStateOf<Int?>(null)
    
    // Signal from AWT canvas → Compose UI
    var canvasClicked   by mutableStateOf(0)
    var canvasPointerMoved by mutableStateOf(0L)
    
    // Commands from UI → player
    var playbackSpeed   by mutableStateOf(config.speed)
    var subtitleSize    by mutableStateOf(config.subtitleSize)
}

@Composable
fun rememberVideoPlayerState(
    url:          String,
    loop:         Boolean = false,
    muted:        Boolean = false,
    showControls: Boolean = true,
    enableSubs:   Boolean = true,
    embeddedFonts:Boolean = true,
    hwdec:        String  = "auto",
    startPosition:Double  = 0.0,
    fontsDir:     String? = null,
    autoPlay:     Boolean = true,
    speed:        Double  = 1.0,
    subtitleSize: Int     = 100,
    headers:      Map<String, String>? = null
): VideoPlayerState = remember(url) {
    VideoPlayerState(
        VideoPlayerConfig(
            url           = url,
            loop          = loop,
            muted         = muted,
            showControls  = showControls,
            enableSubs    = enableSubs,
            embeddedFonts = embeddedFonts,
            hwdec         = hwdec,
            startPosition = startPosition,
            fontsDir      = fontsDir,
            autoPlay      = autoPlay,
            speed         = speed,
            subtitleSize  = subtitleSize,
            headers       = headers
        )
    ).apply {
        pauseRequested = !autoPlay
        seekTarget = null // Reset seek target when creating new state for new video
    }
}

/**
 * Cross-platform video surface.
 * Desktop actual → JNA libmpv via AWT SwingPanel.
 * (Future: Android actual → dev.jdtech.mpv, iOS actual → MPVKit)
 */
@Composable
expect fun VideoPlayerSurface(
    state:    VideoPlayerState,
    modifier: Modifier = Modifier,
    onFinished: (() -> Unit)? = null,
)
