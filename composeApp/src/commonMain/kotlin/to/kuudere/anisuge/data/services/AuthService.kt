package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import to.kuudere.anisuge.data.models.AuthResponse
import to.kuudere.anisuge.data.models.BasicApiResponse
import to.kuudere.anisuge.data.models.ForgotPasswordRequest
import to.kuudere.anisuge.data.models.LoginRequest
import to.kuudere.anisuge.data.models.ResetPasswordRequest
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.models.SessionInfo
import to.kuudere.anisuge.data.models.SignupRequest
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

class AuthService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val _authState = MutableStateFlow<SessionCheckResult>(SessionCheckResult.NoSession)
    val authState: StateFlow<SessionCheckResult> = _authState.asStateFlow()

    suspend fun login(identifier: String, password: String): SessionInfo {
        val response = httpClient.post("${ApiConfig.API_BASE}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier, password))
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val errorBody = try { response.body<AuthResponse>() } catch (e: Exception) { null }
            throw Exception(errorBody?.error ?: errorBody?.message ?: "Login failed (${response.status})")
        }
        val body: AuthResponse = response.body()
        if (body.error != null)
            throw Exception(body.error)
        if (body.token == null)
            throw Exception(body.message ?: "Login failed - no token returned")
        val profile = body.user
        val session = SessionInfo(
            token = body.token,
            userId = profile?.effectiveId ?: "",
            username = profile?.username ?: "",
        ).also { sessionStore.save(it) }
        _authState.value = SessionCheckResult.Valid(session, body.user)
        return session
    }

    suspend fun signup(email: String, password: String, username: String): SessionInfo {
        val response = httpClient.post("${ApiConfig.API_BASE}/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(username, email, password))
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val errorBody = try { response.body<AuthResponse>() } catch (e: Exception) { null }
            throw Exception(errorBody?.error ?: errorBody?.message ?: "Registration failed (${response.status})")
        }
        val body: AuthResponse = response.body()
        if (body.error != null)
            throw Exception(body.error)
        if (body.token == null)
            throw Exception(body.message ?: "Registration failed - no token returned")
        val profile = body.user
        val session = SessionInfo(
            token = body.token,
            userId = profile?.effectiveId ?: "",
            username = profile?.username ?: "",
        ).also { sessionStore.save(it) }
        _authState.value = SessionCheckResult.Valid(session, body.user)
        return session
    }

    suspend fun forgotPassword(identifier: String): String {
        val response = httpClient.post("${ApiConfig.API_BASE}/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(identifier))
        }
        val body: BasicApiResponse = response.body()
        if (body.error != null) throw Exception(body.error)
        if (!body.success && body.message == null) throw Exception("Failed to send reset code")
        return body.message ?: "Reset code sent successfully"
    }

    suspend fun resetPassword(email: String, otp: String, password: String): String {
        val response = httpClient.post("${ApiConfig.API_BASE}/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(email, otp, password))
        }
        val body: BasicApiResponse = response.body()
        if (body.error != null) throw Exception(body.error)
        if (!body.success && body.message == null) throw Exception("Failed to reset password")
        return body.message ?: "Password reset successfully"
    }

    suspend fun checkSession(): SessionCheckResult {
        val stored = sessionStore.get()
        if (stored == null) {
            _authState.value = SessionCheckResult.NoSession
            return SessionCheckResult.NoSession
        }

        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/user") {
                bearer(stored.token)
            }
            if (response.status == HttpStatusCode.OK) {
                val profile: UserProfile = response.body<JsonElement>().decodeUserProfile()
                val updatedSession = stored.copy(
                    userId = profile.effectiveId ?: stored.userId,
                    username = profile.username ?: stored.username,
                )
                SessionCheckResult.Valid(updatedSession, profile).also { _authState.value = it }
            } else if (response.status == HttpStatusCode.Unauthorized) {
                SessionCheckResult.Expired.also { _authState.value = it }
            } else {
                SessionCheckResult.Expired.also { _authState.value = it }
            }
        } catch (e: Exception) {
            SessionCheckResult.NetworkError.also { _authState.value = it }
        }
    }

    suspend fun logout() {
        try {
            val stored = sessionStore.get()
            if (stored != null) {
                httpClient.post("${ApiConfig.API_BASE}/auth/logout") {
                    bearer(stored.token)
                }
            }
        } catch (e: Exception) {
            println("[AuthService] logout API call failed (proceeding with local clear): ${e.message}")
        } finally {
            sessionStore.clear()
            _authState.value = SessionCheckResult.NoSession
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
