package to.kuudere.anisuge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberChatTestFrameBytes(): ByteArray? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.assets.open("chat_frame_test.png").use { it.readBytes() }
        }.getOrNull()
    }
}
