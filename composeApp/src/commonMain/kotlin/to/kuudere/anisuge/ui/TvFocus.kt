package to.kuudere.anisuge.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.kuudere.anisuge.platform.isAndroidTvPlatform

fun Modifier.tvFocusableClick(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    borderWidth: Dp = 2.dp,
    onClick: () -> Unit,
): Modifier = composed {
    if (!isAndroidTvPlatform) {
        clickable(enabled = enabled, onClick = onClick)
    } else {
        var focused by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }

        this
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) borderWidth else 0.dp,
                color = if (focused) Color.White.copy(alpha = 0.9f) else Color.Transparent,
                shape = shape,
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    }
}
