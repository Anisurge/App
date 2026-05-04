package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeDetails(
    @SerialName("anime_id") val animeId: String = "",
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("mal_id") val malId: Int? = null,
    val title: AnimeTitle? = null,
    @SerialName("cover_image") val coverImage: CoverImage? = null,
    @SerialName("banner_image") val bannerImage: String? = null,
    val description: String? = null,
    val format: String? = null,
    val type: String? = null,
    val status: String? = null,
    val genres: List<String>? = emptyList(),
    val tags: List<String>? = emptyList(),
    val season: String? = null,
    @SerialName("season_year") val seasonYear: Int? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    val duration: Int? = null,
    val subbed: Int? = null,
    val dubbed: Int? = null,
    @SerialName("average_score") val averageScore: Int? = null,
    @SerialName("mal_score") val malScore: Int? = null,
    @SerialName("mean_score") val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    @SerialName("can_watch") val canWatch: Boolean? = null,
    @SerialName("can_request") val canRequest: Boolean? = null,
    @SerialName("is_adult") val isAdult: Boolean? = null,
    @SerialName("country_of_origin") val countryOfOrigin: String? = null,
    val country: String? = null,
    val source: String? = null,
    val studios: List<String>? = emptyList(),
    val synonyms: List<String>? = emptyList(),
    @SerialName("start_date") val startDate: FuzzyDate? = null,
    @SerialName("end_date") val endDate: FuzzyDate? = null,
    @SerialName("next_airing_episode") val nextAiringEpisode: NextAiringEpisode? = null,
    @SerialName("last_episode") val lastEpisode: Int? = null,
    @SerialName("watchlist") val watchlist: WatchlistInfo? = null,
    @SerialName("watch_progress") val watchProgress: WatchProgressInfo? = null,
    @SerialName("continue_watching") val continueWatching: ContinueWatching? = null,
    @SerialName("recommendation_ids") val recommendationIds: List<String>? = emptyList(),
    val relations: List<AnimeRelation>? = emptyList(),
    val characters: List<CharacterInfo>? = emptyList(),
    val staff: List<StaffInfo>? = emptyList(),
    val artworks: List<ArtworkInfo>? = emptyList(),
    val trailer: TrailerInfo? = null,
    @SerialName("external_links") val externalLinks: List<ExternalLink>? = emptyList(),
    // Legacy fields
    val id: String = "",
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null,
    val ageRating: String? = null,
    val cover: String? = "",
    val banner: String? = "",
    val epCount: Int? = 0,
    val subbedCount: Int? = 0,
    val dubbedCount: Int? = 0,
    val year: Int? = 0,
    val slug: String? = null,
    @SerialName("in_watchlist") val inWatchlist: Boolean = false,
    val folder: String? = null,
    val views: String? = "0",
    val likes: String? = "0",
) {
    val displayTitle: String get() = title?.userPreferred ?: title?.english ?: english ?: title?.romaji ?: romaji ?: "Unknown"
    val imageUrl: String get() = coverImage?.extraLarge ?: coverImage?.large ?: coverImage?.medium ?: cover ?: ""
    val bannerUrl: String? get() = bannerImage?.ifBlank { null } ?: banner
    val activeSlug: String get() = animeId.ifBlank { slug ?: id }
    val displayEpCount: Int get() = episodesTotal ?: epCount ?: 0
    val displaySubbed: Int get() = subbed ?: subbedCount ?: 0
    val displayDubbed: Int get() = dubbed ?: dubbedCount ?: 0
    val displayYear: Int get() = seasonYear ?: year ?: 0
    val isInWatchlist: Boolean get() = inWatchlist || watchlist != null
    val displayCountry: String? get() = country ?: countryOfOrigin
}

@Serializable
data class NextAiringEpisode(
    @SerialName("airing_at") val airingAt: Long? = null,
    @SerialName("episode") val episode: Int? = null,
)

@Serializable
data class WatchlistInfo(
    val folder: String? = null,
    val notes: String? = null,
    @SerialName("started_at") val startedAt: FuzzyDate? = null,
    @SerialName("completed_at") val completedAt: FuzzyDate? = null,
)

@Serializable
data class WatchProgressInfo(
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("current_time") val currentTime: Double? = null,
    val duration: Double? = null,
    val server: String? = null,
    val language: String? = null,
)

@Serializable
data class FuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class AnimeRelation(
    @SerialName("anime_id") val animeId: String? = null,
    val type: String? = null,
    val title: AnimeTitle? = null,
    val format: String? = null,
)

@Serializable
data class CharacterInfo(
    val name: String? = null,
    val role: String? = null,
    val image: String? = null,
)

@Serializable
data class StaffInfo(
    val name: String? = null,
    val role: String? = null,
    val image: String? = null,
)

@Serializable
data class ArtworkInfo(
    val url: String? = null,
    @SerialName("image_type") val imageType: String? = null,
    val source: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class TrailerInfo(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class ExternalLink(
    val url: String? = null,
    val site: String? = null,
    val type: String? = null,
)

@Serializable
data class AnimeDetailsResponse(
    val data: AnimeDetails? = null,
)

@Serializable
data class ContinueWatching(
    @SerialName("continue_id") val continueId: String? = null,
    val link: String? = null,
    val episode: Int? = null,
    val progress: String? = null,
    val duration: String? = null,
)

@Serializable
data class AnimeInfoMeta(
    val id: String? = null,
    val anilist: Int? = null,
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null,
    val ageRating: String? = null,
    val malScore: Double? = null,
    val averageScore: Int? = 0,
    val duration: Int? = 0,
    val genres: List<String>? = emptyList(),
    val folder: String? = null,
    val cover: String? = null,
    val banner: String? = null,
    val season: String? = null,
    val startDate: String? = null,
    val status: String? = null,
    val synonyms: List<String>? = emptyList(),
    val studios: List<String>? = emptyList(),
    val type: String? = null,
    val year: Int? = 0,
    val epCount: Int? = 0,
    val subbedCount: Int? = 0,
    val dubbedCount: Int? = 0,
    val description: String? = null,
    val url: String? = null,
    @SerialName("inWatchlist") val inWatchlist: Boolean? = null,
    val userLiked: Boolean? = null,
    val userUnliked: Boolean? = null,
    val likes: Int? = 0,
    val dislikes: Int? = 0,
)

@Serializable
data class EpisodeLink(
    val dataLink: String? = null,
    val dataType: String? = null,
    val serverName: String? = null,
)

@Serializable
data class EpisodeDataResponse(
    @SerialName("episode_id") val episodeId: String? = null,
    val current: Int? = null,
    @SerialName("total_comments") val totalComments: Int? = 0,
    @SerialName("all_episodes") val allEpisodes: List<EpisodeItem>? = emptyList(),
    @SerialName("anime_info") val animeInfo: AnimeInfoMeta? = null,
    @SerialName("episode_links") val episodeLinks: List<EpisodeLink>? = emptyList(),
    val inWatchlist: Boolean = false,
    val folder: String? = null,
)

@Serializable
data class ThumbnailsResponse(
    val success: Boolean = true,
    val thumbnails: Map<String, String>? = emptyMap(),
)
