package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import kotlinx.datetime.Clock
import to.kuudere.anisuge.data.models.BffLibrarySyncResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

enum class LibrarySyncDirection(val wireValue: String) {
    Merge("merge"),
    Import("import"),
    Export("export"),
}

/**
 * Merges watchlist + continue watching with ReAnime via the BFF
 * (pull remote changes, push newer local rows).
 */
class LibrarySyncService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private var lastSyncEpochMs: Long = 0L

    suspend fun syncWithReanime(
        force: Boolean = false,
        direction: LibrarySyncDirection = LibrarySyncDirection.Merge,
    ): Boolean {
        val session = sessionStore.get() ?: return false
        if (!sessionStore.isValid(session)) return false

        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastSyncEpochMs < MIN_INTERVAL_MS) return false

        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/library/sync") {
                applyAnisurgeAuth(session)
                parameter("direction", direction.wireValue)
            }
            if (response.status.value in 200..299) {
                lastSyncEpochMs = now
                response.body<BffLibrarySyncResponse>()
                true
            } else {
                println("[LibrarySyncService] sync failed: HTTP ${response.status.value}")
                false
            }
        } catch (e: Exception) {
            println("[LibrarySyncService] sync error: ${e.message}")
            false
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 60_000L
    }
}
