package to.kuudere.anisuge.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.data.models.SURGE_BOT_USER_ID

/** Multi-color shifting gradient for premium/bot usernames. */
private val premiumGradientColors = listOf(
    Color(0xFFFF6B6B), // coral red
    Color(0xFFFFD93D), // gold
    Color(0xFF6BCB77), // green
    Color(0xFF4D96FF), // blue
    Color(0xFFC77DFF), // purple
    Color(0xFFFF6B6B), // wrap back to start for seamless loop
)

/** Special bot gradient — electric purple/cyan vibe. */
private val botGradientColors = listOf(
    Color(0xFFBF80FF), // purple
    Color(0xFF00E5FF), // cyan
    Color(0xFFFF80AB), // pink
    Color(0xFF69F0AE), // mint
    Color(0xFFBF80FF), // wrap back
)

@Composable
fun ChatUsernameLabel(
    message: ChatMessage,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val isBot = message.userId == SURGE_BOT_USER_ID || message.isBot
    val showGradient = isBot || message.effectivePremium

    val gradient = if (showGradient) {
        val infiniteTransition = rememberInfiniteTransition(label = "proGradient")
        val offset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "gradientShift",
        )

        val colors = if (isBot) {
            botGradientColors
        } else {
            premiumGradientColors
        }

        // Shift the gradient continuously for a flowing multi-color effect
        val shiftPx = offset * 600f
        Brush.linearGradient(
            colors = colors,
            start = Offset(x = -shiftPx, y = 0f),
            end = Offset(x = 400f - shiftPx, y = 0f),
        )
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
                fontWeight = fontWeight ?: FontWeight.Bold,
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
