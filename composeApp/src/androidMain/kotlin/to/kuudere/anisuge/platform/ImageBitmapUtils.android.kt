package to.kuudere.anisuge.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bitmap.asImageBitmap()
}

actual fun cropSquareBitmap(
    source: ImageBitmap,
    left: Int,
    top: Int,
    size: Int,
): ImageBitmap {
    val pixels = IntArray(source.width * source.height)
    source.readPixels(pixels)
    val target = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val sx = (left + x).coerceIn(0, source.width - 1)
            val sy = (top + y).coerceIn(0, source.height - 1)
            target[y * size + x] = pixels[sy * source.width + sx]
        }
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bmp.setPixels(target, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}

actual fun encodeJpeg(bitmap: ImageBitmap, quality: Int): ByteArray? {
    val androidBitmap = Bitmap.createBitmap(
        bitmap.width,
        bitmap.height,
        Bitmap.Config.ARGB_8888,
    )
    val buffer = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(buffer)
    androidBitmap.setPixels(buffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return ByteArrayOutputStream().use { stream ->
        if (!androidBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)) {
            return null
        }
        stream.toByteArray()
    }
}
