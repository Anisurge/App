package to.kuudere.anisuge.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import to.kuudere.anisuge.BuildConfig

private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
private const val DEFAULT_LARGE_IMAGE_URL = "https://raw.githubusercontent.com/Anisurge/App/main/composeApp/src/commonMain/composeResources/drawable/logo.png"
private const val DOWNLOAD_URL = "https://www.anisurge.lol/#download"
private const val GITHUB_URL = "https://github.com/Anisurge/App/releases"

actual object DiscordRichPresenceManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) { install(WebSockets) }

    private var enabled = false
    private var token = ""
    private var session: DefaultClientWebSocketSession? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var ready = false
    private var readySignal = CompletableDeferred<Unit>()
    private var sequence: Int? = null
    private var currentActivity: DiscordPresenceActivity? = null

    actual fun configureMobile(enabled: Boolean, token: String) {
        scope.launch {
            mutex.withLock {
                this@DiscordRichPresenceManager.enabled = enabled
                this@DiscordRichPresenceManager.token = token.trim()
                if (!canRun()) {
                    sendClearLocked()
                    closeLocked()
                    return@withLock
                }
                currentActivity?.let { sendActivityLocked(it) }
            }
        }
    }

    actual fun update(activity: DiscordPresenceActivity) {
        currentActivity = activity
        scope.launch {
            mutex.withLock {
                if (!canRun()) return@withLock
                sendActivityLocked(activity)
            }
        }
    }

    actual fun clear() {
        currentActivity = null
        scope.launch {
            mutex.withLock { sendClearLocked() }
        }
    }

    actual fun shutdown() {
        runBlocking {
            mutex.withLock {
                sendClearLocked()
                closeLocked()
            }
        }
    }

    private fun canRun(): Boolean = enabled && token.isNotBlank()

    private suspend fun sendActivityLocked(activity: DiscordPresenceActivity) {
        runCatching {
            if (!ensureConnectedLocked()) return
            sendPresence(activity)
        }.onFailure { closeLocked() }
    }

    private suspend fun sendClearLocked() {
        runCatching {
            if (session?.isActive == true && ready) sendPresence(null)
        }.onFailure { closeLocked() }
    }

    private suspend fun ensureConnectedLocked(): Boolean {
        if (session?.isActive == true && ready) return true
        closeLocked()

        ready = false
        readySignal = CompletableDeferred()
        session = client.webSocketSession(GATEWAY_URL)
        receiveJob = scope.launch { receiveLoop() }

        return runCatching {
            withTimeout(10_000) { readySignal.await() }
            true
        }.getOrElse {
            closeLocked()
            false
        }
    }

    private suspend fun receiveLoop() {
        val activeSession = session ?: return
        runCatching {
            for (frame in activeSession.incoming) {
                if (frame is Frame.Text) handleGatewayMessage(frame.readText())
            }
        }
        ready = false
    }

    private suspend fun handleGatewayMessage(message: String) {
        val payload = json.parseToJsonElement(message).jsonObject
        payload["s"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull?.let { sequence = it }

        when (payload["op"]?.jsonPrimitive?.intOrNull) {
            0 -> handleDispatch(payload)
            7 -> reconnect()
            9 -> reconnect()
            10 -> handleHello(payload)
            11 -> Unit
        }
    }

    private suspend fun handleHello(payload: JsonObject) {
        val heartbeatInterval = payload["d"]
            ?.jsonObject
            ?.get("heartbeat_interval")
            ?.jsonPrimitive
            ?.longOrNull
            ?: return

        sendIdentify()
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatInterval)
                sendHeartbeat()
            }
        }
    }

    private fun handleDispatch(payload: JsonObject) {
        when (payload["t"]?.jsonPrimitive?.content) {
            "READY", "RESUMED" -> {
                ready = true
                if (!readySignal.isCompleted) readySignal.complete(Unit)
            }
        }
    }

    private suspend fun reconnect() {
        closeLocked()
        currentActivity?.takeIf { canRun() }?.let { sendActivityLocked(it) }
    }

    private suspend fun sendIdentify() {
        sendGatewayPayload(
            op = 2,
            data = buildJsonObject {
                put("token", token)
                put("capabilities", 65)
                put("compress", false)
                put("largeThreshold", 100)
                putJsonObject("properties") {
                    put("os", "Windows")
                    put("browser", "Discord Client")
                    put("device", "ktor")
                }
            }
        )
    }

    private suspend fun sendHeartbeat() {
        sendGatewayPayload(op = 1, data = sequence?.let(::JsonPrimitive) ?: JsonNull)
    }

    private suspend fun sendPresence(activity: DiscordPresenceActivity?) {
        sendGatewayPayload(
            op = 3,
            data = buildJsonObject {
                put("since", 0)
                put("status", "online")
                put("afk", false)
                put("activities", activity?.let { buildJsonArray { add(activityJson(it)) } } ?: JsonArray(emptyList()))
            }
        )
    }

    private fun activityJson(activity: DiscordPresenceActivity): JsonObject = buildJsonObject {
        put("name", "Anisurge")
        put("type", 3)
        put("details", activity.details.trimPresenceField("Watching anime"))
        put("state", activity.state.trimPresenceField("On Anisurge"))
        BuildConfig.DISCORD_CLIENT_ID.trim().takeIf { it.isNotBlank() }?.let { put("application_id", it) }
        putJsonObject("assets") {
            resolveMobileImage(BuildConfig.DISCORD_LARGE_IMAGE_KEY).let { put("large_image", it) }
            put("large_text", activity.largeImageText.trimPresenceField("Anisurge"))
            resolveMobileImage(BuildConfig.DISCORD_SMALL_IMAGE_KEY).let { put("small_image", it) }
            activity.smallImageText?.trimPresenceField()?.let { put("small_text", it) }
        }
        put("buttons", JsonArray(listOf(JsonPrimitive("Download App"), JsonPrimitive("GitHub"))))
        putJsonObject("metadata") {
            put("button_urls", JsonArray(listOf(JsonPrimitive(DOWNLOAD_URL), JsonPrimitive(GITHUB_URL))))
        }
        val timestamps = buildJsonObject {
            activity.startTimestampMillis?.let { put("start", it) }
            activity.endTimestampMillis?.let { put("end", it) }
        }
        if (timestamps.isNotEmpty()) put("timestamps", timestamps)
    }

    private suspend fun sendGatewayPayload(op: Int, data: kotlinx.serialization.json.JsonElement) {
        val payload = buildJsonObject {
            put("op", op)
            put("d", data)
        }
        session?.send(Frame.Text(payload.toString()))
    }

    private suspend fun closeLocked() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        receiveJob?.cancel()
        receiveJob = null
        ready = false
        if (!readySignal.isCompleted) readySignal.cancel()
        runCatching { session?.close() }
        session = null
        sequence = null
    }
}

private fun resolveMobileImage(value: String): String {
    val image = value.trim()
    return when {
        image.startsWith("http://") || image.startsWith("https://") || image.startsWith("mp:") -> image
        image.startsWith("app-assets/") -> "mp:$image"
        else -> DEFAULT_LARGE_IMAGE_URL
    }
}

private fun String.trimPresenceField(fallback: String? = null): String? {
    val value = trim().ifBlank { fallback ?: return null }
    return value.take(128).let { if (it.length == 1) "$it " else it }
}
