package to.kuudere.anisuge.utils

import to.kuudere.anisuge.data.models.ContinueWatchingItem

/** ISO-ish timestamps from API sort newest-first when compared as strings (same as BFF `lastUpdated`). */
private fun ContinueWatchingItem.sortKey(): String = updatedAt.orEmpty()

/**
 * One card per series on home: resume the furthest in-progress episode (highest ep number, then
 * most playback), not whichever row ReAnime touched last (often a stale ep-1).
 */
fun List<ContinueWatchingItem>.latestPerAnime(): List<ContinueWatchingItem> =
    groupBy { it.effectiveAnimeId.ifBlank { it.animeId } }
        .mapNotNull { (_, episodes) ->
            episodes.maxWithOrNull(
                compareBy<ContinueWatchingItem> { it.displayEpisode }
                    .thenBy { it.progress }
                    .thenBy { it.sortKey() },
            )
        }
        .sortedByDescending { it.sortKey() }

fun List<ContinueWatchingItem>.sortedByRecent(): List<ContinueWatchingItem> =
    sortedByDescending { it.sortKey() }
