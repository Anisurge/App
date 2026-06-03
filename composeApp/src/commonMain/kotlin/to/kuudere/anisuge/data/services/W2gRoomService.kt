package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.W2gRoomCreateRequest
import to.kuudere.anisuge.data.models.W2gRoomCreateResponse
import to.kuudere.anisuge.data.models.W2gJoinRequest
import to.kuudere.anisuge.data.models.W2gJoinResponse
import to.kuudere.anisuge.data.models.W2gRoomListResponse
import to.kuudere.anisuge.data.models.W2gRoomUpdateRequest
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth
import to.kuudere.anisuge.utils.currentTimeMillis

class W2gRoomService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = AnisurgeApi.v1Base
    private var lastErrorMessage: String? = null

    val lastError: String?
        get() = lastErrorMessage

    private suspend fun parseError(response: io.ktor.client.statement.HttpResponse): String {
        return runCatching {
            json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
        }.getOrElse { "Request failed (${response.status.value})" }
    }

    suspend fun listRooms(page: Int = 1, limit: Int = 20, query: String? = null): W2gRoomListResponse? {
        return try {
            lastErrorMessage = null
            val response = httpClient.get("$baseUrl/w2g/rooms") {
                parameter("page", page)
                parameter("limit", limit)
                query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
                parameter("_ts", currentTimeMillis())
                header("Cache-Control", "no-cache")
                header("Pragma", "no-cache")
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                lastErrorMessage = parseError(response)
                println("[W2gRoomService] listRooms HTTP ${response.status.value}: $lastErrorMessage")
                null
            }
        } catch (e: Exception) {
            lastErrorMessage = e.message
            println("[W2gRoomService] listRooms error: ${e.message}")
            null
        }
    }

    suspend fun createRoom(request: W2gRoomCreateRequest): W2gRoomCreateResponse? {
        return try {
            lastErrorMessage = null
            val stored = sessionStore.get() ?: return null
            val response = httpClient.post("$baseUrl/w2g/rooms") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                lastErrorMessage = parseError(response)
                println("[W2gRoomService] createRoom HTTP ${response.status.value}: $lastErrorMessage")
                null
            }
        } catch (e: Exception) {
            lastErrorMessage = e.message
            println("[W2gRoomService] createRoom error: ${e.message}")
            null
        }
    }

    suspend fun joinRoom(inviteCode: String, password: String? = null): W2gJoinResponse? {
        return try {
            lastErrorMessage = null
            val stored = sessionStore.get() ?: return null
            val response = httpClient.post("$baseUrl/w2g/rooms/$inviteCode/join") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(W2gJoinRequest(password = password))
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                lastErrorMessage = parseError(response)
                println("[W2gRoomService] joinRoom HTTP ${response.status.value}: $lastErrorMessage")
                null
            }
        } catch (e: Exception) {
            lastErrorMessage = e.message
            println("[W2gRoomService] joinRoom error: ${e.message}")
            null
        }
    }

    suspend fun leaveRoom(inviteCode: String, action: String? = null): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val response = httpClient.post("$baseUrl/w2g/rooms/$inviteCode/leave") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(action?.let { mapOf("action" to it) } ?: emptyMap<String, String>())
            }
            if (response.status.isSuccess()) {
                lastErrorMessage = null
                true
            } else {
                lastErrorMessage = parseError(response)
                println("[W2gRoomService] leaveRoom HTTP ${response.status.value}: $lastErrorMessage")
                false
            }
        } catch (e: Exception) {
            lastErrorMessage = e.message
            println("[W2gRoomService] leaveRoom error: ${e.message}")
            false
        }
    }

    suspend fun updateRoom(inviteCode: String, request: W2gRoomUpdateRequest): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val response = httpClient.patch("$baseUrl/w2g/rooms/$inviteCode") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("[W2gRoomService] updateRoom error: ${e.message}")
            false
        }
    }
}
