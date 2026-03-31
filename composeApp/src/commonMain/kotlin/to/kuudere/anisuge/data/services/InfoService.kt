package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import to.kuudere.anisuge.data.models.AnimeDetailsResponse
import to.kuudere.anisuge.data.models.EpisodeDataResponse
import to.kuudere.anisuge.data.models.SessionInfo
import to.kuudere.anisuge.data.models.ThumbnailsResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.TextContent

class InfoService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    companion object {
        const val BASE_URL = "https://anime.anisurge.qzz.io"
    }

    private fun sessionToCookie(s: SessionInfo): String =
        "session_id=${s.sessionId}; session_secret=${s.session}; user_id=${s.userId}"

    suspend fun getAnimeDetails(id: String): AnimeDetailsResponse? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("$BASE_URL/anime/$id") {
                parameter("type", "api")
                if (stored != null) header("Cookie", sessionToCookie(stored))
            }
            response.body<AnimeDetailsResponse>()
        } catch (e: Exception) {
            println("[InfoService] fetchAnimeDetails error: ${e.message}")
            null
        }
    }

    suspend fun getEpisodes(animeId: String, episodeNumber: Int = 1): EpisodeDataResponse? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("$BASE_URL/api/watch/$animeId/$episodeNumber") {
                parameter("nolinks", "true")
                if (stored != null) header("Cookie", sessionToCookie(stored))
            }
            response.body<EpisodeDataResponse>()
        } catch (e: Exception) {
            println("[InfoService] getEpisodes error: ${e.message}")
            null
        }
    }

    suspend fun getThumbnails(anilistId: Int): ThumbnailsResponse? {
        return try {
            val response = httpClient.get("$BASE_URL/api/thumbnails/$anilistId")
            response.body<ThumbnailsResponse>()
        } catch (e: Exception) {
            println("[InfoService] getThumbnails error: ${e.message}")
            null
        }
    }

    suspend fun updateWatchlistStatus(animeId: String, folder: String): to.kuudere.anisuge.data.models.WatchlistUpdateResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val payload = buildJsonObject {
                put("animeId", animeId)
                put("folder", folder)
            }
            val response = httpClient.post("$BASE_URL/api/anime/watchlist") {
                header("Cookie", sessionToCookie(stored))
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (response.status.value in 200..299) {
                response.body<to.kuudere.anisuge.data.models.WatchlistUpdateResponse>()
            } else null
        } catch (e: Exception) {
            println("[InfoService] updateWatchlistStatus error: ${e.message}")
            null
        }
    }

    suspend fun getVideoStream(anilistId: Int, episodeNumber: Int, server: String): to.kuudere.anisuge.data.models.WatchServerResponse? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("$BASE_URL/$anilistId/$episodeNumber/$server") {
                if (stored != null) header("Cookie", sessionToCookie(stored))
            }
            response.body<to.kuudere.anisuge.data.models.WatchServerResponse>()
        } catch (e: Exception) {
            println("[InfoService] getVideoStream error: ${e.message}")
            null
        }
    }

    /** Fire-and-forget HEAD request to warm the DNS + TCP/TLS path for a CDN stream URL.
     *  OkHttp on Android shares the system netd DNS cache with MPV's libcurl, so this
     *  pre-resolve saves ~50-200ms off the first MPV connection to the CDN. */
    suspend fun prewarmStreamUrl(url: String) {
        try {
            httpClient.head(url)
        } catch (_: Exception) {
            // Ignore - this is best-effort only
        }
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
            println("[InfoService] Saving progress for $animeId / $episodeId => $currentTime / $duration")
            val response = httpClient.post("$BASE_URL/api/save-progress") {
                header("Cookie", sessionToCookie(stored))
                setBody(TextContent(payload.toString(), ContentType.Application.Json))
            }
            println("[InfoService] Save-progress responded with: ${response.status} - ${response.bodyAsText()}")
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[InfoService] saveProgress error: ${e.message}")
            false
        }
    }
}
