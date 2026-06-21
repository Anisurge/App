package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.BasicApiResponse
import to.kuudere.anisuge.data.models.BffAuthResponse
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.BffMeResponse
import to.kuudere.anisuge.data.models.ForgotPasswordRequest
import to.kuudere.anisuge.data.models.LoginRequest
import to.kuudere.anisuge.data.models.RegisterRequest
import to.kuudere.anisuge.data.models.ResetPasswordRequest
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.models.SessionInfo
import to.kuudere.anisuge.data.models.toUserProfile
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class AuthService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
    private val integrationsSyncService: IntegrationsSyncService,
    private val librarySyncService: LibrarySyncService,
    private val notificationService: NotificationService,
) {
    private val _authState = MutableStateFlow<SessionCheckResult>(SessionCheckResult.Checking)
    val authState: StateFlow<SessionCheckResult> = _authState.asStateFlow()
    private val bffErrorJson = Json { ignoreUnknownKeys = true }
    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun login(identifier: String, password: String): SessionInfo =
        withAuthRetry { loginOnce(identifier, password) }

    suspend fun register(username: String, email: String, password: String): SessionInfo =
        withAuthRetry { registerOnce(username, email, password) }

    private suspend fun loginOnce(identifier: String, password: String): SessionInfo {
        val response = httpClient.post("${AnisurgeApi.v1Base}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier, password))
        }
        if (response.status != HttpStatusCode.OK) {
            val msg = response.bffErrorMessage("Login failed (${response.status})")
            throw Exception(msg.friendlyAuthFailure(response.status.value))
        }
        return handleAuthResponse(response.status, response.body())
    }

    private suspend fun registerOnce(username: String, email: String, password: String): SessionInfo {
        val response = httpClient.post("${AnisurgeApi.v1Base}/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, email, password))
        }
        val ok = response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created
        if (!ok) {
            val msg = response.bffErrorMessage("Registration failed (${response.status})")
                .friendlyAuthFailure(response.status.value)
            println("[AuthService] signup failed: ${response.status} — $msg")
            throw Exception(msg)
        }
        return handleAuthResponse(response.status, response.body())
    }

    private suspend fun <T> withAuthRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (isRetryableAuthError(e)) {
                block()
            } else {
                throw e
            }
        }
    }

    private fun isRetryableAuthError(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("timeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ||
                msg.contains("408", ignoreCase = false) ||
                msg.contains("504", ignoreCase = false)
    }

    private suspend fun HttpResponse.bffErrorMessage(fallback: String): String {
        val raw = bodyAsText()
        try {
            bffErrorJson.decodeFromString<BffErrorResponse>(raw).displayMessage()
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        } catch (_: Exception) {
        }
        return try {
            val root = bffErrorJson.parseToJsonElement(raw).jsonObject
            val err = root["error"] ?: return fallback
            when (err) {
                is JsonPrimitive -> err.contentOrNull?.takeIf { it.isNotBlank() } ?: fallback
                is JsonObject -> formatBffValidationError(err) ?: fallback
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun String.friendlyAuthFailure(httpStatus: Int): String {
        if (contains("failed (409)", ignoreCase = true) || contains("already", ignoreCase = true)) {
            return when {
                contains("email", ignoreCase = true) -> "That email is already registered — try logging in"
                contains("username", ignoreCase = true) -> "That username is taken — pick another"
                httpStatus == 409 -> "An account with this email or username already exists — try logging in"
                else -> this
            }
        }
        return this
    }

    private fun formatBffValidationError(err: JsonObject): String? {
        val fieldErrors = err["fieldErrors"]?.jsonObject ?: return null
        val parts = fieldErrors.mapNotNull { (field, messages) ->
            val msg = messages.jsonArray.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: messages.jsonPrimitive.contentOrNull
            msg?.let { "$field: $it" }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    suspend fun savePairedSession(session: SessionInfo) {
        if (!sessionStore.hasProjectRToken(session)) {
            throw Exception("Invalid paired session")
        }
        val upgraded = if (sessionStore.requiresBffRelogin(session)) {
            when (val synced = syncSession(session.token)) {
                is SessionCheckResult.Valid -> synced.session
                else -> throw Exception("Failed to sync TV session")
            }
        } else {
            session
        }
        sessionStore.save(upgraded)
        _authState.value = SessionCheckResult.Valid(upgraded)
    }

    suspend fun forgotPassword(identifier: String): String {
        val response = httpClient.post("${AnisurgeApi.v1Base}/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(identifier))
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception(response.bffErrorMessage("Could not send reset code"))
        }
        val body: BasicApiResponse = response.body()
        return body.message ?: "Reset code sent to your email"
    }

    suspend fun resetPassword(identifier: String, otp: String, newPassword: String): String {
        val response = httpClient.post("${AnisurgeApi.v1Base}/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(identifier, otp, newPassword))
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception(response.bffErrorMessage("Failed to reset password"))
        }
        val body: BasicApiResponse = response.body()
        return body.message ?: "Password reset successfully"
    }

    suspend fun checkSession(): SessionCheckResult {
        var stored = sessionStore.get()
        if (stored == null) {
            _authState.value = SessionCheckResult.NoSession
            return SessionCheckResult.NoSession
        }

        if (sessionStore.requiresBffRelogin(stored)) {
            println("[AuthService] legacy ReAnime-only session — clearing; user must log in again")
            sessionStore.clear()
            _authState.value = SessionCheckResult.Expired
            return SessionCheckResult.Expired
        }

        if (!sessionStore.isValid(stored)) {
            _authState.value = SessionCheckResult.Expired
            return SessionCheckResult.Expired
        }

        return fetchMe(stored)
    }

    suspend fun logout() {
        try {
            val stored = sessionStore.get()
            if (stored != null && !stored.anisurgeToken.isNullOrBlank()) {
                notificationService.unregisterCurrentDevice()
                httpClient.post("${AnisurgeApi.v1Base}/auth/logout") {
                    applyAnisurgeAuth(stored)
                }
            }
        } catch (e: Exception) {
            println("[AuthService] logout API call failed (proceeding with local clear): ${e.message}")
        } finally {
            sessionStore.clear()
            _authState.value = SessionCheckResult.NoSession
        }
    }

    private suspend fun syncSession(projectRToken: String): SessionCheckResult {
        val response = httpClient.post("${AnisurgeApi.v1Base}/auth/sync") {
            header("Authorization", "Bearer $projectRToken")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {})
        }
        if (response.status != HttpStatusCode.OK) {
            val err = try {
                response.body<BffErrorResponse>()
            } catch (_: Exception) {
                null
            }
            throw Exception(err?.displayMessage() ?: "Sync failed (${response.status})")
        }
        val body: BffAuthResponse = response.body()
        val session = sessionFromBff(body)
        sessionStore.save(session)
        val user = body.user?.toUserProfile()
        val result = SessionCheckResult.Valid(session, user)
        _authState.value = result
        authScope.launch { integrationsSyncService.restoreFromServer() }
        return result
    }

    private suspend fun fetchMe(stored: SessionInfo): SessionCheckResult {
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/me") {
                applyAnisurgeAuth(stored)
            }
            if (response.status == HttpStatusCode.OK) {
                val body: BffMeResponse = response.body()
                val result = SessionCheckResult.Valid(stored, body.user.toUserProfile())
                _authState.value = result
                authScope.launch { integrationsSyncService.restoreFromServer() }
                result
            } else if (response.status == HttpStatusCode.Unauthorized) {
                SessionCheckResult.Expired.also { _authState.value = it }
            } else {
                SessionCheckResult.NetworkError.also { _authState.value = it }
            }
        } catch (e: Exception) {
            println("[AuthService] checkSession error: ${e.message}")
            SessionCheckResult.NetworkError.also { _authState.value = it }
        }
    }

    private suspend fun handleAuthResponse(
        status: HttpStatusCode,
        body: BffAuthResponse,
    ): SessionInfo {
        if (status != HttpStatusCode.OK && status != HttpStatusCode.Created) {
            throw Exception("Authentication failed ($status)")
        }
        val session = sessionFromBff(body)
        sessionStore.save(session)
        val user = body.user?.toUserProfile()
        _authState.value = SessionCheckResult.Valid(session, user)
        authScope.launch {
            integrationsSyncService.restoreFromServer()
            if (status == HttpStatusCode.Created) {
                librarySyncService.syncWithReanime(force = true)
            }
        }
        return session
    }

    private fun sessionFromBff(body: BffAuthResponse): SessionInfo {
        if (body.projectRToken.isBlank() || body.anisurgeToken.isBlank()) {
            throw Exception("No token returned")
        }
        return SessionInfo(token = body.projectRToken, anisurgeToken = body.anisurgeToken)
    }
}
