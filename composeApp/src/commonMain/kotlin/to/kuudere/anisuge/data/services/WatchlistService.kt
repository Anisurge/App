package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.WatchlistResponse
import to.kuudere.anisuge.data.models.WatchlistUpdateResponse

class WatchlistService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getWatchlist(
        limit: Int = 20,
        offset: Int = 0,
        folder: String? = null,
        q: String? = null,
        sort: String? = null,
        format: String? = null,
        status: String? = null,
        season: String? = null,
        year: Int? = null,
        genre: String? = null,
        tag: String? = null,
        minScore: Int? = null,
    ): WatchlistResponse? {
        val stored = sessionStore.get() ?: return null
        val response = httpClient.get("${AppComponent.BASE_URL}/watchlist") {
            header("Authorization", "Bearer ${stored.token}")
            parameter("limit", limit)
            if (offset > 0) parameter("offset", offset)
            folder?.let { parameter("folder", it) }
            q?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
            sort?.let { parameter("sort", it) }
            format?.let { parameter("format", it) }
            status?.let { parameter("status", it) }
            season?.let { parameter("season", it) }
            year?.let { parameter("year", it) }
            genre?.let { parameter("genre", it) }
            tag?.let { parameter("tag", it) }
            minScore?.let { parameter("min_score", it) }
        }
        return response.body<WatchlistResponse>()
    }

    @Serializable
    private data class UpdateWatchlistRequest(
        val animeId: String,
        val folder: String,
        val notes: String? = null,
    )

    suspend fun updateStatus(animeId: String, folder: String, notes: String? = null): WatchlistUpdateResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val response = httpClient.post("${AppComponent.BASE_URL}/watchlist") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(UpdateWatchlistRequest(animeId, folder, notes))
            }
            if (response.status.value in 200..299) {
                response.body<WatchlistUpdateResponse>()
            } else null
        } catch (e: Exception) {
            println("[WatchlistService] updateStatus error: ${e.message}")
            null
        }
    }

    suspend fun removeFromWatchlist(animeId: String): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val response = httpClient.delete("${AppComponent.BASE_URL}/watchlist/$animeId") {
                header("Authorization", "Bearer ${stored.token}")
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[WatchlistService] removeFromWatchlist error: ${e.message}")
            false
        }
    }
}
