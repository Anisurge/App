package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchlistResponse(
    val results: List<WatchlistEntry> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    @SerialName("totalPages") val totalPages: Int = 0,
    val query: String? = null,
)

@Serializable
data class WatchlistEntry(
    val anime: AnimeItem = AnimeItem(),
    @SerialName("animeId") val animeIdStr: String = "",
    val folder: String? = null,
    @SerialName("viewerFolder") val viewerFolder: String? = null,
    val notes: String? = null,
    @SerialName("userId") val userId: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("lastUpdated") val lastUpdated: String? = null,
) {
    val effectiveAnimeId: String get() = animeIdStr.ifBlank { anime.animeId }
    val effectiveFolder: String? get() = viewerFolder ?: folder
}

@Serializable
data class WatchlistUpdateResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val data: WatchlistUpdateData? = null,
)

@Serializable
data class WatchlistUpdateData(
    @SerialName("itemId") val itemId: String? = null,
    val folder: String? = null,
    val anime: String? = null,
    val notes: String? = null,
    @SerialName("startedAt") val startedAt: String? = null,
    @SerialName("completedAt") val completedAt: String? = null,
)
