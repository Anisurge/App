package to.kuudere.anisuge.platform

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

expect fun encodeJpeg(bitmap: ImageBitmap, quality: Int = 88): ByteArray?

expect fun cropSquareBitmap(
    source: ImageBitmap,
    left: Int,
    top: Int,
    size: Int,
): ImageBitmap
