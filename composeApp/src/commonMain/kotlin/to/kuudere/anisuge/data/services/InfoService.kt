package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import to.kuudere.anisuge.data.models.AnimeThemesResponse
import to.kuudere.anisuge.data.models.WatchInfoResponse
import to.kuudere.anisuge.utils.currentTimeMillis

class InfoService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getAnimeThemes(anilistId: Int): AnimeThemesResponse? {
        if (anilistId <= 0) return null
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/anime-themes/$anilistId")
            if (response.status.value in 200..299) response.body<AnimeThemesResponse>() else null
        } catch (e: Exception) {
            println("[InfoService] getAnimeThemes error: ${e.message}")
            null
        }
    }
    private data class CachedVideoStream(
        val storedAtMs: Long,
        val response: BatchScrapeResponse,
    )

    private val videoStreamCache = mutableMapOf<String, CachedVideoStream>()
    private val videoStreamCacheLock = Mutex()
    private val videoStreamCacheTtlMs = 3 * 60 * 1000L
    private val flixIpCacheLock = Mutex()
    private var flixIpCache: Pair<String, Long>? = null
    private val flixIpCacheTtlMs = 10 * 60 * 1000L

    suspend fun getAnimeDetails(slug: String, includeEpisodes: Boolean = true): AnimeDetails? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/anime/$slug") {
                parameter("include_episodes", includeEpisodes)
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
        val flixIp = if (normalizedServer == "flix") resolveFlixClientIp() else null
        if (normalizedServer == "flix") {
            println("[InfoService] flix using clientIp=${flixIp ?: "(null)"} for anilistId=$anilistId ep=$episodeNumber (device public IP for token binding)")
        }
        val cacheKey = buildString {
            append(anilistId)
            append(':')
            append(episodeNumber)
            append(':')
            append(normalizedServer)
            if (!flixIp.isNullOrBlank()) {
                append(':')
                append(flixIp)
            }
        }
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
                flixIp?.let { parameter("ip", it) }
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

    private val ipServices = listOf(
        "https://api.ipify.org",
        "https://ifconfig.me/ip",
        "https://icanhazip.com",
        "https://checkip.amazonaws.com",
        "https://ipinfo.io/ip",
    )

    private suspend fun fetchIp(service: String): String? = try {
        val raw = httpClient.get(service).bodyAsText()
        val ip = raw.trim().trim('\r', '\n', ' ', '\t')
        val isUsable = ip.isNotBlank() &&
            ip != "127.0.0.1" && ip != "0.0.0.0" &&
            !ip.startsWith("127.") &&
            !ip.startsWith("192.168.") &&
            !ip.startsWith("10.") &&
            !ip.startsWith("172.16.") && !ip.startsWith("172.17.") && !ip.startsWith("172.18.") &&
            !ip.startsWith("172.19.") && !ip.startsWith("172.20.") && !ip.startsWith("172.21.") &&
            !ip.startsWith("172.22.") && !ip.startsWith("172.23.") && !ip.startsWith("172.24.") &&
            !ip.startsWith("172.25.") && !ip.startsWith("172.26.") && !ip.startsWith("172.27.") &&
            !ip.startsWith("172.28.") && !ip.startsWith("172.29.") && !ip.startsWith("172.30.") &&
            !ip.startsWith("172.31.")
        if (!isUsable && ip.isNotBlank()) {
            println("[InfoService] resolveFlixClientIp $service returned unusable: ${ip.take(32)}")
        }
        ip.takeIf { isUsable }
    } catch (e: Exception) {
        println("[InfoService] resolveFlixClientIp $service error: ${e.message}")
        null
    }

    private suspend fun resolveFlixClientIp(): String? {
        val now = currentTimeMillis()
        flixIpCacheLock.withLock {
            flixIpCache?.takeIf { now - it.second <= flixIpCacheTtlMs }?.first
        }?.let { return it }

        // First pass
        var ip = ipServices.firstNotNullOfOrNull { fetchIp(it) }

        // Second pass (some ip services can be flaky on mobile)
        if (ip.isNullOrBlank()) {
            kotlinx.coroutines.delay(250)
            ip = ipServices.firstNotNullOfOrNull { fetchIp(it) }
        }

        if (!ip.isNullOrBlank()) {
            flixIpCacheLock.withLock {
                flixIpCache = ip to currentTimeMillis()
            }
            println("[InfoService] resolveFlixClientIp success: ${ip.take(8)}... (len=${ip.length})")
        } else {
            println("[InfoService] resolveFlixClientIp FAILED — all services returned null or private IP")
        }
        return ip
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

    suspend fun prewarmStreamUrl(url: String) {
        try {
            httpClient.head(url)
        } catch (_: Exception) {}
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

    suspend fun reportMegaplayMissing(anilistId: Int, episodeNumber: Int): Boolean {
        if (anilistId <= 0 || episodeNumber <= 0) return false
        return try {
            val payload = buildJsonObject {
                put("id_type", "AniList")
                put("external_id", anilistId)
                put("episode", episodeNumber.toString())
                put("message", "Missing AniList title reported from my app")
            }
            val response = httpClient.post("https://megaplay.buzz/api/mapping-request") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            val ok = response.status.value in 200..299
            if (!ok) {
                val errBody = runCatching { response.body<String>() }.getOrNull()
                println("[InfoService] reportMegaplayMissing failed HTTP ${response.status.value}: $errBody")
            }
            ok
        } catch (e: Exception) {
            println("[InfoService] reportMegaplayMissing error anilist=$anilistId episode=$episodeNumber: ${e.message}")
            false
        }
    }
}
