package to.kuudere.anisuge.platform

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.kuudere.anisuge.platform.androidAppContext

@Composable
actual fun rememberChatImagePicker(
    onResult: (ChatImagePick?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val pick = withContext(Dispatchers.IO) { readChatImage(uri) }
            onResult(pick)
        }
    }
    return { launcher.launch("image/*") }
}

private fun readChatImage(uri: Uri): ChatImagePick? {
    val resolver = androidAppContext.contentResolver
    val mime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
    if (mime !in setOf("image/jpeg", "image/png", "image/gif", "image/webp")) {
        return null
    }

    var name = "image.jpg"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                name = cursor.getString(idx) ?: name
            }
        }
    }

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.isEmpty() || bytes.size.toLong() > CHAT_IMAGE_MAX_BYTES) {
        return null
    }
    return ChatImagePick(bytes, mime, name, bytes.size.toLong())
}
