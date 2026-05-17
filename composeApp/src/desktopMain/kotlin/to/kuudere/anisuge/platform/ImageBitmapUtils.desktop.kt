package to.kuudere.anisuge.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.jetbrains.skia.Image

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
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
    val buffered = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    buffered.setRGB(0, 0, size, size, target, 0, size)
    return decodeImageBitmap(
        ByteArrayOutputStream().use { stream ->
            ImageIO.write(buffered, "png", stream)
            stream.toByteArray()
        },
    ) ?: ImageBitmap(size, size)
}

actual fun encodeJpeg(bitmap: ImageBitmap, quality: Int): ByteArray? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    val buffered = BufferedImage(
        bitmap.width,
        bitmap.height,
        BufferedImage.TYPE_INT_ARGB,
    )
    buffered.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
    return ByteArrayOutputStream().use { stream ->
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (!writers.hasNext()) return null
        val writer = writers.next()
        val params = writer.defaultWriteParam
        params.compressionMode = ImageWriteParam.MODE_EXPLICIT
        params.compressionQuality = quality.coerceIn(1, 100) / 100f
        ImageIO.createImageOutputStream(stream).use { output ->
            writer.output = output
            writer.write(null, IIOImage(buffered, null, null), params)
        }
        writer.dispose()
        stream.toByteArray()
    }
}
