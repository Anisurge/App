package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import to.kuudere.anisuge.data.models.SessionInfo

actual fun generatePairingNonce(): String = "desktop-unsupported"

actual class TvQrPairingReceiver actual constructor() {
    actual suspend fun start(
        nonce: String,
        expiresAtMillis: Long,
        onClientConnected: () -> Unit,
        onSessionReceived: suspend (SessionInfo) -> Unit,
    ): TvQrPairingEndpoint {
        throw UnsupportedOperationException("TV QR pairing is Android-only")
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
) = Unit
