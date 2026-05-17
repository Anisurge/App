package to.kuudere.anisuge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Renders animated PNG bytes (APNG) as an overlay — used for bundled chat frame tests. */
@Composable
expect fun ApngBytesOverlay(
    bytes: ByteArray,
    modifier: Modifier,
)
