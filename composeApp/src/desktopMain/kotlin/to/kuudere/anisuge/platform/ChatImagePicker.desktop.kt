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
                readChatImageFile(File(platformFile.absolutePath()))
            }
            onResult(pick)
        }
    }
    return { launcher.launch() }
}

private fun readChatImageFile(file: File): ChatImagePick? {
    if (!file.exists() || !file.isFile) return null
    val size = file.length()
    if (size <= 0 || size > CHAT_IMAGE_MAX_BYTES) return null
    val mime = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        "bmp" -> "image/bmp"
        else -> return null
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
