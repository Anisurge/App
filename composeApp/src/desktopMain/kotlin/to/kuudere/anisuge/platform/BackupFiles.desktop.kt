package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberBackupFileActions(
    onImport: (String) -> Unit,
    onError: (String) -> Unit,
): BackupFileActions = remember(onImport, onError) {
    BackupFileActions(
        supported = true,
        export = { fileName, content ->
            runCatching {
                val dialog = FileDialog(null as Frame?, "Export Anisurge backup", FileDialog.SAVE)
                dialog.file = fileName
                dialog.isVisible = true
                val directory = dialog.directory
                val selected = dialog.file
                if (directory != null && selected != null) {
                    File(directory, selected).writeText(content)
                }
            }.onFailure { onError(it.message ?: "Could not save backup") }
        },
        import = {
            runCatching {
                val dialog = FileDialog(null as Frame?, "Import Anisurge backup", FileDialog.LOAD)
                dialog.isVisible = true
                val directory = dialog.directory
                val selected = dialog.file
                if (directory != null && selected != null) {
                    onImport(File(directory, selected).readText())
                }
            }.onFailure { onError(it.message ?: "Could not read backup") }
        },
    )
}
