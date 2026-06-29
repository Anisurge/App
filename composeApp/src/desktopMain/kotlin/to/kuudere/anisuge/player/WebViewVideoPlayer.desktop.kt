package to.kuudere.anisuge.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Desktop stub for iframe/embed player.
 *
 * True WebView support on Compose Desktop requires a CEF or JavaFX WebView integration
 * which is planned for a future release. For now this surface shows an informational
 * message and marks the state as errored so the user can switch to a different server.
 */
@Composable
actual fun WebViewVideoPlayerSurface(
    embedUrl: String,
    state: VideoPlayerState,
    modifier: Modifier,
    headers: Map<String, String>?,
    onFinished: (() -> Unit)?,
) {
    LaunchedEffect(embedUrl) {
        state.isBuffering = false
        state.isPlaying = false
        state.error = "Iframe servers are not yet supported on Desktop. Switch to a different server."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Iframe playback is not yet supported on Desktop.\nPlease select a different server.",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
