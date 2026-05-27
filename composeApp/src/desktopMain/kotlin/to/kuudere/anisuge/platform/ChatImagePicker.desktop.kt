package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberChatImagePicker(
    allowVideo: Boolean,
    onResult: (ChatImagePick?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberFilePickerLauncher(
        type = FileKitType.File(),
    ) { platformFile ->
        if (platformFile == null) {
            onResult(null)
            return@rememberFilePickerLauncher
        }
        scope.launch {
            val pick = withContext(Dispatchers.IO) {
                readChatImageFile(File(platformFile.absolutePath()), allowVideo)
            }
            onResult(pick)
        }
    }
    return { launcher.launch() }
}

private fun readChatImageFile(file: File, allowVideo: Boolean): ChatImagePick? {
    if (!file.exists() || !file.isFile) return null
    val size = file.length()
    val mime = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        "bmp" -> "image/bmp"
        "mp4" -> if (allowVideo) "video/mp4" else return null
        else -> return null
    }
    val maxBytes = if (mime == "video/mp4") PROFILE_VIDEO_MAX_BYTES else CHAT_IMAGE_MAX_BYTES
    if (size <= 0 || size > maxBytes) return null
    if (mime == "video/mp4") {
        val bytes = file.readBytes()
        return ChatImagePick(bytes, mime, file.name, bytes.size.toLong())
    }
    val raw = file.readBytes()
    // Re-encode JPEGs through ImageIO so CMYK / odd ICC profiles never hit Skia's libjpeg on Windows.
    val normalizedJpeg = if (mime == "image/jpeg") {
        decodeImageBitmap(raw)?.let { encodeJpeg(it, quality = 92) }
    } else {
        null
    }
    val bytes = normalizedJpeg ?: raw
    if (bytes.isEmpty() || bytes.size > CHAT_IMAGE_MAX_BYTES) return null
    val outMime = if (normalizedJpeg != null) "image/jpeg" else mime
    val outName = if (normalizedJpeg != null) {
        file.name.substringBeforeLast('.').ifBlank { "image" } + ".jpg"
    } else {
        file.name
    }
    return ChatImagePick(bytes, outMime, outName, bytes.size.toLong())
}
