package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import to.kuudere.anisuge.data.models.PasswordChangeRequest
import to.kuudere.anisuge.data.models.PasswordChangeResponse
import to.kuudere.anisuge.data.models.ProfileUpdateRequest
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.models.UserSettings
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

class SettingsService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun getUserProfile(): UserProfile? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${ApiConfig.API_BASE}/user") {
                bearer(stored.token)
            }
            response.body<JsonElement>().decodeUserProfile()
        } catch (e: Exception) {
            println("[SettingsService] getUserProfile error: ${e.message}")
            null
        }
    }

    suspend fun updateProfile(request: ProfileUpdateRequest): UserProfile? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.patch("${ApiConfig.API_BASE}/user") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.body<JsonElement>().decodeUserProfile()
        } catch (e: Exception) {
            println("[SettingsService] updateProfile error: ${e.message}")
            null
        }
    }

    suspend fun getSettings(): UserSettings? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.get("${ApiConfig.API_BASE}/user/settings") {
                bearer(stored.token)
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
            val response = httpClient.patch("${ApiConfig.API_BASE}/user/settings") {
                bearer(stored.token)
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
            val stored = sessionStore.get() ?: return null
            val response = httpClient.post("${ApiConfig.API_BASE}/user/password") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(PasswordChangeRequest(currentPassword, newPassword))
            }
            response.body<PasswordChangeResponse>()
        } catch (e: Exception) {
            println("[SettingsService] changePassword error: ${e.message}")
            null
        }
    }

    private fun JsonElement.decodeUserProfile(): UserProfile {
        val obj = this as? JsonObject
        val nested = obj?.get("user")
            ?: obj?.get("data")
            ?: obj?.get("profile")
            ?: this
        return json.decodeFromJsonElement(nested)
    }
}
