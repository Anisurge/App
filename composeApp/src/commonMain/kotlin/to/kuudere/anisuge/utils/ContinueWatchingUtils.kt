package to.kuudere.anisuge.utils

import to.kuudere.anisuge.data.models.ContinueWatchingItem

/** ISO-ish timestamps from API sort newest-first when compared as strings (same as BFF `lastUpdated`). */
private fun ContinueWatchingItem.sortKey(): String = updatedAt.orEmpty()

/**
 * One card per series on home: keep the most recently updated episode per [ContinueWatchingItem.animeId].
 * All rows stay in the database; this is display-only.
 */
fun List<ContinueWatchingItem>.latestPerAnime(): List<ContinueWatchingItem> =
    groupBy { it.animeId.ifBlank { it.effectiveAnimeId } }
        .mapNotNull { (_, episodes) ->
            episodes.maxWithOrNull(compareBy<ContinueWatchingItem> { it.sortKey() })
        }
        .sortedByDescending { it.sortKey() }

fun List<ContinueWatchingItem>.sortedByRecent(): List<ContinueWatchingItem> =
    sortedByDescending { it.sortKey() }
