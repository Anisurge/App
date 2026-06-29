package to.kuudere.anisuge.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Cross-platform iframe/embed video player surface.
 *
 * Loads [embedUrl] in a platform WebView and drives [state] via a JS bridge so that
 * the existing Compose PlayerControls overlay (play/pause, seek bar, speed, etc.) keeps
 * working without modification.
 *
 * Platform notes:
 *  - Android: android.webkit.WebView inside AndroidView; full JS bridge.
 *  - Desktop: stub — shows a "not supported" message until a CEF/JavaFX implementation lands.
 *
 * Features that are **not** available for iframe servers:
 *  - Screenshots (WebView content cannot be captured via PixelCopy).
 *  - Audio track cycling (iframe player owns its own audio pipeline).
 *  - ASS/SSA subtitle rendering (falls back to embed's built-in subtitles).
 *  - Downloads (extraction step needed first).
 */
@Composable
expect fun WebViewVideoPlayerSurface(
    embedUrl: String,
    state: VideoPlayerState,
    modifier: Modifier = Modifier,
    headers: Map<String, String>? = null,
    onFinished: (() -> Unit)? = null,
)
