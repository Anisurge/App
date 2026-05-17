package to.kuudere.anisuge.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.lugf027.apng.Apng
import io.github.lugf027.apng.ApngCancellationBehavior
import io.github.lugf027.apng.ApngCompositionSpec
import io.github.lugf027.apng.animateApngCompositionAsState
import io.github.lugf027.apng.rememberApngComposition
import io.github.lugf027.apng.rememberApngPainter

@Composable
actual fun ApngBytesOverlay(
    bytes: ByteArray,
    modifier: Modifier,
) {
    val spec = remember(bytes) {
        ApngCompositionSpec.Bytes(bytes, "chat_frame_${bytes.hashCode()}")
    }
    val compositionResult = rememberApngComposition(spec)
    if (compositionResult.isFailure) return

    val composition = compositionResult.value ?: return

    val animationState = animateApngCompositionAsState(
        composition = composition,
        isPlaying = true,
        restartOnPlay = true,
        reverseOnRepeat = false,
        clipSpec = null,
        speed = 1f,
        iterations = Apng.IterateForever,
        cancellationBehavior = ApngCancellationBehavior.Immediately,
    )

    val painter = rememberApngPainter(
        composition = composition,
        progress = { animationState.progress },
    )

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
