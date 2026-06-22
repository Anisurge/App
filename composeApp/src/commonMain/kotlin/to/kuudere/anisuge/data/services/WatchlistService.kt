package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth
import to.kuudere.anisuge.data.models.WatchlistResponse
import to.kuudere.anisuge.data.models.WatchlistUpdateResponse

class WatchlistService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
    private val autoTrackingSyncService: AutoTrackingSyncService? = null,
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
        val response = httpClient.get("${AnisurgeApi.v1Base}/watchlist") {
            applyAnisurgeAuth(stored)
            parameter("limit", limit)
            if (offset > 0) parameter("offset", offset)
            folder?.let { parameter("folder", it.toProjectRFolder()) }
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
        if (!response.status.isSuccess()) {
            error("Watchlist request failed (${response.status.value})")
        }
        return response.body<WatchlistResponse>()
    }

    @Serializable
    private data class UpdateWatchlistRequest(
        val animeId: String,
        val folder: String,
        val notes: String? = null,
        val anilistId: Int? = null,
        val malId: Int? = null,
    )

    suspend fun updateStatus(
        animeId: String,
        folder: String,
        notes: String? = null,
        anilistId: Int? = null,
        malId: Int? = null,
        suppressTrackerSync: Boolean = false,
    ): WatchlistUpdateResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val normalizedFolder = folder.toProjectRFolder()
            if (normalizedFolder == "REMOVE") {
                return if (removeFromWatchlist(
                        animeId,
                        anilistId,
                        malId,
                        suppressTrackerSync,
                    )
                ) WatchlistUpdateResponse(success = true) else null
            }
            val response = httpClient.post("${AnisurgeApi.v1Base}/watchlist") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(
                    UpdateWatchlistRequest(
                        animeId = animeId,
                        folder = normalizedFolder,
                        notes = notes,
                        anilistId = anilistId?.takeIf { it > 0 },
                        malId = malId?.takeIf { it > 0 },
                    )
                )
            }
            if (response.status.value in 200..299) {
                response.body<WatchlistUpdateResponse>().also { result ->
                    if (!suppressTrackerSync) {
                        autoTrackingSyncService?.enqueueUpsert(
                            animeId = animeId,
                            malId = malId,
                            anilistId = anilistId,
                            status = normalizedFolder,
                            startedAt = result.data?.startedAt,
                            completedAt = result.data?.completedAt,
                        )
                    }
                }
            } else null
        } catch (e: Exception) {
            println("[WatchlistService] updateStatus error: ${e.message}")
            null
        }
    }

    suspend fun removeFromWatchlist(
        animeId: String,
        anilistId: Int? = null,
        malId: Int? = null,
        suppressTrackerSync: Boolean = false,
    ): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val response = httpClient.delete("${AnisurgeApi.v1Base}/watchlist/${animeId.encodeURLPathPart()}") {
                applyAnisurgeAuth(stored)
            }
            (response.status.value in 200..299).also { success ->
                if (success && !suppressTrackerSync) {
                    autoTrackingSyncService?.enqueueDelete(animeId, malId, anilistId)
                }
            }
        } catch (e: Exception) {
            println("[WatchlistService] removeFromWatchlist error: ${e.message}")
            false
        }
    }

    private fun String.toProjectRFolder(): String = when (trim().uppercase()) {
        "CURRENT", "WATCHING" -> "WATCHING"
        "PLAN TO WATCH", "PLAN_TO_WATCH", "PLANNING" -> "PLANNING"
        "ON HOLD", "ON_HOLD", "PAUSED" -> "PAUSED"
        "COMPLETED" -> "COMPLETED"
        "DROPPED" -> "DROPPED"
        "REMOVE" -> "REMOVE"
        else -> trim().uppercase()
    }
}
