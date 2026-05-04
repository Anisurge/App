package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeTitle(
    val english: String? = null,
    val native: String? = null,
    val romaji: String? = null,
    @SerialName("user_preferred") val userPreferred: String? = null,
)

@Serializable
data class CoverImage(
    val color: String? = null,
    @SerialName("extra_large") val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class LatestEpisode(
    val aired: String? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    val playable: Boolean? = null,
    val thumbnail: String? = null,
    val title: String? = null,
)

@Serializable
data class AnimeItem(
    @SerialName("anime_id") val animeId: String = "",
    @SerialName("itemId") val itemId: String? = null,
    val title: AnimeTitle? = null,
    @SerialName("cover_image") val coverImage: CoverImage? = null,
    @SerialName("banner_image") val bannerImage: String? = null,
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val genres: List<String>? = emptyList(),
    val season: String? = null,
    @SerialName("season_year") val seasonYear: Int? = null,
    val episodes: Int? = null,
    val duration: String? = null,
    val subbed: Int? = null,
    val dubbed: Int? = null,
    @SerialName("average_score") val averageScore: Int? = null,
    @SerialName("mal_score") val malScore: Int? = null,
    val popularity: Int? = null,
    @SerialName("can_watch") val canWatch: Boolean? = null,
    @SerialName("can_request") val canRequest: Boolean? = null,
    val episode: LatestEpisode? = null,
    // Watchlist-specific field
    val folder: String? = null,
    // Legacy fields for backward compatibility
    val id: String = "",
    val english: String? = null,
    val romaji: String? = null,
    val cover: String? = null,
    @SerialName("carouselBanner") val carouselBanner: String? = null,
    val banner: String? = null,
    @SerialName("epCount") val epCount: Int? = null,
    val type: String? = null,
    val year: Int? = null,
    val tags: List<String>? = emptyList(),
    val studios: List<String>? = emptyList(),
    val slug: String? = null,
    @SerialName("mainId") val mainId: String? = null,
    @SerialName("anilistId") val anilistId: Int? = null,
) {
    val displayTitle: String get() = title?.userPreferred ?: title?.english ?: english ?: title?.romaji ?: romaji ?: ""
    val imageUrl: String get() = coverImage?.extraLarge ?: coverImage?.large ?: coverImage?.medium ?: cover ?: ""
    val bannerUrl: String? get() = bannerImage?.ifBlank { null } ?: carouselBanner ?: banner
    val activeId: String get() = animeId.ifBlank { itemId?.ifBlank { mainId?.ifBlank { id } ?: id } ?: mainId?.ifBlank { id } ?: id }
    val activeSlug: String get() = animeId.ifBlank { slug?.ifBlank { id } ?: id }
    val displayFolder: String? get() = when (folder?.uppercase()) {
        "CURRENT", "WATCHING" -> "Watching"
        "PAUSED", "ON_HOLD", "ON HOLD" -> "On Hold"
        "PLANNING", "PLAN_TO_WATCH", "PLAN TO WATCH" -> "Plan To Watch"
        "COMPLETED" -> "Completed"
        "DROPPED" -> "Dropped"
        else -> folder
    }
}

@Serializable
data class ContinueWatchingItem(
    val animeId: String = "",
    val episodeId: String = "",
    val duration: String = "",
    val episode: Int = 0,
    val link: String = "",
    val progress: String = "",
    val server: String? = null,
    val language: String? = null,
    val thumbnail: String = "",
    val title: String = "",
)

@Serializable
data class HomeData(
    @SerialName("latest_aired") val latestAired: List<AnimeItem> = emptyList(),
    @SerialName("new_on_site") val newOnSite: List<AnimeItem> = emptyList(),
    val trending: List<AnimeItem> = emptyList(),
    val upcoming: List<AnimeItem> = emptyList(),
    // Legacy field mappings for backward compatibility
    @SerialName("new_additions") val newAdditions: List<AnimeItem> = emptyList(),
    @SerialName("lastUpdated") val lastUpdated: List<AnimeItem> = emptyList(),
    @SerialName("top_upcoming") val topUpcoming: List<AnimeItem> = emptyList(),
    @SerialName("hasContinueWatching") val hasContinueWatching: Boolean = false,
) {
    val topAired: List<AnimeItem> get() = latestAired.ifEmpty { emptyList() }
    val latestEps: List<AnimeItem> get() = newOnSite.ifEmpty { newAdditions }
    val displayUpcoming: List<AnimeItem> get() = upcoming.ifEmpty { topUpcoming }
}

@Serializable
data class LatestAiredResponse(
    val data: List<AnimeItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class EpisodeItem(
    @SerialName("episodeId") val id: String = "",
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val title: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    @SerialName("title_romanji") val titleRomanji: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val aired: String? = null,
    val duration: Int? = null,
    @SerialName("is_filler") val isFiller: Boolean = false,
    @SerialName("is_recap") val isRecap: Boolean = false,
    val url: String? = null,
    // Legacy fields
    val number: Int = 0,
    val titles: List<String?>? = emptyList(),
    val ago: String? = null,
    val filler: Boolean = false,
    val recap: Boolean = false,
) {
    val displayNumber: Int get() = if (episodeNumber > 0) episodeNumber else number
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() }
        ?: titles?.filterNotNull()?.firstOrNull { it.isNotBlank() }
        ?: titleRomanji?.takeIf { it.isNotBlank() }
        ?: "Episode $displayNumber"
    val displayTitles: List<String?> get() = titles?.takeIf { it.isNotEmpty() } ?: listOf(displayTitle)
    val isFillerEpisode: Boolean get() = isFiller || filler
    val isRecapEpisode: Boolean get() = isRecap || recap
}

@Serializable
data class EpisodesResponse(
    val data: List<EpisodeItem> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val total: Int = 0,
    val limit: Int = 30,
    val offset: Int = 0,
    @SerialName("totalPages") val totalPages: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
    val source: String? = null,
) {
    val episodeList: List<EpisodeItem> get() = data.ifEmpty { episodes }
}
