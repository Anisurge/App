package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeTitle(
    val english: String = "",
    val native: String = "",
    val romaji: String = "",
    @SerialName("user_preferred") val userPreferred: String = "",
) {
    val displayTitle: String get() = userPreferred.ifBlank { english.ifBlank { romaji.ifBlank { native } } }
}

@Serializable
data class CoverImage(
    val color: String = "",
    @SerialName("extra_large") val extraLarge: String = "",
    val large: String = "",
    val medium: String = "",
) {
    val bestUrl: String get() = extraLarge.ifBlank { large.ifBlank { medium } }
}

@Serializable
data class AnimeItem(
    @SerialName("anime_id") val animeId: String = "",
    val title: AnimeTitle = AnimeTitle(),
    @SerialName("cover_image") val coverImage: CoverImage = CoverImage(),
    @SerialName("banner_image") val bannerImage: String = "",
    val description: String = "",
    val format: String = "",
    val status: String = "",
    val episodes: Int? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    @SerialName("season_year") val seasonYear: Int? = null,
    val season: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("average_score") val averageScore: Int? = null,
    @SerialName("mal_score") val malScore: Int? = null,
    @SerialName("mean_score") val meanScore: Int? = null,
    val popularity: Int? = null,
    val duration: String = "",
    val subbed: Int = 0,
    val dubbed: Int = 0,
    @SerialName("can_watch") val canWatch: Boolean = false,
    @SerialName("can_request") val canRequest: Boolean = false,
    val folder: String? = null,
    val type: String? = null,
    // Fields from latest_aired episode info
    val episode: EpisodeBrief? = null,
    @SerialName("next_airing_episode") val nextAiringEpisode: NextAiringEpisode? = null,
    // Fields from search results
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("mal_id") val malId: Int? = null,
) {
    val displayTitle: String get() = title.displayTitle
    val imageUrl: String get() = coverImage.bestUrl
    val bannerUrl: String? get() = bannerImage.ifBlank { null }
    val activeId: String get() = animeId
    val activeSlug: String get() = animeId
    val epCount: Int? get() = episodes ?: episodesTotal
    val score: Int? get() = averageScore ?: meanScore ?: malScore
    // Backward compatibility
    val id: String get() = animeId
    val english: String get() = title.english
    val romaji: String get() = title.romaji
    val cover: String get() = coverImage.bestUrl
    val banner: String? get() = bannerUrl
    val image: String get() = coverImage.extraLarge
    val poster: String get() = coverImage.large
    val slug: String get() = animeId
    val year: Int? get() = seasonYear
}

@Serializable
data class EpisodeBrief(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val aired: String = "",
    val playable: Boolean = false,
    val thumbnail: String = "",
    val title: String = "",
)

@Serializable
data class NextAiringEpisode(
    @SerialName("airing_at") val airingAt: String = "",
    val episode: Int = 0,
    @SerialName("time_until_airing") val timeUntilAiring: Int = 0,
)

@Serializable
data class HomeData(
    @SerialName("latest_aired") val latestAired: List<AnimeItem> = emptyList(),
    @SerialName("new_on_site") val newOnSite: List<AnimeItem> = emptyList(),
    val trending: List<AnimeItem> = emptyList(),
    val upcoming: List<AnimeItem> = emptyList(),
    @SerialName("has_continue_watching") val hasContinueWatching: Boolean = false,
)

@Serializable
data class LatestAiredResponse(
    val episodes: List<AnimeItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)
