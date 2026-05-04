package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

@Serializable
data class OAuthApp(
    val clientId: String = "",
    val appName: String = "",
    val description: String? = null,
    val redirectUrls: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
    val createdAt: String? = null,
)

@Serializable
data class CreateOAuthAppRequest(
    val app_name: String,
    val description: String? = null,
    val redirect_urls: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
)

@Serializable
data class AuthorizeOAuthRequest(
    val client_id: String,
    val redirect_url: String,
    val scopes: List<String> = emptyList(),
)

@Serializable
data class OAuthSession(
    val clientId: String = "",
    val appName: String = "",
    val scopes: List<String> = emptyList(),
    val createdAt: String = "",
)

class OAuthService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun listApps(): List<OAuthApp>? {
        val stored = sessionStore.get() ?: return null
        return try {
            httpClient.get("${ApiConfig.API_BASE}/auth/oauth/apps") {
                bearer(stored.token)
            }.body()
        } catch (e: Exception) {
            println("[OAuthService] listApps error: ${e.message}")
            null
        }
    }

    suspend fun registerApp(request: CreateOAuthAppRequest): OAuthApp? {
        val stored = sessionStore.get() ?: return null
        return try {
            httpClient.post("${ApiConfig.API_BASE}/auth/oauth/apps") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            println("[OAuthService] registerApp error: ${e.message}")
            null
        }
    }

    suspend fun deleteApp(clientId: String): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.delete("${ApiConfig.API_BASE}/auth/oauth/apps/$clientId") {
                bearer(stored.token)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[OAuthService] deleteApp error: ${e.message}")
            false
        }
    }

    suspend fun authorize(request: AuthorizeOAuthRequest): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.post("${ApiConfig.API_BASE}/auth/oauth/authorize") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[OAuthService] authorize error: ${e.message}")
            false
        }
    }

    suspend fun revoke(token: String): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.post("${ApiConfig.API_BASE}/auth/oauth/revoke") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(mapOf("token" to token))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[OAuthService] revoke error: ${e.message}")
            false
        }
    }

    suspend fun listScopes(): List<String>? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/auth/oauth/scopes").body()
        } catch (e: Exception) {
            println("[OAuthService] listScopes error: ${e.message}")
            null
        }
    }

    suspend fun listSessions(): List<OAuthSession>? {
        val stored = sessionStore.get() ?: return null
        return try {
            httpClient.get("${ApiConfig.API_BASE}/auth/oauth/sessions") {
                bearer(stored.token)
            }.body()
        } catch (e: Exception) {
            println("[OAuthService] listSessions error: ${e.message}")
            null
        }
    }
}
