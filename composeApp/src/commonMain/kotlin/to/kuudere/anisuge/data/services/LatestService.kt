package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.LatestAiredResponse
import to.kuudere.anisuge.data.models.SearchResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

class LatestService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getLatestUpdates(
        lang: String? = null,
        limit: Int = 12,
        cursor: String? = null,
    ): LatestAiredResponse? {
        val stored = sessionStore.get()
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/home/latest-aired") {
                lang?.let { parameter("lang", it) }
                parameter("limit", limit)
                cursor?.let { parameter("cursor", it) }
                if (stored != null) bearer(stored.token)
            }
            response.body<LatestAiredResponse>()
        } catch (e: Exception) {
            println("[LatestService] getLatestUpdates error: ${e.message}")
            null
        }
    }
}
