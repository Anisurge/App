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
    val tags: List<JsonObject> = emptyList(),
    val studios: List<JsonObject> = emptyList(),
    @SerialName("average_score") val averageScore: Int? = null,
    @SerialName("mal_score") val malScore: Int? = null,
    @SerialName("mean_score") val meanScore: Int? = null,
    val popularity: Int? = null,
    val duration: Int? = null,
    val subbed: Int = 0,
    val dubbed: Int = 0,
    @SerialName("start_date") val startDate: DateObject? = null,
    @SerialName("end_date") val endDate: DateObject? = null,
    val synonyms: List<String> = emptyList(),
    @SerialName("country_of_origin") val countryOfOrigin: String? = null,
    val source: String? = null,
    @SerialName("is_adult") val isAdult: Boolean = false,
    val favourites: Int? = null,
    val ranking: Int? = null,
    val trailer: TrailerInfo? = null,
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
    @SerialName("external_seasons") val externalSeasons: Map<String, Int>? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    @SerialName("last_episode") val lastEpisode: Int? = null,
    @SerialName("last_episode_aired_at") val lastEpisodeAiredAt: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_licensed") val isLicensed: Boolean? = null,
    @SerialName("is_locked") val isLocked: Boolean? = null,
    val trending: Int? = null,
    val requested: Boolean? = null,
    val rankings: List<JsonObject>? = null,
    // Extra IDs from the API
    @SerialName("anidb_id") val anidbId: Int? = null,
    @SerialName("anime_planet_id") val animePlanetId: String? = null,
    @SerialName("animecountdown_id") val animecountdownId: String? = null,
    @SerialName("animenewsnetwork_id") val animenewsnetworkId: Int? = null,
    @SerialName("anisearch_id") val anisearchId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("kitsu_id") val kitsuId: Int? = null,
    @SerialName("livechart_id") val livechartId: Int? = null,
    @SerialName("simkl_id") val simklId: Int? = null,
    @SerialName("themoviedb_id") val themoviedbId: Int? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
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
data class DateObject(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
)

@Serializable
data class TrailerInfo(
    val id: String = "",
    val site: String = "",
    val thumbnail: String = "",
)

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
    @SerialName("image_type") val imageType: String? = null,
    val source: String? = null,
    val height: Int? = null,
    val width: Int? = null,
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
    val anime: AnimeItem? = null,
    val folder: String? = null,
    val progress: WatchProgressDetail? = null,
) {
    val animeId: String? get() = anime?.animeId
    val title: AnimeTitle? get() = anime?.title
    val coverImage: CoverImage? get() = anime?.coverImage
    val bannerImage: String? get() = anime?.bannerImage
    val anilistId: Int? get() = anime?.anilistId
    val episodes: List<EpisodeItem>? get() = null
    val currentTime: Double? get() = progress?.currentTime
    val server: String? get() = progress?.server
    val language: String? get() = progress?.language
    val episode: Int? get() = null
}

@Serializable
data class WatchProgressDetail(
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("current_time") val currentTime: Double? = null,
    val duration: Double? = null,
    val server: String? = null,
    val language: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
)

@Serializable
data class RecommendationsResponse(
    val recommendations: List<RecommendationItem> = emptyList(),
    val success: Boolean? = null,
)

@Serializable
data class RecommendationItem(
    val id: String = "",
    val title: AnimeTitle = AnimeTitle(),
    @SerialName("cover_image") val coverImage: CoverImage = CoverImage(),
    val format: String = "",
    val year: Int? = null,
    val status: String = "",
    @SerialName("episodeCount") val episodeCount: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("average_score") val averageScore: Int? = null,
) {
    val displayTitle: String get() = title.displayTitle
    val imageUrl: String get() = coverImage.bestUrl
    val activeSlug: String get() = id
}
