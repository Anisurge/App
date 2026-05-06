package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.CurrentUserResponse
import to.kuudere.anisuge.data.models.NotificationCountResponse
import to.kuudere.anisuge.data.models.NotificationsResponse
import to.kuudere.anisuge.data.models.PasswordChangeRequest
import to.kuudere.anisuge.data.models.PasswordChangeResponse
import to.kuudere.anisuge.data.models.ProfileUpdateRequest
import to.kuudere.anisuge.data.models.ProfileUpdateResponse
import to.kuudere.anisuge.data.models.UserSettings
import to.kuudere.anisuge.data.models.UserSettingsResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SettingsService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getUserProfile(): CurrentUserResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${AppComponent.BASE_URL}/user") {
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
            val response = httpClient.patch("${AppComponent.BASE_URL}/user") {
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

    suspend fun getSettings(): UserSettingsResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${AppComponent.BASE_URL}/user/settings") {
                header("Authorization", "Bearer ${stored.token}")
            }
            response.body<UserSettingsResponse>()
        } catch (e: Exception) {
            println("[SettingsService] getSettings error: ${e.message}")
            null
        }
    }

    suspend fun updateSettings(settings: UserSettings): UserSettingsResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.patch("${AppComponent.BASE_URL}/user/settings") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(settings)
            }
            response.body<UserSettingsResponse>()
        } catch (e: Exception) {
            println("[SettingsService] updateSettings error: ${e.message}")
            null
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): PasswordChangeResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.post("${AppComponent.BASE_URL}/user/password") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(PasswordChangeRequest(currentPassword, newPassword))
            }
            response.body<PasswordChangeResponse>()
        } catch (e: Exception) {
            println("[SettingsService] changePassword error: ${e.message}")
            null
        }
    }

    suspend fun getNotificationCount(): NotificationCountResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${AppComponent.BASE_URL}/notifications/count") {
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
            val response = httpClient.get("${AppComponent.BASE_URL}/notifications/$type") {
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
            val response = httpClient.post("${AppComponent.BASE_URL}/notifications/mark-all-read") {
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
