package to.kuudere.anisuge.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun DraggableWindowArea(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}

@Composable
actual fun WindowManagementButtons(
    onClose: () -> Unit,
    modifier: Modifier
) {
    // iOS has no window chrome
}
