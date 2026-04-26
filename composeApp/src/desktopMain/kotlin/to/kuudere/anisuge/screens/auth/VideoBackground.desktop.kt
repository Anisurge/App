package to.kuudere.anisuge.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VideoBackground(
    videoUrl: String,
    modifier: Modifier
) {
    // Desktop uses static image background instead of video
    Box(modifier = modifier)
}
