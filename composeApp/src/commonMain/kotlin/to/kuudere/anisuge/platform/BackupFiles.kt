package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable

data class BackupFileActions(
    val supported: Boolean,
    val export: (fileName: String, content: String) -> Unit,
    val import: () -> Unit,
)

@Composable
expect fun rememberBackupFileActions(
    onImport: (String) -> Unit,
    onError: (String) -> Unit,
): BackupFileActions
