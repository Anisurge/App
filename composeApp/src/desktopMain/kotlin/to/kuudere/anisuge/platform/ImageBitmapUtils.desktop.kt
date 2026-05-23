package to.kuudere.anisuge.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.jetbrains.skia.Image

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    // ImageIO first — avoids Skia/libjpeg native dialogs on CMYK or odd JPEG color profiles.
    decodeViaImageIo(bytes)?.let { return it }
    return runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

private fun decodeViaImageIo(bytes: ByteArray): ImageBitmap? = runCatching {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    input.use { stream ->
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) return null
        val reader = readers.next()
        try {
            reader.input = stream
            val buffered = reader.read(0, reader.defaultReadParam) ?: return null
            bufferedToComposeImageBitmap(buffered)
        } finally {
            reader.dispose()
        }
    }
}.getOrNull()

private fun bufferedToComposeImageBitmap(source: BufferedImage): ImageBitmap? {
    val rgba = toArgbBufferedImage(source)
    return ByteArrayOutputStream().use { stream ->
        ImageIO.write(rgba, "png", stream)
        Image.makeFromEncoded(stream.toByteArray()).toComposeImageBitmap()
    }
}

private fun toArgbBufferedImage(source: BufferedImage): BufferedImage {
    if (source.type == BufferedImage.TYPE_INT_ARGB) return source
    val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    val g: Graphics2D = out.createGraphics()
    try {
        g.drawImage(source, 0, 0, null)
    } finally {
        g.dispose()
    }
    return out
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
    val rgb = toArgbBufferedImage(buffered)
    return ByteArrayOutputStream().use { stream ->
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (!writers.hasNext()) return null
        val writer = writers.next()
        try {
            val params = writer.defaultWriteParam
            params.compressionMode = ImageWriteParam.MODE_EXPLICIT
            params.compressionQuality = quality.coerceIn(1, 100) / 100f
            ImageIO.createImageOutputStream(stream).use { output ->
                writer.output = output
                writer.write(null, IIOImage(rgb, null, null), params)
            }
            stream.toByteArray()
        } finally {
            writer.dispose()
        }
    }
}
