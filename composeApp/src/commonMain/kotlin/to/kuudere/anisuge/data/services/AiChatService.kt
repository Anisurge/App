package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import to.kuudere.anisuge.data.models.AiChatAnimeCard
import to.kuudere.anisuge.data.models.AiChatMessageRequest
import to.kuudere.anisuge.data.models.AiChatQuotaResponse
import to.kuudere.anisuge.data.models.AiChatSendRequest
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

/**
 * AiChatService - wraps the BFF ai-chat endpoints.
 *
 * getQuota      - daily usage / remaining for the current user
 * streamMessage - SSE-based streaming chat, emits AiChatToken events
 */
class AiChatService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getQuota(): Result<AiChatQuotaResponse> {
        val stored = sessionStore.get()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/ai-chat/quota") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Quota fetch failed (${response.status.value})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a message and emits AiChatToken events as they stream from the BFF.
     * Emits AiChatToken.Done at the end, or throws on error.
     */
    fun streamMessage(
        message: String,
        history: List<AiChatMessageRequest> = emptyList(),
    ): Flow<AiChatToken> = flow {
        val stored = sessionStore.get()
            ?: throw IllegalStateException("Not signed in")

        val request = AiChatSendRequest(
            message = message,
            history = history.takeLast(20),
        )

        httpClient.preparePost("${AnisurgeApi.v1Base}/ai-chat/message") {
            applyAnisurgeAuth(stored)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val text = try { response.body<String>() } catch (e: Exception) { "" }
                val msg = try {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
                } catch (e: Exception) { null }
                throw Exception(msg ?: "AI chat error (${response.status.value})")
            }

            val channel = response.bodyAsChannel()
            val sb = StringBuilder()

            var animeCard: AiChatAnimeCard? = null

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                try {
                    val element = json.parseToJsonElement(data)
                    val obj = element.jsonObject

                    // Check for anime card event
                    val animeJson = obj["anime"]
                    if (animeJson != null) {
                        animeCard = json.decodeFromJsonElement(animeJson)
                        emit(AiChatToken.AnimeCard(animeCard!!))
                        continue
                    }

                    val token = obj["token"]?.jsonPrimitive?.content
                    if (!token.isNullOrEmpty()) {
                        sb.append(token)
                        emit(AiChatToken.Chunk(token))
                    }
                } catch (e: Exception) {
                    // Malformed SSE chunk - skip
                }
            }

            emit(AiChatToken.Done(sb.toString(), animeCard))
        }
    }
}

sealed class AiChatToken {
    /** A partial text token streaming in */
    data class Chunk(val text: String) : AiChatToken()
    /** Anime card was resolved from the server */
    data class AnimeCard(val card: to.kuudere.anisuge.data.models.AiChatAnimeCard) : AiChatToken()
    /** Stream ended; full accumulated text and optional anime card */
    data class Done(
        val fullText: String,
        val anime: to.kuudere.anisuge.data.models.AiChatAnimeCard? = null,
    ) : AiChatToken()
}
