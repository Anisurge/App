package to.kuudere.anisuge.data.services

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrates parallel sync to MAL and AniList on episode completion.
 * Silently fails per-service (one failing doesn't affect the other).
 */
class SyncManager(
    private val trackingService: TrackingService,
) {
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
    ) {
        if (malId == null && anilistId == null) return

        coroutineScope {
            if (malId != null) {
                async {
                    try {
                        trackingService.syncMalProgress(malId, episodeNumber, totalEpisodes)
                    } catch (_: Exception) {
                        // Silently fail
                    }
                }
            }
            if (anilistId != null) {
                async {
                    try {
                        trackingService.syncAnilistProgress(anilistId, episodeNumber, totalEpisodes)
                    } catch (_: Exception) {
                        // Silently fail
                    }
                }
            }
        }
    }
}
