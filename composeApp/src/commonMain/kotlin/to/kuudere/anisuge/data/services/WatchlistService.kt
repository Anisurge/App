package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.models.WatchlistResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

@Serializable
private data class WatchlistRequest(
    val animeId: String,
    val folder: String,
    val notes: String? = null,
)

class WatchlistService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getWatchlist(
        q: String? = null,
        folder: String? = null,
        format: String? = null,
        status: String? = null,
        season: String? = null,
        year: Int? = null,
        genre: String? = null,
        tag: String? = null,
        minScore: Int? = null,
        limit: Int = 20,
        offset: Int = 0,
        sort: String? = null,
    ): WatchlistResponse? {
        val stored = sessionStore.get()
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/watchlist") {
                q?.let { parameter("q", it) }
                folder?.let { parameter("folder", it) }
                format?.let { parameter("format", it) }
                status?.let { parameter("status", it) }
                season?.let { parameter("season", it) }
                year?.let { parameter("year", it) }
                genre?.let { parameter("genre", it) }
                tag?.let { parameter("tag", it) }
                minScore?.let { parameter("min_score", it) }
                parameter("limit", limit)
                parameter("offset", offset)
                sort?.let { parameter("sort", it) }
                if (stored != null) bearer(stored.token)
            }
            response.body<WatchlistResponse>()
        } catch (e: Exception) {
            println("[WatchlistService] getWatchlist error: ${e.message}")
            null
        }
    }

    suspend fun updateStatus(animeId: String, folder: String, notes: String? = null): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            if (folder.equals("Remove", ignoreCase = true)) {
                return removeFromWatchlist(animeId)
            }
            val response = httpClient.post("${ApiConfig.API_BASE}/watchlist") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(WatchlistRequest(animeId, folder.toPostFolder(), notes))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[WatchlistService] updateStatus error: ${e.message}")
            false
        }
    }

    suspend fun removeFromWatchlist(animeId: String): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val response = httpClient.delete("${ApiConfig.API_BASE}/watchlist/$animeId") {
                bearer(stored.token)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[WatchlistService] removeFromWatchlist error: ${e.message}")
            false
        }
    }

    /** Map display names to GET query param values (WATCHING, PLANNING, COMPLETED, PAUSED, DROPPED) */
    fun folderToGetParam(folder: String): String = when (folder.trim().uppercase()) {
        "WATCHING", "CURRENT" -> "WATCHING"
        "ON HOLD", "ON_HOLD", "PAUSED" -> "PAUSED"
        "PLAN TO WATCH", "PLAN_TO_WATCH", "PLANNING" -> "PLANNING"
        "COMPLETED" -> "COMPLETED"
        "DROPPED" -> "DROPPED"
        else -> folder.trim().uppercase()
    }

    /** Map display names to POST body values (CURRENT, PLANNING, COMPLETED, PAUSED, DROPPED) */
    private fun String.toPostFolder(): String = when (trim().uppercase()) {
        "WATCHING", "CURRENT" -> "CURRENT"
        "ON HOLD", "ON_HOLD", "PAUSED" -> "PAUSED"
        "PLAN TO WATCH", "PLAN_TO_WATCH", "PLANNING" -> "PLANNING"
        "COMPLETED" -> "COMPLETED"
        "DROPPED" -> "DROPPED"
        else -> trim().uppercase()
    }
}
