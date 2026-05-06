package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.BatchScrapeResponse
import to.kuudere.anisuge.data.models.EpisodeListResponse
import to.kuudere.anisuge.data.models.RecommendationsResponse
import to.kuudere.anisuge.data.models.WatchInfoResponse

class InfoService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getAnimeDetails(slug: String): AnimeDetails? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${AppComponent.BASE_URL}/anime/$slug") {
                parameter("include_episodes", "true")
                if (stored != null) header("Authorization", "Bearer ${stored.token}")
            }
            response.body<AnimeDetails>()
        } catch (e: Exception) {
            System.err.println("[InfoService] getAnimeDetails error for slug=$slug: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace(System.err)
            null
        }
    }

    suspend fun getEpisodes(
        slug: String,
        limit: Int = 30,
        offset: Int = 0,
        filler: Boolean = true,
        recap: Boolean = true,
    ): EpisodeListResponse? {
        return try {
            val response = httpClient.get("${AppComponent.BASE_URL}/anime/$slug/episodes") {
                parameter("limit", limit)
                if (offset > 0) parameter("offset", offset)
                parameter("filler", filler)
                parameter("recap", recap)
            }
            response.body<EpisodeListResponse>()
        } catch (e: Exception) {
            println("[InfoService] getEpisodes error: ${e.message}")
            null
        }
    }

    suspend fun getWatchInfo(
        slug: String,
        ep: String? = null,
        nid: String? = null,
        tz: String? = null,
    ): WatchInfoResponse? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${AppComponent.BASE_URL}/watch/$slug") {
                if (stored != null) header("Authorization", "Bearer ${stored.token}")
                ep?.let { parameter("ep", it) }
                nid?.let { parameter("nid", it) }
                tz?.let { parameter("tz", it) }
            }
            response.body<WatchInfoResponse>()
        } catch (e: Exception) {
            println("[InfoService] getWatchInfo error: ${e.message}")
            null
        }
    }

    suspend fun getRecommendations(slug: String): RecommendationsResponse? {
        return try {
            val response = httpClient.get("${AppComponent.BASE_URL}/anime/$slug/recommendations")
            response.body<RecommendationsResponse>()
        } catch (e: Exception) {
            println("[InfoService] getRecommendations error: ${e.message}")
            null
        }
    }

    suspend fun getVideoStream(
        anilistId: Int,
        episodeNumber: Int,
        server: String
    ): BatchScrapeResponse? {
        return try {
            val response = httpClient.get(AppComponent.STREAMING_URL) {
                parameter("action", "batch_scrape")
                parameter("anilistId", anilistId)
                parameter("episode", episodeNumber)
                parameter("source", server)
            }
            response.body<BatchScrapeResponse>()
        } catch (e: Exception) {
            println("[InfoService] getVideoStream error: ${e.message}")
            null
        }
    }

    suspend fun prewarmStreamUrl(url: String) {
        try {
            httpClient.head(url)
        } catch (_: Exception) {}
    }

    suspend fun saveProgress(
        animeId: String,
        episodeId: String,
        currentTime: Double,
        duration: Double,
        server: String,
        language: String = "sub"
    ): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val payload = buildJsonObject {
                put("animeId", animeId)
                put("episodeId", episodeId)
                put("currentTime", currentTime)
                put("duration", duration)
                put("server", server)
                put("language", language)
            }
            val response = httpClient.post("${AppComponent.BASE_URL}/watch/progress") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[InfoService] saveProgress error: ${e.message}")
            false
        }
    }
}
