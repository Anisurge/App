package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchlistResponse(
    val data: List<AnimeItem> = emptyList(),
    val items: List<AnimeItem> = emptyList(),
    val results: List<AnimeItem> = emptyList(),
    @SerialName("watchlist") val watchlistList: List<AnimeItem>? = null,
    @SerialName("watchlist_items") val watchlistItems: List<AnimeItem> = emptyList(),
    val total: Int = 0,
    @SerialName("has_more") val hasMoreSnake: Boolean = false,
    val hasMore: Boolean = false,
) {
    val entries: List<AnimeItem> get() = data.ifEmpty { items.ifEmpty { results.ifEmpty { watchlistList ?: watchlistItems } } }
    fun hasMore(limit: Int, offset: Int): Boolean = hasMore || hasMoreSnake || (offset + limit < total)
}

@Serializable
data class WatchlistUpdateResponse(
    val success: Boolean = true,
    val message: String? = null,
    val data: WatchlistUpdateData? = null,
    val error: String? = null,
)

@Serializable
data class WatchlistUpdateData(
    val itemId: String? = null,
    val folder: String? = null,
    val anime: String? = null,
    val token: String? = null,
    val anilist: Int? = null,
    val inWatchlist: Boolean = false
)
