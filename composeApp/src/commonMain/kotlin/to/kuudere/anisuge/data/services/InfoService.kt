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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.BatchScrapeResponse
import to.kuudere.anisuge.data.models.EpisodeListResponse
import to.kuudere.anisuge.data.models.RecommendationsResponse
import to.kuudere.anisuge.data.models.SuzuEmbedStream
import to.kuudere.anisuge.data.models.BffWatchProgressResponse
import to.kuudere.anisuge.data.models.WatchInfoResponse
import to.kuudere.anisuge.utils.currentTimeMillis

class InfoService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private data class CachedVideoStream(
        val storedAtMs: Long,
        val response: BatchScrapeResponse,
    )

    private val videoStreamCache = mutableMapOf<String, CachedVideoStream>()
    private val videoStreamCacheLock = Mutex()
    private val videoStreamCacheTtlMs = 3 * 60 * 1000L

    suspend fun getAnimeDetails(slug: String): AnimeDetails? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/anime/$slug") {
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
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/anime/$slug/episodes") {
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
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/watch/$slug") {
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
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/anime/$slug/recommendations")
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
        val normalizedServer = server.lowercase()
        val cacheKey = "$anilistId:$episodeNumber:$normalizedServer"
        val now = currentTimeMillis()
        videoStreamCacheLock.withLock {
            videoStreamCache[cacheKey]
                ?.takeIf { now - it.storedAtMs <= videoStreamCacheTtlMs }
                ?.response
        }?.let { return it }

        return try {
            val response = httpClient.get(AppComponent.STREAMING_URL) {
                parameter("action", "batch_scrape")
                parameter("anilistId", anilistId)
                parameter("episode", episodeNumber)
                parameter("source", server)
            }
            response.body<BatchScrapeResponse>().also { body ->
                videoStreamCacheLock.withLock {
                    videoStreamCache[cacheKey] = CachedVideoStream(currentTimeMillis(), body)
                    if (videoStreamCache.size > 40) {
                        videoStreamCache.minByOrNull { it.value.storedAtMs }?.key?.let(videoStreamCache::remove)
                    }
                }
            }
        } catch (e: Exception) {
            println("[InfoService] getVideoStream error: ${e.message}")
            null
        }
    }

    suspend fun fetchSuzuEmbedStreams(embedUrl: String): List<SuzuEmbedStream>? {
        return try {
            val response = httpClient.get(embedUrl)
            response.body<List<SuzuEmbedStream>>()
        } catch (e: Exception) {
            println("[InfoService] fetchSuzuEmbedStreams error: ${e.message}")
            null
        }
    }

    suspend fun prewarmStreamUrl(url: String, headers: Map<String, String>? = null) {
        try {
            httpClient.head(url) {
                headers.orEmpty().forEach { (name, value) -> header(name, value) }
            }
        } catch (_: Exception) {
            try {
                httpClient.get(url) {
                    headers.orEmpty().forEach { (name, value) -> header(name, value) }
                    header("Range", "bytes=0-0")
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun saveProgress(
        animeId: String,
        anilistId: Int? = null,
        malId: Int? = null,
        episodeId: String,
        currentTime: Double,
        duration: Double,
        server: String,
        language: String = "sub",
    ): BffWatchProgressResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val payload = buildJsonObject {
                put("animeId", animeId)
                anilistId?.takeIf { it > 0 }?.let { put("anilistId", it) }
                malId?.takeIf { it > 0 }?.let { put("malId", it) }
                put("episodeId", episodeId)
                put("currentTime", currentTime)
                put("duration", duration)
                put("server", server)
                put("language", language)
            }
            val response = httpClient.post("${AnisurgeApi.v1Base}/watch/progress") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (response.status.value in 200..299) {
                response.body<BffWatchProgressResponse>()
            } else {
                val errBody = runCatching { response.body<String>() }.getOrNull()
                println(
                    "[InfoService] saveProgress failed HTTP ${response.status.value} " +
                        "anime=$animeId $episodeId: $errBody",
                )
                null
            }
        } catch (e: Exception) {
            println("[InfoService] saveProgress error anime=$animeId $episodeId: ${e.message}")
            null
        }
    }
}
