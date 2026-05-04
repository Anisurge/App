package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer
import kotlinx.serialization.Serializable

@Serializable
data class RequestEntry(
    val animeId: String = "",
    val userId: String = "",
    val status: String = "",
    val createdAt: String = "",
    val solvedAt: String? = null,
)

class RequestService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getUserRequests(): List<RequestEntry>? {
        val stored = sessionStore.get() ?: return null
        return try {
            httpClient.get("${ApiConfig.API_BASE}/requests") {
                bearer(stored.token)
            }.body()
        } catch (e: Exception) {
            println("[RequestService] getUserRequests error: ${e.message}")
            null
        }
    }

    suspend fun addRequest(animeId: String): RequestEntry? {
        val stored = sessionStore.get() ?: return null
        return try {
            httpClient.post("${ApiConfig.API_BASE}/requests/$animeId") {
                bearer(stored.token)
            }.body()
        } catch (e: Exception) {
            println("[RequestService] addRequest error: ${e.message}")
            null
        }
    }

    suspend fun removeRequest(animeId: String): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.delete("${ApiConfig.API_BASE}/requests/$animeId") {
                bearer(stored.token)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[RequestService] removeRequest error: ${e.message}")
            false
        }
    }
}
