package to.kuudere.anisuge.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Image

@Composable
actual fun ApngBytesOverlay(
    bytes: ByteArray,
    modifier: Modifier,
) {
    val image = remember(bytes) {
        runCatching { Image.makeFromEncoded(bytes) }.getOrNull()
    } ?: return
    val bitmap = remember(image) { image.toComposeImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

private fun org.jetbrains.skia.Image.toComposeImageBitmap() =
    org.jetbrains.skia.Bitmap.makeFromImage(this).asComposeImageBitmap()
