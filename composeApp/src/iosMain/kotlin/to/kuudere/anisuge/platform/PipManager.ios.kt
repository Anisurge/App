package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPipManager(): PipManager = remember {
    PipManager(
        isAvailable = false,
        isActive = false,
        requestPip = { _, _ -> },
    )
}
