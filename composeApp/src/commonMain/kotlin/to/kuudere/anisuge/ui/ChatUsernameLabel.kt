package to.kuudere.anisuge.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import to.kuudere.anisuge.data.models.ChatMessage

@Composable
fun ChatUsernameLabel(
    message: ChatMessage,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val style = message.nameStyle
    val gradient = if (
        message.isPremium &&
        style != null &&
        style.gradientStart.isNotBlank() &&
        style.gradientEnd.isNotBlank()
    ) {
        val start = parseChatColorHex(style.gradientStart) ?: chatAccentColor(message.userId, isMine)
        val end = parseChatColorHex(style.gradientEnd) ?: start
        Brush.horizontalGradient(listOf(start, end))
    } else {
        null
    }

    val solid = chatUsernameColor(message.userId, message.userColor, isMine)

    if (gradient != null) {
        Text(
            text = message.username.ifBlank { "User" },
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                brush = gradient,
                fontSize = fontSize,
                fontWeight = fontWeight,
            ),
        )
    } else {
        Text(
            text = message.username.ifBlank { "User" },
            color = solid,
            modifier = modifier,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
