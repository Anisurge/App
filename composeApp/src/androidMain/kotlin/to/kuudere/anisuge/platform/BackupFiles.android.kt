package to.kuudere.anisuge.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
actual fun rememberBackupFileActions(
    onImport: (String) -> Unit,
    onError: (String) -> Unit,
): BackupFileActions {
    var pendingContent by remember { mutableStateOf<String?>(null) }
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val content = pendingContent
        pendingContent = null
        if (uri != null && content != null) {
            runCatching {
                androidAppContext.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                    it.write(content)
                }
            }.onFailure { onError(it.message ?: "Could not save backup") }
        }
    }
    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                androidAppContext.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }.onSuccess(onImport).onFailure { onError(it.message ?: "Could not read backup") }
        }
    }
    return BackupFileActions(
        supported = !isAndroidTvPlatform,
        export = { name, content ->
            pendingContent = content
            create.launch(name)
        },
        import = { open.launch(arrayOf("application/json", "text/plain")) },
    )
}
