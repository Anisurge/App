package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import to.kuudere.anisuge.data.models.W2gPlayerState
import to.kuudere.anisuge.data.models.W2gRoomDetail
import to.kuudere.anisuge.data.models.W2gWsEnvelope

class W2gWsClient(
    private val inviteCode: String,
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed class Event {
        data class RoomInfo(val room: W2gRoomDetail) : Event()
        data class PlayerState(val state: W2gPlayerState) : Event()
        data class EpisodeChange(
            val animeId: String,
            val animeTitle: String?,
            val animePoster: String?,
            val anilistId: Int?,
            val malId: Int?,
            val episodeNumber: Int,
            val server: String,
            val language: String?,
            val quality: String?,
            val streamUrl: String?,
            val streamHeaders: Map<String, String>?,
        ) : Event()

        data object RoomClosed : Event()

        data class MemberJoined(val userId: String, val username: String?, val avatarUrl: String?) : Event()
        data class MemberLeft(val userId: String, val username: String?) : Event()
        data class HostChanged(val userId: String, val username: String?) : Event()
        data class ChatMessage(
            val id: String,
            val userId: String,
            val username: String?,
            val avatarUrl: String?,
            val body: String,
            val createdAt: String,
        ) : Event()

        data class Error(val message: String) : Event()
        data object Connected : Event()
        data object Disconnected : Event()
    }

    private var session: WebSocketSession? = null

    private fun wsUrl(): String {
        val httpBase = AnisurgeApi.v1Base.trimEnd('/')
        val wsBase = when {
            httpBase.startsWith("https://") -> httpBase.replaceFirst("https://", "wss://")
            httpBase.startsWith("http://") -> httpBase.replaceFirst("http://", "ws://")
            else -> "wss://$httpBase"
        }
        return URLBuilder("$wsBase/w2g/ws").apply {
            parameters.append("room", inviteCode)
        }.buildString()
    }

    fun connect(): Flow<Event> = callbackFlow {
        val stored = sessionStore.get()
        val token = stored?.anisurgeToken?.takeIf { it.isNotBlank() }
        if (token == null) {
            trySend(Event.Error("Not signed in"))
            close()
            return@callbackFlow
        }

        val url = wsUrl()
        try {
            httpClient.webSocket(
                urlString = url,
                request = {
                    header(HttpHeaders.Authorization, "Bearer $token")
                },
            ) {
                session = this
                trySend(Event.Connected)
                try {
                    for (frame in incoming) {
                        if (!isActive) break
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        val envelope = runCatching {
                            json.decodeFromString<W2gWsEnvelope>(text)
                        }.getOrNull() ?: continue

                        val event = parseEnvelope(envelope)
                        if (event != null) {
                            trySend(event)
                        }
                    }
                } finally {
                    session = null
                    trySend(Event.Disconnected)
                }
            }
        } catch (e: Exception) {
            println("[W2gWsClient] connect error: ${e.message}")
            session = null
            trySend(Event.Error(e.message ?: "Connection failed"))
            trySend(Event.Disconnected)
        }

        awaitClose {
            session = null
        }
    }

    private fun parseEnvelope(envelope: W2gWsEnvelope): Event? {
        val data = envelope.data ?: return when (envelope.type) {
            "pong" -> null
            "error" -> Event.Error(envelope.message ?: "Unknown error")
            else -> null
        }

        return try {
            when (envelope.type) {
                "room_info" -> {
                    val room = json.decodeFromJsonElement(W2gRoomDetail.serializer(), data)
                    Event.RoomInfo(room)
                }
                "player_state" -> {
                    val state = json.decodeFromJsonElement(W2gPlayerState.serializer(), data)
                    Event.PlayerState(state)
                }
                "episode_change" -> {
                    val obj = data.jsonObject
                    val rawHeaders = obj["stream_headers"]
                    val headers = if (rawHeaders != null && rawHeaders is kotlinx.serialization.json.JsonObject) {
                        rawHeaders.mapValues { it.value.jsonPrimitive.content }
                    } else null
                    Event.EpisodeChange(
                        animeId = obj["anime_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        animeTitle = obj["anime_title"]?.jsonPrimitive?.contentOrNull,
                        animePoster = obj["anime_poster"]?.jsonPrimitive?.contentOrNull,
                        anilistId = obj["anilist_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                        malId = obj["mal_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                        episodeNumber = obj["episode_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                        server = obj["server"]?.jsonPrimitive?.content ?: "suzu",
                        language = obj["language"]?.jsonPrimitive?.contentOrNull,
                        quality = obj["quality"]?.jsonPrimitive?.contentOrNull,
                        streamUrl = obj["stream_url"]?.jsonPrimitive?.contentOrNull,
                        streamHeaders = headers,
                    )
                }
                "room_closed" -> Event.RoomClosed
                "member_joined" -> {
                    val obj = data.jsonObject
                    Event.MemberJoined(
                        userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                        username = obj["username"]?.jsonPrimitive?.contentOrNull,
                        avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull,
                    )
                }
                "member_left" -> {
                    val obj = data.jsonObject
                    Event.MemberLeft(
                        userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                        username = obj["username"]?.jsonPrimitive?.contentOrNull,
                    )
                }
                "host_changed" -> {
                    val obj = data.jsonObject
                    Event.HostChanged(
                        userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                        username = obj["username"]?.jsonPrimitive?.contentOrNull,
                    )
                }
                "chat" -> {
                    val obj = data.jsonObject
                    Event.ChatMessage(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                        username = obj["username"]?.jsonPrimitive?.contentOrNull,
                        avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull,
                        body = obj["body"]?.jsonPrimitive?.content ?: "",
                        createdAt = obj["created_at"]?.jsonPrimitive?.content ?: "",
                    )
                }
                "error" -> Event.Error(envelope.message ?: "Unknown error")
                else -> null
            }
        } catch (e: Exception) {
            println("[W2gWsClient] parseEnvelope error: ${e.message}")
            null
        }
    }

    private fun sendRaw(text: String) {
        kotlinx.coroutines.runBlocking {
            runCatching {
                session?.send(Frame.Text(text))
            }
        }
    }

    fun sendPlay(currentTime: Double) {
        sendRaw(json.encodeToString(W2gWsEnvelope("play", buildJsonObject {
            put("currentTime", currentTime)
        })))
    }

    fun sendPause(currentTime: Double) {
        sendRaw(json.encodeToString(W2gWsEnvelope("pause", buildJsonObject {
            put("currentTime", currentTime)
        })))
    }

    fun sendSeek(currentTime: Double, playing: Boolean = true) {
        sendRaw(json.encodeToString(W2gWsEnvelope("seek", buildJsonObject {
            put("currentTime", currentTime)
            put("playing", playing)
        })))
    }

    fun sendChangeEpisode(
        animeId: String,
        episodeNumber: Int,
        server: String,
        language: String?,
        quality: String?,
        animeTitle: String? = null,
        animePoster: String? = null,
        anilistId: Int? = null,
        malId: Int? = null,
        streamUrl: String? = null,
        streamHeaders: Map<String, String>? = null,
    ) {
        sendRaw(json.encodeToString(W2gWsEnvelope("change_episode", buildJsonObject {
            put("anime_id", animeId)
            animeTitle?.let { put("anime_title", it) }
            animePoster?.let { put("anime_poster", it) }
            anilistId?.let { put("anilist_id", it) }
            malId?.let { put("mal_id", it) }
            put("episode_number", episodeNumber)
            put("server", server)
            language?.let { put("language", it) }
            quality?.let { put("quality", it) }
            streamUrl?.let { put("stream_url", it) }
            streamHeaders?.let {
                put("stream_headers", buildJsonObject {
                    it.forEach { (k, v) -> put(k, v) }
                })
            }
        })))
    }

    fun sendChat(body: String) {
        sendRaw(json.encodeToString(W2gWsEnvelope("chat", buildJsonObject {
            put("body", body)
        })))
    }

    fun sendPing() {
        sendRaw(json.encodeToString(W2gWsEnvelope("ping", null)))
    }

    fun disconnect() {
        session = null
    }
}
