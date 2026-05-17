package to.kuudere.anisuge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage

/** APNG/GIF frame overlay (inner ring or outer aura) for chat avatars. */
@Composable
fun AnimatedProfileOverlay(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resolved = resolveProfileMediaUrl(url) ?: return
    if (isAnimatedProfileOverlayUrl(resolved)) {
        PlatformAnimatedProfileOverlay(
            url = resolved,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    } else {
        AsyncImage(
            model = resolved,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
expect fun PlatformAnimatedProfileOverlay(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
)
