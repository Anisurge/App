package to.kuudere.anisuge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

fun Modifier.animateItemEntrance(
    index: Int,
    staggerMs: Long = 60L,
    maxDelayMs: Long = 480L,
    durationMs: Int = 500,
    scaleBegin: Float = 0.88f,
): Modifier = composed {
    val animatable = remember { Animatable(0f) }
    val delayMs = (index * staggerMs).coerceAtMost(maxDelayMs)

    LaunchedEffect(Unit) {
        delay(delayMs)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = EaseOutBack
            )
        )
    }

    graphicsLayer {
        alpha = animatable.value
        val scale = scaleBegin + (1f - scaleBegin) * animatable.value
        scaleX = scale
        scaleY = scale
        translationY = (1f - animatable.value) * 12f
    }
}
