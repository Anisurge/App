package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchlistResponse(
    val data: List<WatchlistEntry> = emptyList(),
    val items: List<WatchlistEntry> = emptyList(),
    val results: List<WatchlistEntry> = emptyList(),
    @SerialName("watchlist") val watchlistList: List<WatchlistEntry>? = null,
    @SerialName("watchlist_items") val watchlistItems: List<WatchlistEntry> = emptyList(),
    val total: Int = 0,
    @SerialName("has_more") val hasMoreSnake: Boolean = false,
    val hasMore: Boolean = false,
    @SerialName("totalPages") val totalPages: Int = 0,
) {
    val entries: List<WatchlistEntry> get() = results.ifEmpty { data.ifEmpty { items.ifEmpty { watchlistList ?: watchlistItems } } }
    fun hasMore(limit: Int, offset: Int): Boolean = hasMore || hasMoreSnake || (offset + limit < total)
}

@Serializable
data class WatchlistEntry(
    val anime: AnimeItem? = null,
    @SerialName("animeId") val animeId: String = "",
    val folder: String? = null,
    @SerialName("viewerFolder") val viewerFolder: String? = null,
    val notes: String? = null,
    @SerialName("lastUpdated") val lastUpdated: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("userId") val userId: String? = null,
    @SerialName("itemId") val itemId: String? = null,
) {
    /** Merge the nested anime data with watchlist metadata into a flat AnimeItem */
    fun toAnimeItem(): AnimeItem {
        val base = anime ?: AnimeItem()
        return base.copy(
            animeId = base.animeId.ifBlank { animeId },
            folder = viewerFolder ?: folder,
            itemId = itemId ?: base.itemId,
        )
    }
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
