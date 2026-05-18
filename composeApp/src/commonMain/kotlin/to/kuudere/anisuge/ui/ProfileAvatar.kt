package to.kuudere.anisuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ProfileAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 36.dp,
    frameUrl: String? = null,
    outerFrameUrl: String? = null,
    contentDescription: String? = null,
    placeholderTint: Color = Color.White.copy(alpha = 0.45f),
    backgroundColor: Color = Color(0xFF222222),
    /** When false, skips the bundled test APNG (shop previews & owned frames). */
    showBundledTestFrame: Boolean = true,
) {
    val hasEquippedFrame = !frameUrl.isNullOrBlank() || !outerFrameUrl.isNullOrBlank()
    val showTestFrame = USE_TEST_CHAT_FRAME && showBundledTestFrame && !hasEquippedFrame
    val needsDecor = showTestFrame || hasEquippedFrame

    val layoutSize = when {
        hasEquippedFrame -> avatarSize * 1.55f
        showTestFrame -> testChatFrameSize(avatarSize)
        else -> avatarSize
    }

    Box(
        modifier = modifier.then(
            if (needsDecor) Modifier.size(layoutSize) else Modifier.size(avatarSize),
        ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hasEquippedFrame -> {
                val outerSize = avatarSize * 1.55f
                val ringSize = avatarSize * 1.28f
                if (!outerFrameUrl.isNullOrBlank()) {
                    AnimatedProfileOverlay(
                        url = outerFrameUrl,
                        modifier = Modifier.size(outerSize),
                        contentDescription = null,
                    )
                }
                if (!frameUrl.isNullOrBlank()) {
                    AnimatedProfileOverlay(
                        url = frameUrl,
                        modifier = Modifier.size(ringSize),
                        contentDescription = null,
                    )
                }
                ProfileAvatarContent(
                    url = url,
                    modifier = Modifier.size(avatarSize),
                    contentDescription = contentDescription,
                    placeholderTint = placeholderTint,
                    backgroundColor = backgroundColor,
                )
            }
            showTestFrame -> {
                val frameSize = testChatFrameSize(avatarSize)
                ProfileAvatarContent(
                    url = url,
                    modifier = Modifier.size(avatarSize),
                    contentDescription = contentDescription,
                    placeholderTint = placeholderTint,
                    backgroundColor = backgroundColor,
                )
                BundledChatTestFrame(Modifier.size(frameSize))
            }
            else -> {
                ProfileAvatarContent(
                    url = url,
                    modifier = Modifier.size(avatarSize),
                    contentDescription = contentDescription,
                    placeholderTint = placeholderTint,
                    backgroundColor = backgroundColor,
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatarContent(
    url: String?,
    modifier: Modifier,
    contentDescription: String?,
    placeholderTint: Color,
    backgroundColor: Color,
) {
    val resolved = remember(url) { resolveProfileMediaUrl(url) }
    val shapeModifier = modifier.clip(CircleShape).background(backgroundColor)

    Box(shapeModifier, contentAlignment = Alignment.Center) {
        when {
            resolved == null -> {
                Icon(
                    Icons.Default.Person,
                    contentDescription = contentDescription,
                    tint = placeholderTint,
                    modifier = Modifier.fillMaxSize(0.55f),
                )
            }
            isProfileVideoUrl(resolved) -> {
                ProfileVideoAvatar(
                    url = resolved,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = contentDescription,
                )
            }
            else -> {
                AsyncImage(
                    model = resolved,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Looping muted profile video (mp4/webm). Platform actual uses the video player when available. */
@Composable
expect fun ProfileVideoAvatar(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
)
