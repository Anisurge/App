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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.BffMeResponse
import to.kuudere.anisuge.data.models.BffPfpUploadResponse
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.models.toUserProfile
import to.kuudere.anisuge.data.models.BffConnectReanimeRequest
import to.kuudere.anisuge.data.models.BffConnectReanimeResponse
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

    suspend fun updateProfileDetails(bio: String?, website: String?): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.patch("${AnisurgeApi.v1Base}/me") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(
                    BffPatchProfileDetailsRequest(
                        bio = bio?.trim()?.takeIf { it.isNotBlank() },
                        website = website?.trim()?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            if (response.status.isSuccess()) {
                val body: BffMeResponse = response.body()
                Result.success(body.user.toUserProfile())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to update profile (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadCustomPfp(pick: ChatImagePick): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (pick.bytes.isEmpty()) {
            return Result.failure(IllegalStateException("Selected image is empty"))
        }
        val isVideo = pick.mimeType == "video/mp4"
        val maxBytes = if (isVideo) PROFILE_VIDEO_MAX_BYTES else CHAT_IMAGE_MAX_BYTES
        if (pick.bytes.size > maxBytes) {
            val message = if (isVideo) {
                "Video profile pictures must be cropped square, 6 seconds or shorter, and less than 3 MB"
            } else {
                "Image must be less than 2.5 MB"
            }
            return Result.failure(IllegalStateException(message))
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

    suspend fun updateEquippedFrame(
        currentEquipped: JsonObject?,
        frameUrl: String?,
        frameItemId: String?,
    ): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        val base = currentEquipped
            ?.entries
            ?.associate { it.key to it.value }
            ?.toMutableMap()
            ?: mutableMapOf()
        listOf("chatAvatarFrame", "chatAvatarFrameItemId", "avatarFrame", "frame").forEach { base.remove(it) }
        if (!frameUrl.isNullOrBlank()) {
            base["chatAvatarFrame"] = JsonPrimitive(frameUrl)
            if (!frameItemId.isNullOrBlank()) {
                base["chatAvatarFrameItemId"] = JsonPrimitive(frameItemId)
            }
        }
        val equipped = JsonObject(base.mapValues { it.value })
        return try {
            val response = httpClient.patch("${AnisurgeApi.v1Base}/me") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(BffPatchEquippedRequest(equipped = equipped))
            }
            if (response.status.isSuccess()) {
                val body: BffMeResponse = response.body()
                Result.success(body.user.toUserProfile())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to update frame (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChatProfilePrivacy(hidden: Boolean): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.patch("${AnisurgeApi.v1Base}/me") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(
                    BffPatchProfileExtraRequest(
                        profileExtra = JsonObject(
                            mapOf("chatProfilePrivate" to JsonPrimitive(hidden)),
                        ),
                    ),
                )
            }
            if (response.status.isSuccess()) {
                val body: BffMeResponse = response.body()
                Result.success(body.user.toUserProfile())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to update privacy (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPremiumCheckoutSession(): Result<BffPremiumCheckoutSessionResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/premium/checkout-session") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to start checkout (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    companion object {
        private const val CHAT_IMAGE_MAX_BYTES = (2.5 * 1024 * 1024).toLong()
        private const val PROFILE_VIDEO_MAX_BYTES = (3 * 1024 * 1024).toLong()
    }

    suspend fun connectReanime(email: String, password: String): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/me/connect/reanime") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(BffConnectReanimeRequest(email = email.trim(), password = password))
            }
            if (response.status.isSuccess()) {
                val body: BffConnectReanimeResponse = response.body()
                if (!body.projectRToken.isNullOrBlank()) {
                    sessionStore.save(stored.copy(token = body.projectRToken))
                }
                Result.success(body.user.toUserProfile())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to connect ReAnime account (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            println("[BffMeService] connectReanime error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun disconnectReanime(): Result<UserProfile> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/me/disconnect/reanime") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) {
                val body: BffMeResponse = response.body()
                sessionStore.save(stored.copy(token = "project_r_anisurge_offline"))
                Result.success(body.user.toUserProfile())
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to disconnect ReAnime account (${response.status.value})" }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            println("[BffMeService] disconnectReanime error: ${e.message}")
            Result.failure(e)
        }
    }
}

@Serializable
private data class BffPatchMeRequest(
    val username: String,
)

@Serializable
private data class BffPatchProfileDetailsRequest(
    val bio: String? = null,
    val website: String? = null,
)

@Serializable
private data class BffPfpJsonUploadRequest(
    val imageBase64: String,
    val mimeType: String,
    val fileName: String,
)

@Serializable
private data class BffPatchEquippedRequest(
    val equipped: JsonObject,
)

@Serializable
private data class BffPatchProfileExtraRequest(
    val profileExtra: JsonObject,
)

@Serializable
data class BffPremiumCheckoutSessionResponse(
    val id: String,
    val checkoutUrl: String,
    val expiresAt: String,
)
