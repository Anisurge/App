package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import to.kuudere.anisuge.data.models.SessionInfo

data class TvQrPairingEndpoint(
    val host: String,
    val port: Int,
)

expect fun generatePairingNonce(): String

expect class TvQrPairingReceiver() {
    suspend fun start(
        nonce: String,
        expiresAtMillis: Long,
        onClientConnected: () -> Unit,
        onSessionReceived: suspend (SessionInfo) -> Unit,
    ): TvQrPairingEndpoint

    fun stop()
}

@Composable
expect fun TvPairingQrCode(
    payload: String,
    modifier: Modifier = Modifier,
)

@Composable
expect fun TvQrPairingAction(
    modifier: Modifier = Modifier,
)
