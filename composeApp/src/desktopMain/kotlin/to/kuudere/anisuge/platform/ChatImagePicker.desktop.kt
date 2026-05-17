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
        else -> return null
    }
    return ChatImagePick(file.readBytes(), mime, file.name, size)
}
