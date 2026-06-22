package to.kuudere.anisuge.platform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun DiscordLoginDialog(
    onDismiss: () -> Unit,
    onToken: (token: String) -> Unit,
) {
    var tokenInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Connect with Discord")
        },
        text = {
            Column {
                Text(
                    text = "Discord Rich Presence works automatically when the Discord desktop app is running on your computer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "If you need to use a token manually, paste it below:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    placeholder = { Text("Paste Discord token here...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (tokenInput.isNotBlank()) {
                        onToken(tokenInput.trim())
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(if (tokenInput.isNotBlank()) "Connect" else "Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
