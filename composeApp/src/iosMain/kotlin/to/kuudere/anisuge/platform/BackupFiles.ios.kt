package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberBackupFileActions(
    onImport: (String) -> Unit,
    onError: (String) -> Unit,
): BackupFileActions = BackupFileActions(
    supported = false,
    export = { _, _ -> onError("Backup files are not supported on iOS yet") },
    import = { onError("Backup files are not supported on iOS yet") },
)
