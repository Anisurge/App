package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.LatestAiredResponse

class LatestService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getLatestAired(
        lang: String? = null,
        limit: Int = 12,
        cursor: String? = null,
    ): LatestAiredResponse? {
        return try {
            val response = httpClient.get("${AppComponent.BASE_URL}/home/latest-aired") {
                lang?.let { parameter("lang", it) }
                parameter("limit", limit)
                cursor?.let { parameter("cursor", it) }
            }
            response.body<LatestAiredResponse>()
        } catch (e: Exception) {
            println("[LatestService] getLatestAired error: ${e.message}")
            null
        }
    }
}
