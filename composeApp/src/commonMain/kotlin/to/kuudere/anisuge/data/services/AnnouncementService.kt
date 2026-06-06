package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.AnnouncementCreateRequest
import to.kuudere.anisuge.data.models.AnnouncementCreateResponse
import to.kuudere.anisuge.data.models.AnnouncementPollVoteRequest
import to.kuudere.anisuge.data.models.AnnouncementPollVoteResponse
import to.kuudere.anisuge.data.models.AnnouncementReadResponse
import to.kuudere.anisuge.data.models.AnnouncementStatusResponse
import to.kuudere.anisuge.data.models.AnnouncementVoteResponse
import to.kuudere.anisuge.data.models.AnnouncementsResponse
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class AnnouncementService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "${AnisurgeApi.v1Base}/announcements"

    private suspend fun parseError(response: HttpResponse): String =
        runCatching {
            json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
        }.getOrElse { "Request failed (${response.status.value})" }

    suspend fun getStatus(): AnnouncementStatusResponse? {
        val stored = sessionStore.get() ?: return null
        return runCatching {
            val response = httpClient.get("$baseUrl/status") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) response.body() else null
        }.getOrNull()
    }

    suspend fun list(
        limit: Int = 30,
        before: String? = null,
        includeDrafts: Boolean = false,
    ): Result<AnnouncementsResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            val response = httpClient.get(baseUrl) {
                applyAnisurgeAuth(stored)
                parameter("limit", limit)
                before?.let { parameter("before", it) }
                if (includeDrafts) parameter("includeDrafts", "true")
            }
            if (response.status.isSuccess()) response.body() else throw Exception(parseError(response))
        }
    }

    suspend fun create(request: AnnouncementCreateRequest): Result<AnnouncementCreateResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            val response = httpClient.post(baseUrl) {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) response.body() else throw Exception(parseError(response))
        }
    }

    suspend fun update(id: String, request: AnnouncementCreateRequest): Result<Unit> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            val response = httpClient.patch("$baseUrl/$id") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) throw Exception(parseError(response))
        }
    }

    suspend fun toggleUpvote(id: String): Result<AnnouncementVoteResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            val response = httpClient.post("$baseUrl/$id/vote") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(mapOf<String, String>())
            }
            if (response.status.isSuccess()) response.body() else throw Exception(parseError(response))
        }
    }

    suspend fun markRead(id: String): Result<AnnouncementReadResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            val response = httpClient.post("$baseUrl/$id/read") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(mapOf<String, String>())
            }
            if (response.status.isSuccess()) response.body() else throw Exception(parseError(response))
        }
    }

    suspend fun markAllRead(): Result<AnnouncementReadResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            val response = httpClient.post("$baseUrl/read-all") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(mapOf<String, String>())
            }
            if (response.status.isSuccess()) response.body() else throw Exception(parseError(response))
        }
    }

    suspend fun votePoll(id: String, optionId: String): Result<AnnouncementPollVoteResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return runCatching {
            val response = httpClient.post("$baseUrl/$id/poll/vote") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(AnnouncementPollVoteRequest(optionId))
            }
            if (response.status.isSuccess()) response.body() else throw Exception(parseError(response))
        }
    }
}
