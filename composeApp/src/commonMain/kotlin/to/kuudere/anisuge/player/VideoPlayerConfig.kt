package to.kuudere.anisuge.player

import androidx.compose.runtime.Stable

/**
 * Shared state for VideoPlayerSurface.
 * Used on both the watch page (full player) and background contexts (splash/auth).
 */
@Stable
data class VideoPlayerConfig(
    /** URL or local file path to play */
    val url: String,
    /** Loop the file indefinitely (for background use) */
    val loop: Boolean = false,
    /** Mute audio (for background use) */
    val muted: Boolean = false,
    /** Show on-screen controls (pause/seek bar). False for background use. */
    val showControls: Boolean = true,
    /** Enable ASS/SSA subtitle rendering */
    val enableSubs: Boolean = true,
    /** Use embedded fonts from MKV container */
    val embeddedFonts: Boolean = true,
    /** Hardware decoding mode: "auto", "no", "nvdec", "vaapi", etc. */
    val hwdec: String = "auto",
    /** Seek to this position on load (seconds) */
    val startPosition: Double = 0.0,
    /** Directory for custom fonts */
    val fontsDir: String? = null,
    /** Should video start playing automatically */
    val autoPlay: Boolean = true,
    /** Default playback speed */
    val speed: Double = 1.0,
    /** Subtitle scale percentage. 100 keeps mpv defaults. */
    val subtitleSize: Int = 100,
    /** Custom HTTP headers (Referer, User-Agent, etc) */
    val headers: Map<String, String>? = null
)
