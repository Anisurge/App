package to.kuudere.anisuge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import to.kuudere.anisuge.player.VideoPlayerConfig
import to.kuudere.anisuge.player.VideoPlayerState
import to.kuudere.anisuge.player.VideoPlayerSurface

@Composable
actual fun ProfileVideoAvatar(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
) {
    val state = androidx.compose.runtime.remember(url) {
        VideoPlayerState(
            VideoPlayerConfig(
                url = url,
                loop = true,
                muted = true,
                showControls = false,
                enableSubs = false,
                embeddedFonts = false,
                autoPlay = true,
                hwdec = "auto",
            ),
        )
    }
    VideoPlayerSurface(
        state = state,
        modifier = modifier,
        onFinished = null,
    )
}
