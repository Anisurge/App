package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AnimeDetails(
    @SerialName("anime_id") val animeId: String = "",
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("mal_id") val malId: Int? = null,
    val title: AnimeTitle = AnimeTitle(),
    @SerialName("cover_image") val coverImage: CoverImage = CoverImage(),
    @SerialName("banner_image") val bannerImage: String = "",
    val description: String = "",
    val format: String = "",
    val type: String? = null,
    val status: String = "",
    val episodes: List<EpisodeItem>? = null,
    @SerialName("episodes_total") val episodesTotal: Int = 0,
    @SerialName("season_year") val seasonYear: Int? = null,
    val season: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    @SerialName("average_score") val averageScore: Int? = null,
    @SerialName("mal_score") val malScore: Int? = null,
    @SerialName("mean_score") val meanScore: Int? = null,
    val popularity: Int? = null,
    val duration: String = "",
    val subbed: Int = 0,
    val dubbed: Int = 0,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val synonyms: List<String> = emptyList(),
    @SerialName("country_of_origin") val countryOfOrigin: String? = null,
    val source: String? = null,
    @SerialName("is_adult") val isAdult: Boolean = false,
    val favourites: Int? = null,
    val ranking: Int? = null,
    val trailer: String? = null,
    val hashtag: String? = null,
    @SerialName("can_watch") val canWatch: Boolean = false,
    @SerialName("can_request") val canRequest: Boolean = false,
    val artworks: List<Artwork>? = null,
    val characters: List<JsonObject>? = null,
    val staff: List<JsonObject>? = null,
    val relations: List<JsonObject>? = null,
    @SerialName("external_links") val externalLinks: List<JsonObject>? = null,
    @SerialName("recommendation_ids") val recommendationIds: List<String>? = null,
    @SerialName("next_airing_episode") val nextAiringEpisode: NextAiringEpisode? = null,
    @SerialName("sub_release") val subRelease: SubRelease? = null,
    val watchlist: WatchlistInfo? = null,
    @SerialName("watch_progress") val watchProgress: WatchProgress? = null,
    @SerialName("score_distribution") val scoreDistribution: Map<String, Int>? = null,
    @SerialName("status_distribution") val statusDistribution: Map<String, Int>? = null,
    @SerialName("external_seasons") val externalSeasons: List<JsonObject>? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    @SerialName("last_episode") val lastEpisode: Int? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_licensed") val isLicensed: Boolean? = null,
    @SerialName("is_locked") val isLocked: Boolean? = null,
    val trending: Boolean? = null,
    val requested: Boolean? = null,
) {
    val displayTitle: String get() = title.displayTitle
    val imageUrl: String get() = coverImage.bestUrl
    val bannerUrl: String? get() = bannerImage.ifBlank { null }
    val activeId: String get() = animeId
    val activeSlug: String get() = animeId
    val inWatchlist: Boolean get() = watchlist != null
    val folder: String? get() = watchlist?.folder
    val epCount: Int get() = episodesTotal
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
data class WatchlistInfo(
    val folder: String? = null,
    val notes: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class WatchProgress(
    val episode: Int? = null,
    @SerialName("current_time") val currentTime: Double? = null,
    val server: String? = null,
)

@Serializable
data class SubRelease(
    @SerialName("airing_at") val airingAt: String? = null,
    val episode: Int? = null,
)

@Serializable
data class Artwork(
    val url: String? = null,
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
data class EpisodeItem(
    @SerialName("episode_number") val number: Int = 0,
    @SerialName("episodeId") val id: String = "",
    val title: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    @SerialName("title_romanji") val titleRomanji: String? = null,
    val aired: String? = null,
    val duration: Int? = null,
    val thumbnail: String? = null,
    val description: String? = null,
    @SerialName("is_filler") val filler: Boolean? = null,
    @SerialName("is_recap") val recap: Boolean? = null,
    val subbed: Boolean = false,
    val dubbed: Boolean = false,
    val playable: Boolean = false,
    val site: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val url: String? = null,
    // Legacy fields for compatibility
    val titles: List<String?>? = null,
    val ago: String? = null,
)

@Serializable
data class EpisodeListResponse(
    val episodes: List<EpisodeItem> = emptyList(),
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

@Serializable
data class WatchInfoResponse(
    @SerialName("anime_id") val animeId: String? = null,
    val title: AnimeTitle? = null,
    @SerialName("cover_image") val coverImage: CoverImage? = null,
    @SerialName("banner_image") val bannerImage: String? = null,
    @SerialName("anilist_id") val anilistId: Int? = null,
    val folder: String? = null,
    val episode: Int? = null,
    @SerialName("current_time") val currentTime: Double? = null,
    val server: String? = null,
    val language: String? = null,
    val episodes: List<EpisodeItem>? = null,
    @SerialName("sub_release") val subRelease: SubRelease? = null,
)

@Serializable
data class RecommendationsResponse(
    val recommendations: List<AnimeItem> = emptyList(),
)
