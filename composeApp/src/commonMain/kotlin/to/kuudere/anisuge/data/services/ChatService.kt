package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.ChatLiveEvent
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.data.models.ChatMessagesResponse
import to.kuudere.anisuge.data.models.ChatPostMessageRequest
import to.kuudere.anisuge.data.models.ChatRoomResponse
import to.kuudere.anisuge.data.models.ChatWsEnvelope
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class ChatService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val GLOBAL_ROOM_SLUG = "global"
    }

    private fun wsUrl(roomSlug: String): String {
        val httpBase = AnisurgeApi.v1Base.trimEnd('/')
        val wsBase = when {
            httpBase.startsWith("https://") -> httpBase.replaceFirst("https://", "wss://")
            httpBase.startsWith("http://") -> httpBase.replaceFirst("http://", "ws://")
            else -> "wss://$httpBase"
        }
        return URLBuilder("$wsBase/chat/ws").apply {
            parameters.append("room", roomSlug)
        }.buildString()
    }

    private suspend fun parseError(response: io.ktor.client.statement.HttpResponse): String {
        return runCatching {
            json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
        }.getOrElse { "Request failed (${response.status.value})" }
    }

    suspend fun fetchRoom(roomSlug: String = GLOBAL_ROOM_SLUG): ChatRoomResponse? {
        val stored = sessionStore.get() ?: return null
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/chat/rooms/$roomSlug") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) response.body() else {
                println("[ChatService] fetchRoom HTTP ${response.status.value}: ${parseError(response)}")
                null
            }
        } catch (e: Exception) {
            println("[ChatService] fetchRoom error: ${e.message}")
            null
        }
    }

    suspend fun fetchHistory(
        roomSlug: String = GLOBAL_ROOM_SLUG,
        limit: Int = 50,
        before: String? = null,
    ): ChatMessagesResponse? {
        val stored = sessionStore.get() ?: return null
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/chat/rooms/$roomSlug/messages") {
                applyAnisurgeAuth(stored)
                parameter("limit", limit)
                before?.let { parameter("before", it) }
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                println("[ChatService] fetchHistory HTTP ${response.status.value}: ${parseError(response)}")
                null
            }
        } catch (e: Exception) {
            println("[ChatService] fetchHistory error: ${e.message}")
            null
        }
    }

    suspend fun postMessage(
        body: String,
        roomSlug: String = GLOBAL_ROOM_SLUG,
    ): Result<ChatMessage> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty"))
        }
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/chat/rooms/$roomSlug/messages") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(ChatPostMessageRequest(trimmed))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            println("[ChatService] postMessage error: ${e.message}")
            Result.failure(e)
        }
    }

    fun connectLive(roomSlug: String = GLOBAL_ROOM_SLUG): Flow<ChatLiveEvent> = callbackFlow {
        val stored = sessionStore.get()
        val token = stored?.anisurgeToken?.takeIf { it.isNotBlank() }
        if (token == null) {
            trySend(ChatLiveEvent.Error("Not signed in"))
            close()
            return@callbackFlow
        }

        val url = wsUrl(roomSlug)
        try {
            httpClient.webSocket(
                urlString = url,
                request = {
                    header(HttpHeaders.Authorization, "Bearer $token")
                },
            ) {
                trySend(ChatLiveEvent.Connected)
                try {
                    for (frame in incoming) {
                        if (!isActive) break
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        val envelope = runCatching {
                            json.decodeFromString<ChatWsEnvelope>(text)
                        }.getOrNull() ?: continue

                        when (envelope.type) {
                            "message" -> envelope.data?.let { trySend(ChatLiveEvent.Message(it)) }
                            "error" -> envelope.message?.let { trySend(ChatLiveEvent.Error(it)) }
                        }
                    }
                } finally {
                    trySend(ChatLiveEvent.Disconnected)
                }
            }
        } catch (e: Exception) {
            println("[ChatService] connectLive error: ${e.message}")
            trySend(ChatLiveEvent.Error(e.message ?: "Connection failed"))
            trySend(ChatLiveEvent.Disconnected)
        }

        awaitClose { }
    }
}
