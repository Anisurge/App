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
    val tags: List<TagInfo>? = emptyList(),
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
    val studios: List<StudioInfo>? = emptyList(),
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
    val trending: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
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
    val displayFolder: String? get() = when ((folder ?: watchlist?.folder)?.uppercase()) {
        "CURRENT", "WATCHING" -> "Watching"
        "PAUSED", "ON_HOLD", "ON HOLD" -> "On Hold"
        "PLANNING", "PLAN_TO_WATCH", "PLAN TO WATCH" -> "Plan To Watch"
        "COMPLETED" -> "Completed"
        "DROPPED" -> "Dropped"
        else -> folder ?: watchlist?.folder
    }
    /** Extract studio names as strings for display */
    val studioNames: List<String> get() = studios?.mapNotNull { it.name } ?: emptyList()
    /** Extract tag names as strings for display */
    val tagNames: List<String> get() = tags?.mapNotNull { it.name } ?: emptyList()
}

@Serializable
data class StudioInfo(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("is_main") val isMain: Boolean? = null,
)

@Serializable
data class TagInfo(
    val id: Int? = null,
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val rank: Int? = null,
    @SerialName("is_adult") val isAdult: Boolean? = null,
    @SerialName("is_general_spoiler") val isGeneralSpoiler: Boolean? = null,
    @SerialName("is_media_spoiler") val isMediaSpoiler: Boolean? = null,
)

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
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("lastUpdated") val lastUpdated: String? = null,
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
    @SerialName("relation_type") val relationType: String? = null,
    val type: String? = null,
    val title: AnimeTitle? = null,
    val format: String? = null,
    @SerialName("season_year") val seasonYear: Int? = null,
    @SerialName("cover_image") val coverImage: CoverImage? = null,
) {
    val displayTitle: String get() = title?.userPreferred ?: title?.english ?: title?.romaji ?: ""
    val imageUrl: String get() = coverImage?.extraLarge ?: coverImage?.large ?: coverImage?.medium ?: ""
    val displayRelation: String? get() = relationType ?: type
}

@Serializable
data class CharacterInfo(
    val id: Int? = null,
    val name: String? = null,
    val role: String? = null,
    val image: String? = null,
)

@Serializable
data class StaffInfo(
    val id: Int? = null,
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
    val language: String? = null,
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

@Serializable
data class RecommendationsResponse(
    val recommendations: List<RecommendationItem> = emptyList(),
    val success: Boolean = true,
)

@Serializable
data class RecommendationItem(
    val id: String = "",
    val title: AnimeTitle? = null,
    @SerialName("cover_image") val coverImage: CoverImage? = null,
    val format: String? = null,
    val year: Int? = null,
    val status: String? = null,
    @SerialName("episodeCount") val episodeCount: Int? = null,
    val genres: List<String>? = emptyList(),
    @SerialName("average_score") val averageScore: Int? = null,
) {
    val displayTitle: String get() = title?.userPreferred ?: title?.english ?: title?.romaji ?: ""
    val imageUrl: String get() = coverImage?.extraLarge ?: coverImage?.large ?: coverImage?.medium ?: ""
}
