package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import to.kuudere.anisuge.AppComponent
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.CurrentUserResponse
import to.kuudere.anisuge.data.models.NotificationCountResponse
import to.kuudere.anisuge.data.models.NotificationsResponse
import to.kuudere.anisuge.data.models.PasswordChangeRequest
import to.kuudere.anisuge.data.models.PasswordChangeResponse
import to.kuudere.anisuge.data.models.ProfileUpdateRequest
import to.kuudere.anisuge.data.models.ProfileUpdateResponse
import to.kuudere.anisuge.data.models.UserSettings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class SettingsService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getUserProfile(): CurrentUserResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/user") {
                header("Authorization", "Bearer ${stored.token}")
            }
            response.body<CurrentUserResponse>()
        } catch (e: Exception) {
            println("[SettingsService] getUserProfile error: ${e.message}")
            null
        }
    }

    suspend fun updateProfile(request: ProfileUpdateRequest): ProfileUpdateResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.patch("${AppComponent.PROJECT_R_BASE_URL}/user") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.body<ProfileUpdateResponse>()
        } catch (e: Exception) {
            println("[SettingsService] updateProfile error: ${e.message}")
            null
        }
    }

    /** API returns the settings object directly (not `{ "settings": { ... } }`). */
    suspend fun getSettings(): UserSettings? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/user/settings") {
                header("Authorization", "Bearer ${stored.token}")
            }
            response.body<UserSettings>()
        } catch (e: Exception) {
            println("[SettingsService] getSettings error: ${e.message}")
            null
        }
    }

    suspend fun updateSettings(settings: UserSettings): UserSettings? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.patch("${AppComponent.PROJECT_R_BASE_URL}/user/settings") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(settings)
            }
            response.body<UserSettings>()
        } catch (e: Exception) {
            println("[SettingsService] updateSettings error: ${e.message}")
            null
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): PasswordChangeResponse? {
        return try {
            val stored = sessionStore.get()
                ?: return PasswordChangeResponse(success = false, message = "Please sign in again")
            val response = httpClient.post("${AnisurgeApi.v1Base}/me/password") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(PasswordChangeRequest(currentPassword, newPassword))
            }
            if (response.status.isSuccess()) {
                response.body<PasswordChangeResponse>()
            } else {
                val message = runCatching {
                    json.decodeFromString<BffErrorResponse>(response.bodyAsText()).displayMessage()
                }.getOrElse { "Failed to change password (${response.status.value})" }
                PasswordChangeResponse(success = false, message = message)
            }
        } catch (e: Exception) {
            println("[SettingsService] changePassword error: ${e.message}")
            PasswordChangeResponse(success = false, message = e.message ?: "Failed to change password")
        }
    }

    suspend fun getNotificationCount(): NotificationCountResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/notifications/count") {
                header("Authorization", "Bearer ${stored.token}")
            }
            response.body()
        } catch (e: Exception) {
            println("[SettingsService] getNotificationCount error: ${e.message}")
            null
        }
    }

    suspend fun getNotifications(
        type: String,
        page: Int = 1,
        typeQuery: String? = null,
    ): NotificationsResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/notifications/$type") {
                header("Authorization", "Bearer ${stored.token}")
                parameter("page", page)
                typeQuery?.let { parameter("type", it) }
            }
            response.body()
        } catch (e: Exception) {
            println("[SettingsService] getNotifications error: ${e.message}")
            null
        }
    }

    suspend fun markAllNotificationsRead(type: String = ""): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val body = buildJsonObject {
                if (type.isNotBlank()) put("type", type)
            }
            val response = httpClient.post("${AppComponent.PROJECT_R_BASE_URL}/notifications/mark-all-read") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[SettingsService] markAllNotificationsRead error: ${e.message}")
            false
        }
    }
}
