package to.kuudere.anisuge.platform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
actual fun DiscordLoginDialog(
    onDismiss: () -> Unit,
    onToken: (token: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discord Rich Presence") },
        text = { Text("Discord Rich Presence is not available on iOS.") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}
