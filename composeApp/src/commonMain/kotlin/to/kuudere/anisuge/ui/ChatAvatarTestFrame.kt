package to.kuudere.anisuge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Temporary: overlay bundled test APNG on every chat avatar. Set false before release. */
const val USE_TEST_CHAT_FRAME = true

@Composable
expect fun rememberChatTestFrameBytes(): ByteArray?

@Composable
fun BundledChatTestFrame(
    modifier: Modifier = Modifier,
) {
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    val cached = rememberChatTestFrameBytes()
    LaunchedEffect(cached) {
        bytes = cached
    }
    val data = bytes ?: return
    ApngBytesOverlay(bytes = data, modifier = modifier)
}

/** Outer frame box — ears/aura extend past the circular pfp (larger avatars get a bit more room). */
fun testChatFrameSize(avatarSize: Dp): Dp =
    when {
        avatarSize >= 80.dp -> avatarSize * 1.38f
        avatarSize >= 44.dp -> avatarSize * 1.32f
        else -> avatarSize * 1.28f
    }
