package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.network.ApiConfig

class TopAnimeService(
    private val httpClient: HttpClient,
) {
    suspend fun getTopAnime(period: String = "today", limit: Int = 10): List<AnimeItem>? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/top/anime") {
                parameter("period", period)
                parameter("limit", limit)
            }.body()
        } catch (e: Exception) {
            println("[TopAnimeService] getTopAnime error: ${e.message}")
            null
        }
    }
}
