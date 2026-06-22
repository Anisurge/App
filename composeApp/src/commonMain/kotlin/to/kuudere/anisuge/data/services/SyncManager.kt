package to.kuudere.anisuge.data.services

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrates parallel sync to MAL and AniList on episode completion.
 * Silently fails per-service (one failing doesn't affect the other).
 */
class SyncManager(
    private val trackingService: TrackingService,
    private val autoTrackingSyncService: AutoTrackingSyncService? = null,
) {
    suspend fun isAutoSyncEnabled(): Boolean = autoTrackingSyncService?.isEnabled() == true

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
        autoSync.enqueueUpsert(
            animeId = anilistId?.let { "anilist-$it" } ?: malId?.let { "mal-$it" }.orEmpty(),
            malId = malId,
            anilistId = anilistId,
            status = if (totalEpisodes != null && episodeNumber >= totalEpisodes) "COMPLETED" else "WATCHING",
            progress = episodeNumber,
            totalEpisodes = totalEpisodes,
        )
    }
}
