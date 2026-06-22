package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable

data class DiscordLoginResult(
    val token: String?,
    val error: String?,
)

@Composable
expect fun DiscordLoginDialog(
    onDismiss: () -> Unit,
    onToken: (token: String) -> Unit,
)
