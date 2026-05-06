package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.AuthResponse
import to.kuudere.anisuge.data.models.BasicApiResponse
import to.kuudere.anisuge.data.models.CurrentUserResponse
import to.kuudere.anisuge.data.models.ForgotPasswordRequest
import to.kuudere.anisuge.data.models.LoginRequest
import to.kuudere.anisuge.data.models.RegisterRequest
import to.kuudere.anisuge.data.models.ResetPasswordRequest
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.models.SessionInfo

class AuthService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val _authState = MutableStateFlow<SessionCheckResult>(SessionCheckResult.NoSession)
    val authState: StateFlow<SessionCheckResult> = _authState.asStateFlow()

    private suspend fun authHeader(): String? = sessionStore.get()?.token

    suspend fun login(identifier: String, password: String): SessionInfo {
        val response = httpClient.post("${AppComponent.BASE_URL}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier, password))
        }
        if (response.status != HttpStatusCode.OK) {
            val errorBody = try { response.body<AuthResponse>() } catch (e: Exception) { null }
            throw Exception(errorBody?.message ?: "Login failed (${response.status})")
        }
        val body: AuthResponse = response.body()
        val token = body.token
            ?: throw Exception(body.message ?: "No token returned")
        val session = SessionInfo(token).also { sessionStore.save(it) }
        _authState.value = SessionCheckResult.Valid(session, body.user)
        return session
    }

    suspend fun register(username: String, email: String, password: String): SessionInfo {
        val response = httpClient.post("${AppComponent.BASE_URL}/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, email, password))
        }
        if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
            val errorBody = try { response.body<AuthResponse>() } catch (e: Exception) { null }
            throw Exception(errorBody?.message ?: "Registration failed (${response.status})")
        }
        val body: AuthResponse = response.body()
        val token = body.token
            ?: throw Exception(body.message ?: "No token returned")
        val session = SessionInfo(token).also { sessionStore.save(it) }
        _authState.value = SessionCheckResult.Valid(session, body.user)
        return session
    }

    suspend fun savePairedSession(session: SessionInfo) {
        if (!sessionStore.isValid(session)) {
            throw Exception("Invalid paired session")
        }
        sessionStore.save(session)
        _authState.value = SessionCheckResult.Valid(session)
    }

    suspend fun forgotPassword(identifier: String): String {
        val response = httpClient.post("${AppComponent.BASE_URL}/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(identifier))
        }
        val body: BasicApiResponse = response.body()
        return body.message ?: "Reset code sent successfully"
    }

    suspend fun resetPassword(identifier: String, otp: String, newPassword: String): String {
        val response = httpClient.post("${AppComponent.BASE_URL}/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(identifier, otp, newPassword))
        }
        val body: BasicApiResponse = response.body()
        if (response.status != HttpStatusCode.OK) {
            throw Exception(body.message ?: "Failed to reset password")
        }
        return body.message ?: "Password reset successfully"
    }

    suspend fun checkSession(): SessionCheckResult {
        val stored = sessionStore.get()
        if (stored == null) {
            _authState.value = SessionCheckResult.NoSession
            return SessionCheckResult.NoSession
        }
        if (!sessionStore.isValid(stored)) {
            _authState.value = SessionCheckResult.Expired
            return SessionCheckResult.Expired
        }

        return try {
            val response = httpClient.get("${AppComponent.BASE_URL}/user") {
                header("Authorization", "Bearer ${stored.token}")
            }
            if (response.status == HttpStatusCode.OK) {
                val body: CurrentUserResponse = response.body()
                SessionCheckResult.Valid(stored, body.user).also { _authState.value = it }
            } else if (response.status == HttpStatusCode.Unauthorized) {
                SessionCheckResult.Expired.also { _authState.value = it }
            } else {
                SessionCheckResult.NetworkError.also { _authState.value = it }
            }
        } catch (e: Exception) {
            SessionCheckResult.NetworkError.also { _authState.value = it }
        }
    }

    suspend fun logout() {
        try {
            val stored = sessionStore.get()
            if (stored != null) {
                httpClient.post("${AppComponent.BASE_URL}/auth/logout") {
                    header("Authorization", "Bearer ${stored.token}")
                }
            }
        } catch (e: Exception) {
            println("[AuthService] logout API call failed (proceeding with local clear): ${e.message}")
        } finally {
            sessionStore.clear()
            _authState.value = SessionCheckResult.NoSession
        }
    }
}
