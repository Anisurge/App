package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import io.ktor.http.encodeURLPathPart
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.EpisodeDataResponse
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.models.EpisodeLink
import to.kuudere.anisuge.data.models.EpisodesResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

class InfoService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun getAnimeDetails(slug: String, includeEpisodes: Boolean = false, tz: String? = null): AnimeDetails? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${ApiConfig.API_BASE}/anime/${slug.encodeURLPathPart()}") {
                parameter("include_episodes", includeEpisodes)
                if (tz != null) parameter("tz", tz)
                if (stored != null) bearer(stored.token)
            }
            response.body<JsonElement>().decodePayload()
        } catch (e: Exception) {
            println("[InfoService] getAnimeDetails error for slug='$slug': ${e.message}")
            null
        }
    }

    private inline fun <reified T> JsonElement.decodePayload(): T {
        val obj = this as? JsonObject
        val nested = obj?.get("data") ?: obj?.get("anime") ?: obj?.get("result") ?: this
        return json.decodeFromJsonElement(nested)
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

    suspend fun getRecommendations(slug: String): List<AnimeItem>? {
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/anime/${slug.encodeURLPathPart()}/recommendations")
            response.body()
        } catch (e: Exception) {
            println("[InfoService] getRecommendations error: ${e.message}")
            null
        }
    }

    // TODO: Streaming endpoints are not yet available in the Project-R API.
    //  These placeholders return null/empty so the app compiles. Implement
    //  once the backend adds /watch/* streaming routes.

    suspend fun getVideoStream(anilistId: Int, episodeNum: Int, server: String): EpisodeDataResponse? {
        println("[InfoService] TODO: getVideoStream not yet implemented in Project-R API")
        return null
    }

    suspend fun getSenshiSources(fileId: String): List<EpisodeLink> {
        println("[InfoService] TODO: getSenshiSources not yet implemented in Project-R API")
        return emptyList()
    }

    suspend fun prewarmStreamUrl(streamUrl: String) {
        // no-op placeholder until streaming is available
    }
}
