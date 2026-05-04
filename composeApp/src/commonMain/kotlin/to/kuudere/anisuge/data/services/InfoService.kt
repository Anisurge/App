package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.EpisodeDataResponse
import to.kuudere.anisuge.data.models.EpisodesResponse
import to.kuudere.anisuge.data.models.RecommendationItem
import to.kuudere.anisuge.data.models.RecommendationsResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer
import io.ktor.http.encodeURLPathPart

class InfoService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getAnimeDetails(slug: String, includeEpisodes: Boolean = false, tz: String? = null): AnimeDetails? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${ApiConfig.API_BASE}/anime/${slug.encodeURLPathPart()}") {
                parameter("include_episodes", includeEpisodes)
                if (tz != null) parameter("tz", tz)
                if (stored != null) bearer(stored.token)
            }
            response.body<AnimeDetails>()
        } catch (e: Exception) {
            println("[InfoService] getAnimeDetails error for slug='$slug': ${e.message}")
            null
        }
    }

    suspend fun getEpisodes(
        slug: String,
        limit: Int = 30,
        offset: Int = 0,
        filler: Boolean = true,
        recap: Boolean = true,
    ): EpisodesResponse? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${ApiConfig.API_BASE}/anime/${slug.encodeURLPathPart()}/episodes") {
                parameter("limit", limit)
                parameter("offset", offset)
                parameter("filler", filler)
                parameter("recap", recap)
                if (stored != null) bearer(stored.token)
            }
            response.body<EpisodesResponse>()
        } catch (e: Exception) {
            println("[InfoService] getEpisodes error for slug='$slug': ${e.message}")
            null
        }
    }

    suspend fun getRecommendations(slug: String): List<RecommendationItem>? {
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/anime/${slug.encodeURLPathPart()}/recommendations")
            val result = response.body<RecommendationsResponse>()
            result.recommendations
        } catch (e: Exception) {
            println("[InfoService] getRecommendations error: ${e.message}")
            null
        }
    }

    suspend fun getVideoStream(anilistId: Int, episodeNum: Int, server: String): EpisodeDataResponse? {
        println("[InfoService] TODO: getVideoStream not yet implemented in Project-R API")
        return null
    }

    suspend fun getSenshiSources(fileId: String): List<to.kuudere.anisuge.data.models.EpisodeLink> {
        println("[InfoService] TODO: getSenshiSources not yet implemented in Project-R API")
        return emptyList()
    }

    suspend fun prewarmStreamUrl(streamUrl: String) {
        // no-op placeholder until streaming is available
    }
}
