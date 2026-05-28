package to.kuudere.anisuge.utils

import to.kuudere.anisuge.data.models.ContinueWatchingItem

/** ISO-ish timestamps from API sort newest-first when compared as strings (same as BFF `lastUpdated`). */
private fun ContinueWatchingItem.sortKey(): String = updatedAt.orEmpty()

/**
 * One card per series on home: resume the furthest in-progress episode (highest ep number, then
 * most playback), not whichever row ReAnime touched last (often a stale ep-1).
 *
 * The final list is sorted by the most recent activity on ANY episode of that anime,
 * so a show you just watched always floats to the top regardless of which episode is displayed.
 */
fun List<ContinueWatchingItem>.latestPerAnime(): List<ContinueWatchingItem> =
    groupBy { it.effectiveAnimeId.ifBlank { it.animeId } }
        .mapNotNull { (_, episodes) ->
            // Pick the furthest episode as the representative card
            val representative = episodes.maxWithOrNull(
                compareBy<ContinueWatchingItem> { it.displayEpisode }
                    .thenBy { it.progress }
                    .thenBy { it.sortKey() },
            ) ?: return@mapNotNull null
            // Use the most recent timestamp from ANY episode of this anime for sorting
            val mostRecentTimestamp = episodes.maxOf { it.sortKey() }
            representative to mostRecentTimestamp
        }
        .sortedByDescending { (_, recentTimestamp) -> recentTimestamp }
        .map { (item, _) -> item }

fun List<ContinueWatchingItem>.sortedByRecent(): List<ContinueWatchingItem> =
    sortedByDescending { it.sortKey() }
