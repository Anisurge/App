package to.kuudere.anisuge.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun VideoPlayerSurface(
    state: VideoPlayerState,
    modifier: Modifier,
    onFinished: (() -> Unit)?,
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("Video playback not yet implemented on iOS", color = Color.White)
    }

    // TODO: Integrate AVPlayer via CMPVideoPlayer or similar
}
