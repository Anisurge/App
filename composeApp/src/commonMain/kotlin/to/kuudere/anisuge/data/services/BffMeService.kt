package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.BffMeResponse
import to.kuudere.anisuge.data.models.BffPfpUploadResponse
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.models.toUserProfile
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth
import to.kuudere.anisuge.platform.ChatImagePick

class BffMeService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMe(): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/me") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) {
                val body: BffMeResponse = response.body()
                Result.success(body.user.toUserProfile())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to load profile (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            println("[BffMeService] fetchMe error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUsername(username: String): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.patch("${AnisurgeApi.v1Base}/me") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(BffPatchMeRequest(username = username.trim()))
            }
            if (response.status.isSuccess()) {
                val body: BffMeResponse = response.body()
                Result.success(body.user.toUserProfile())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to update username (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            println("[BffMeService] updateUsername error: ${e.message}")
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadCustomPfp(pick: ChatImagePick): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (pick.bytes.isEmpty()) {
            return Result.failure(IllegalStateException("Selected image is empty"))
        }
        if (pick.bytes.size > CHAT_IMAGE_MAX_BYTES) {
            return Result.failure(IllegalStateException("Image must be less than 2.5 MB"))
        }
        return try {
            val safeName = pick.fileName.ifBlank { "image.jpg" }
            println(
                "[BffMeService] uploadCustomPfp json bytes=${pick.bytes.size} " +
                    "mime=${pick.mimeType} name=$safeName",
            )
            val response = httpClient.post("${AnisurgeApi.v1Base}/me/pfp/upload") {
                method = HttpMethod.Post
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(
                    BffPfpJsonUploadRequest(
                        imageBase64 = Base64.Default.encode(pick.bytes),
                        mimeType = pick.mimeType,
                        fileName = safeName,
                    ),
                )
            }
            if (response.status.isSuccess()) {
                val body: BffPfpUploadResponse = response.body()
                val profile = body.user?.toUserProfile()
                    ?: return Result.failure(Exception("No profile in upload response"))
                Result.success(profile)
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Upload failed (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            println("[BffMeService] uploadCustomPfp error: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val CHAT_IMAGE_MAX_BYTES = (2.5 * 1024 * 1024).toLong()
    }
}

@Serializable
private data class BffPatchMeRequest(
    val username: String,
)

@Serializable
private data class BffPfpJsonUploadRequest(
    val imageBase64: String,
    val mimeType: String,
    val fileName: String,
)
