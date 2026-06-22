@file:OptIn(ExperimentalCoroutinesApi::class)

package to.kuudere.anisuge.platform

import android.util.Log
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import to.kuudere.anisuge.AppComponent

private const val TAG = "DiscordGW"

actual object DiscordRichPresenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gatewayJob: Job? = null
    private var heartbeatJob: Job? = null
    private var session: WebSocketSession? = null
    private var gatewayToken: String? = null
    private var sequence: Int? = null
    private var sessionId: String? = null

    private var enabled = false
    private var authenticated = false
    private var connectionReady = false
    private var lastActivity: DiscordPresenceActivity? = null

    private val json = Json { ignoreUnknownKeys = true }

    actual val availability = DiscordPresenceAvailability(
        supported = true,
        status = "Connected via Gateway",
    )

    actual val isAuthenticated: Boolean get() = authenticated

    actual fun authenticate(token: String) {
        if (token.isBlank()) return
        Log.d(TAG, "authenticate() token.length=${token.length}")
        gatewayToken = token
        authenticated = true
        connectionReady = false
        connectGateway()
    }

    actual fun logout() {
        Log.d(TAG, "logout()")
        enabled = false
        authenticated = false
        connectionReady = false
        gatewayToken = null
        sessionId = null
        sequence = null
        disconnectGateway()
    }

    actual fun configure(enabled: Boolean) {
        Log.d(TAG, "configure(enabled=$enabled) authenticated=$authenticated")
        this.enabled = enabled && authenticated
        if (!this.enabled) {
            sendClearPresence()
        } else if (authenticated && (gatewayJob?.isActive != true)) {
            connectGateway()
        }
        if (this.enabled && connectionReady) {
            lastActivity?.let { sendPresenceUpdate(it) }
        }
    }

    actual fun update(activity: DiscordPresenceActivity) {
        if (!enabled || !authenticated) {
            Log.d(TAG, "update() skipped — enabled=$enabled authenticated=$authenticated")
            return
        }
        lastActivity = activity
        if (!connectionReady) {
            Log.d(TAG, "update() queued — gateway not ready yet")
            return
        }
        sendPresenceUpdate(activity)
    }

    actual fun clear() {
        Log.d(TAG, "clear()")
        if (connectionReady) sendClearPresence()
    }

    actual fun shutdown() {
        Log.d(TAG, "shutdown()")
        logout()
    }

    private fun connectGateway() {
        gatewayJob?.cancel()
        gatewayJob = scope.launch {
            var retryDelay = 5000L
            while (isActive && authenticated) {
                Log.d(TAG, "connectGateway() connecting...")
                try {
                    AppComponent.httpClient.webSocket(
                        urlString = "wss://gateway.discord.gg/?v=10&encoding=json",
                    ) {
                        session = this
                        Log.d(TAG, "WEBSOCKET CONNECTED")
                        retryDelay = 1000L
                        for (frame in incoming) {
                            if (!isActive || !authenticated) break
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleGatewayMessage(text)
                            }
                            if (frame is Frame.Close) {
                                Log.w(TAG, "WS Close frame received")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "connectGateway() error: ${e.message}", e)
                    if (!authenticated) break
                }
                session = null
                connectionReady = false
                Log.d(TAG, "connectGateway() reconnecting in ${retryDelay}ms")
                if (authenticated) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30000L)
                }
            }
            session = null
            connectionReady = false
            Log.d(TAG, "connectGateway() exited")
        }
    }

    private suspend fun WebSocketSession.handleGatewayMessage(text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (obj == null) {
            Log.w(TAG, "handleGatewayMessage() failed to parse: ${text.take(200)}")
            return
        }
        val op = obj["op"]?.jsonPrimitive?.int ?: return

        when (op) {
            0 -> handleDispatch(obj)
            7 -> Log.d(TAG, "Op 7 Reconnect requested")
            9 -> handleInvalidSession(obj)
            10 -> handleHello(obj)
            11 -> Log.d(TAG, "Op 11 Heartbeat ACK")
            else -> Log.d(TAG, "Unhandled op: $op")
        }
    }

    private suspend fun WebSocketSession.handleHello(obj: JsonObject) {
        val d = obj["d"]?.jsonObject ?: return
        val heartbeatInterval = d["heartbeat_interval"]?.jsonPrimitive?.long ?: return
        Log.d(TAG, "Op 10 Hello heartbeat_interval=$heartbeatInterval")
        startHeartbeat(heartbeatInterval)
        identify()
    }

    private suspend fun WebSocketSession.identify() {
        val token = gatewayToken ?: return
        Log.d(TAG, "Op 2 Identify sending...")
        val payload = buildJsonObject {
            put("op", 2)
            putJsonObject("d") {
                put("token", token)
                putJsonObject("properties") {
                    put("os", "Android")
                    put("browser", "Anisurge")
                    put("device", "Anisurge")
                }
                putJsonObject("presence") {
                    put("status", "online")
                    put("since", 0)
                    put("activities", JsonArray(emptyList()))
                    put("afk", false)
                }
            }
        }
        send(Frame.Text(payload.toString()))
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                try {
                    val payload = buildJsonObject {
                        put("op", 1)
                        put("d", sequence?.let { JsonPrimitive(it) } ?: JsonNull)
                    }
                    Log.d(TAG, "Op 1 Heartbeat seq=$sequence")
                    session?.send(Frame.Text(payload.toString()))
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error: ${e.message}")
                    break
                }
            }
        }
    }

    private fun handleDispatch(obj: JsonObject) {
        sequence = obj["s"]?.jsonPrimitive?.intOrNull ?: (sequence?.plus(1) ?: 1)
        val t = obj["t"]?.jsonPrimitive?.contentOrNull ?: return
        val d = obj["d"]?.jsonObject ?: return

        when (t) {
            "READY" -> {
                sessionId = d["session_id"]?.jsonPrimitive?.contentOrNull
                val user = d["user"]?.jsonObject
                val username = user?.get("username")?.jsonPrimitive?.contentOrNull ?: "?"
                Log.d(TAG, "READY received — session_id=$sessionId user=$username")
                connectionReady = true
                if (enabled) {
                    lastActivity?.let { sendPresenceUpdate(it) }
                }
            }
            "RESUMED" -> {
                connectionReady = true
                Log.d(TAG, "RESUMED")
            }
        }
    }

    private fun handleInvalidSession(obj: JsonObject) {
        val canResume = obj["d"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        Log.d(TAG, "Op 9 InvalidSession canResume=$canResume")
        if (!canResume) {
            sessionId = null
            sequence = null
            connectionReady = false
            scope.launch {
                try {
                    session?.identify()
                } catch (_: Exception) { }
            }
        }
    }

    private fun sendPresenceUpdate(activity: DiscordPresenceActivity) {
        scope.launch {
            try {
                val actObj = buildJsonObject {
                    put("name", "Anisurge")
                    put("type", 3)
                    put("state", activity.state.trimPresenceField(fallback = "Watching"))
                    put("details", activity.details.trimPresenceField(fallback = "Anime"))
                    val timestamps = buildJsonObject {
                        activity.startTimestampMillis?.let { put("start", it) }
                        activity.endTimestampMillis?.let { put("end", it) }
                    }
                    if (timestamps.isNotEmpty()) put("timestamps", timestamps)
                }
                val payload = buildJsonObject {
                    put("op", 3)
                    putJsonObject("d") {
                        put("since", 0)
                        put("status", "online")
                        put("afk", false)
                        putJsonArray("activities") {
                            add(actObj)
                        }
                    }
                }
                Log.d(TAG, "Op 3 Presence: state=\"${activity.state}\" details=\"${activity.details}\"")
                session?.send(Frame.Text(payload.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "sendPresenceUpdate error: ${e.message}")
            }
        }
    }

    private fun sendClearPresence() {
        scope.launch {
            try {
                val payload = buildJsonObject {
                    put("op", 3)
                    putJsonObject("d") {
                        put("since", 0)
                        put("status", "online")
                        put("afk", false)
                        put("activities", JsonArray(emptyList()))
                    }
                }
                Log.d(TAG, "Op 3 Clear presence")
                session?.send(Frame.Text(payload.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "sendClearPresence error: ${e.message}")
            }
        }
    }

    private fun disconnectGateway() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        gatewayJob?.cancel()
        gatewayJob = null
        session = null
    }
}

private fun String.trimPresenceField(fallback: String? = null): String {
    val value = trim().ifBlank { fallback ?: return "" }
    return value.take(128)
}
