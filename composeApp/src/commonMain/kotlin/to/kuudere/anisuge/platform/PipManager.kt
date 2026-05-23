package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable

/**
 * Platform-level Picture-in-Picture (PiP) manager.
 * On Android 8.0+ it provides real PiP support; on Desktop/iOS it returns no-op defaults.
 */
data class PipManager(
    val isAvailable: Boolean,
    val isActive: Boolean,
    val requestPip: (width: Int, height: Int) -> Unit,
)

@Composable
expect fun rememberPipManager(): PipManager
