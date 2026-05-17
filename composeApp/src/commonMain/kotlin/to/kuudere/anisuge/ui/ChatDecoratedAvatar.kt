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
) {
    ProfileAvatar(
        url = avatarUrl,
        avatarSize = avatarSize,
        frameUrl = frameUrl,
        outerFrameUrl = outerFrameUrl,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}
