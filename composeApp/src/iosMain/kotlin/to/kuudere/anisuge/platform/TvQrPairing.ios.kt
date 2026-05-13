package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import to.kuudere.anisuge.data.models.SessionInfo

actual fun generatePairingNonce(): String = "ios-unsupported"

actual class TvQrPairingReceiver actual constructor() {
    actual suspend fun start(
        nonce: String,
        expiresAtMillis: Long,
        onClientConnected: () -> Unit,
        onSessionReceived: suspend (SessionInfo) -> Unit,
    ): TvQrPairingEndpoint {
        throw UnsupportedOperationException("TV QR pairing is not supported on iOS")
    }

    actual fun stop() = Unit
}

@Composable
actual fun TvPairingQrCode(
    payload: String,
    modifier: Modifier,
) = Unit

@Composable
actual fun TvQrPairingAction(
    modifier: Modifier,
    enabled: Boolean,
) = Unit
