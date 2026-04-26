package to.kuudere.anisuge.platform

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.CaptureActivity
import com.google.zxing.integration.android.IntentIntegrator
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.EnumMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.SessionInfo
import to.kuudere.anisuge.data.models.TvPairingRequest
import to.kuudere.anisuge.data.models.TvPairingResponse

private val pairingJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

actual fun generatePairingNonce(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    val random = SecureRandom()
    return buildString(32) {
        repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}

actual class TvQrPairingReceiver actual constructor() {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    @Volatile private var completed = false

    actual suspend fun start(
        nonce: String,
        expiresAtMillis: Long,
        onSessionReceived: suspend (SessionInfo) -> Unit,
    ): TvQrPairingEndpoint = withContext(Dispatchers.IO) {
        stop()
        completed = false
        val host = findLocalIpv4() ?: throw IllegalStateException("No Wi-Fi/LAN address found")
        val socket = ServerSocket(0)
        serverSocket = socket
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = receiverScope

        receiverScope.launch {
            while (isActive && !completed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                launch {
                    handleClient(client, nonce, expiresAtMillis, onSessionReceived)
                }
            }
        }

        TvQrPairingEndpoint(host = host, port = socket.localPort)
    }

    actual fun stop() {
        completed = true
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    private suspend fun handleClient(
        socket: Socket,
        nonce: String,
        expiresAtMillis: Long,
        onSessionReceived: suspend (SessionInfo) -> Unit,
    ) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val key = line.substringBefore(":", "").trim().lowercase()
                val value = line.substringAfter(":", "").trim()
                if (key.isNotBlank()) headers[key] = value
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = CharArray(contentLength).also { reader.read(it) }.concatToString()

            val response = when {
                completed -> TvPairingResponse(false, "QR already used")
                System.currentTimeMillis() > expiresAtMillis -> TvPairingResponse(false, "QR expired")
                !requestLine.startsWith("POST /pair ") -> TvPairingResponse(false, "Invalid pairing request")
                else -> runCatching {
                    val request = pairingJson.decodeFromString<TvPairingRequest>(body)
                    if (request.nonce != nonce) {
                        TvPairingResponse(false, "Invalid QR nonce")
                    } else {
                        onSessionReceived(request.session)
                        completed = true
                        TvPairingResponse(true, "TV paired")
                    }
                }.getOrElse {
                    TvPairingResponse(false, it.message ?: "Malformed pairing payload")
                }
            }

            val responseBody = pairingJson.encodeToString(response)
            val status = if (response.success) "200 OK" else "400 Bad Request"
            val bytes = responseBody.encodeToByteArray()
            client.getOutputStream().write(
                "HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".encodeToByteArray()
            )
            client.getOutputStream().write(bytes)
            client.getOutputStream().flush()

            if (response.success) stop()
        }
    }
}

@Composable
actual fun TvPairingQrCode(
    payload: String,
    modifier: Modifier,
) {
    val bitmap = remember(payload) { createQrBitmap(payload, 720) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "TV login QR code",
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
actual fun TvQrPairingAction(
    modifier: Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val scan = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val contents = scan?.contents
        if (result.resultCode != Activity.RESULT_OK || contents.isNullOrBlank()) {
            message = "No QR scanned"
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            message = pairWithTv(contents)
        }
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Button(
            onClick = {
                val activity = context.findActivity()
                if (activity == null) {
                    message = "Scanner unavailable"
                } else {
                    val intent = IntentIntegrator(activity)
                        .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                        .setPrompt("Scan Anisurge TV QR")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .setCaptureActivity(CaptureActivity::class.java)
                        .createScanIntent()
                    launcher.launch(intent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Text("Scan TV QR", fontWeight = FontWeight.SemiBold)
        }
        message?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun createQrBitmap(payload: String, size: Int): Bitmap {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.MARGIN, 1)
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
    }
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

private suspend fun pairWithTv(rawQr: String): String = withContext(Dispatchers.IO) {
    val uri = runCatching { Uri.parse(rawQr) }.getOrNull()
        ?: return@withContext "Invalid QR"
    if (uri.scheme != "anisurge" || uri.host != "tv-login") {
        return@withContext "This is not an Anisurge TV QR"
    }

    val host = uri.getQueryParameter("host").orEmpty()
    val port = uri.getQueryParameter("port")?.toIntOrNull()
    val nonce = uri.getQueryParameter("nonce").orEmpty()
    if (host.isBlank() || port == null || nonce.isBlank()) {
        return@withContext "Invalid TV QR"
    }

    val session = AppComponent.sessionStore.get()
        ?: return@withContext "Log in on this phone first"
    val requestBody = pairingJson.encodeToString(TvPairingRequest(nonce, session)).encodeToByteArray()

    runCatching {
        Socket(host, port).use { socket ->
            socket.soTimeout = 8000
            val output = socket.getOutputStream()
            output.write(
                "POST /pair HTTP/1.1\r\nHost: $host:$port\r\nContent-Type: application/json\r\nContent-Length: ${requestBody.size}\r\nConnection: close\r\n\r\n".encodeToByteArray()
            )
            output.write(requestBody)
            output.flush()

            val responseText = socket.getInputStream().readBytes().decodeToString()
            val body = responseText.substringAfter("\r\n\r\n", "")
            val response = pairingJson.decodeFromString<TvPairingResponse>(body)
            if (response.success) "TV paired successfully" else (response.message ?: "TV pairing failed")
        }
    }.getOrElse {
        "Could not reach TV. Make sure both devices are on the same Wi-Fi."
    }
}

private fun findLocalIpv4(): String? {
    return NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.startsWith("169.254.") != true }
        ?.hostAddress
}
