package to.kuudere.anisuge.data.services

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Orchestrates parallel sync to MAL and AniList on episode completion.
 * Silently fails per-service (one failing doesn't affect the other).
 */
class SyncManager(
    private val trackingService: TrackingService,
    private val autoTrackingSyncService: AutoTrackingSyncService? = null,
) {
    suspend fun isAutoSyncEnabled(): Boolean = autoTrackingSyncService?.isEnabled() == true

    private fun currentYmd(): String {
        val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val m = d.monthNumber.toString().padStart(2, '0')
        val day = d.dayOfMonth.toString().padStart(2, '0')
        return "${d.year}-$m-$day"
    }

    /**
     * @param malId MAL anime ID (null = skip MAL sync)
     * @param anilistId AniList anime ID (null = skip AniList sync)
     * @param episodeNumber Current episode number completed
     * @param totalEpisodes Total episodes (null = unknown, won't auto-complete)
     */
    suspend fun syncEpisodeComplete(
        malId: Int?,
        anilistId: Int?,
        episodeNumber: Int,
        totalEpisodes: Int?,
        animeTitle: String? = null,
    ) {
        if (malId == null && anilistId == null) return
        val autoSync = autoTrackingSyncService ?: return
        if (!autoSync.isEnabled()) return
        val completing = totalEpisodes != null && episodeNumber >= totalEpisodes
        val startedAt = if (episodeNumber <= 1) currentYmd() else null
        val completedAt = if (completing) currentYmd() else null
        autoSync.enqueueUpsert(
            animeId = anilistId?.let { "anilist-$it" } ?: malId?.let { "mal-$it" }.orEmpty(),
            malId = malId,
            anilistId = anilistId,
            status = if (completing) "COMPLETED" else "WATCHING",
            progress = episodeNumber,
            totalEpisodes = totalEpisodes,
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }
}
