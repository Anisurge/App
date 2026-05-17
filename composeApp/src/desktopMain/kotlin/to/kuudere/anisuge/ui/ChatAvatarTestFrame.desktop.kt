package to.kuudere.anisuge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

private const val RESOURCE_PATH =
    "composeResources/anisurge.composeapp.generated.resources/drawable/chat_frame_test.png"

@Composable
actual fun rememberChatTestFrameBytes(): ByteArray? =
    remember {
        loadChatTestFrameBytes()
    }

private fun loadChatTestFrameBytes(): ByteArray? {
    runCatching {
        Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(RESOURCE_PATH)
            ?.use { return it.readBytes() }
    }
    // Packaged app / dev fallback next to compose resources in build output
    val candidates = listOf(
        File("composeResources/anisurge.composeapp.generated.resources/drawable/chat_frame_test.png"),
        File(System.getProperty("user.dir"), "animated.png"),
    )
    for (file in candidates) {
        if (file.isFile) {
            return runCatching { file.readBytes() }.getOrNull()
        }
    }
    return null
}
