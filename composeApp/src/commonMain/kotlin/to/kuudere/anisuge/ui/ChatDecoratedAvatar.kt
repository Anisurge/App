package to.kuudere.anisuge.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Chat message/profile avatar — uses [ProfileAvatar] (frames apply app-wide when enabled). */
@Composable
fun ChatDecoratedAvatar(
    avatarUrl: String?,
    frameUrl: String?,
    outerFrameUrl: String?,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 36.dp,
    contentDescription: String? = null,
    /** Used to warm frame cache for this chatter's ring frame. */
    userId: String? = null,
) {
    val uid = userId?.takeIf { it.isNotBlank() }
    ProfileAvatar(
        url = avatarUrl,
        avatarSize = avatarSize,
        frameUrl = frameUrl,
        outerFrameUrl = outerFrameUrl,
        frameCacheKey = uid?.let { "${it}-ring" },
        outerFrameCacheKey = uid?.let { "${it}-outer" },
        modifier = modifier,
        contentDescription = contentDescription,
        showBundledTestFrame = false,
    )
}
