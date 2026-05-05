package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.EpisodeDataResponse
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.models.EpisodesResponse
import to.kuudere.anisuge.data.models.FetchStreamResponse
import to.kuudere.anisuge.data.models.RecommendationItem
import to.kuudere.anisuge.data.models.RecommendationsResponse
import to.kuudere.anisuge.data.models.SenshiSourceData
import to.kuudere.anisuge.data.models.SourceData
import to.kuudere.anisuge.data.models.StreamingData
import to.kuudere.anisuge.data.models.WatchServerResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer
import io.ktor.http.encodeURLPathPart

class InfoService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    companion object {
        const val FETCH_BASE_URL = "https://fetch.anisurge.lol"
    }

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

    suspend fun getAllEpisodes(
        slug: String,
        filler: Boolean = true,
        recap: Boolean = true,
    ): List<EpisodeItem> {
        val allEpisodes = mutableListOf<EpisodeItem>()
        var offset = 0
        val pageSize = 100

        do {
            val response = getEpisodes(slug, limit = pageSize, offset = offset, filler = filler, recap = recap)
            if (response == null) break

            allEpisodes.addAll(response.episodeList)
            offset += response.episodeList.size

            if (response.episodeList.isEmpty()) break

            val hasMorePages = response.hasMore
                || (response.total > 0 && allEpisodes.size < response.total)
                || response.episodeList.size >= pageSize

            if (!hasMorePages) break
        } while (true)

        return allEpisodes
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

    suspend fun getVideoStream(anilistId: Int, episodeNumber: Int, server: String): WatchServerResponse? {
        return try {
            val response = httpClient.get("$FETCH_BASE_URL/api") {
                parameter("action", "batch_scrape")
                parameter("anilistId", anilistId)
                parameter("episode", episodeNumber)
                parameter("source", server)
            }

            // Try parsing as new FetchStreamResponse format first
            val fetchResponse = response.body<FetchStreamResponse>()
            mapFetchToWatchResponse(fetchResponse)
        } catch (e: Exception) {
            println("[InfoService] getVideoStream error: ${e.message}")
            null
        }
    }

    private fun mapFetchToWatchResponse(fetch: FetchStreamResponse): WatchServerResponse? {
        val section = fetch.sub?.takeIf { !it.notFound && it.streams.isNotEmpty() }
            ?: fetch.dub?.takeIf { !it.notFound && it.streams.isNotEmpty() }
            ?: return null

        val sources = section.streams.mapNotNull { item ->
            if (item.url != null && item.quality != null) {
                SourceData(quality = item.quality, url = item.url)
            } else null
        }

        // Use first stream's headers as the global headers for the streaming data
        val globalHeaders = section.streams.firstOrNull()?.headers

        val streamingData = StreamingData(
            sources = sources,
            headers = globalHeaders
        )

        return WatchServerResponse(
            success = true,
            data = streamingData
        )
    }

    suspend fun getSenshiSources(embedUrl: String): List<SourceData> {
        return try {
            if (!embedUrl.startsWith("https://senshi.live/episode-embeds/")) return emptyList()
            val response = httpClient.get(embedUrl) {
                header("Referer", "https://senshi.live")
            }
            response.body<List<SenshiSourceData>>().mapNotNull { source ->
                val url = source.url ?: return@mapNotNull null
                SourceData(quality = source.status ?: "Auto", url = url)
            }
        } catch (e: Exception) {
            println("[InfoService] getSenshiSources error: ${e.message}")
            emptyList()
        }
    }

    suspend fun prewarmStreamUrl(url: String) {
        try {
            httpClient.head(url)
        } catch (_: Exception) {
            // Ignore - this is best-effort only
        }
    }

    suspend fun fetchM3u8Content(m3u8Url: String, headers: Map<String, String>?): String? {
        return try {
            httpClient.get(m3u8Url) {
                headers?.forEach { (k, v) -> this.headers.append(k, v) }
            }.bodyAsText()
        } catch (e: Exception) {
            println("[InfoService] fetchM3u8Content error: ${e.message}")
            null
        }
    }
}
