package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleApiResponse(
    val schedule: List<ScheduleDay> = emptyList(),
    val timezone: String = "UTC",
    val year: Int? = null,
    val month: Int? = null,
)

@Serializable
data class ScheduleDay(
    val date: String = "",
    val day: String = "",
    val episodes: List<ScheduleAnime> = emptyList(),
)

@Serializable
data class ScheduleAnime(
    @SerialName("anime_id") val animeId: String = "",
    val title: AnimeTitle = AnimeTitle(),
    @SerialName("cover_image") val coverImage: CoverImage = CoverImage(),
    @SerialName("banner_image") val bannerImage: String = "",
    val description: String = "",
    val format: String = "TV",
    val status: String = "",
    val episodes: Int = 0,
    val genres: List<String> = emptyList(),
    @SerialName("season_year") val seasonYear: Int = 0,
    val season: String? = null,
    @SerialName("average_score") val averageScore: Int? = null,
    val subbed: Int = 0,
    val dubbed: Int = 0,
    val duration: String = "",
    @SerialName("can_watch") val canWatch: Boolean = false,
    val type: String? = null,
    @SerialName("next_airing_episode") val nextAiringEpisode: NextAiringEpisode? = null,
    @SerialName("airing_at") val airingAt: String? = null,
    @SerialName("sub_release") val subRelease: String? = null,
    // Legacy fields
    val id: String = "",
    val time: String = "",
    val cover: String = "",
    val banner: String? = null,
    val epCount: Int = 0,
    val year: Int = 0,
) {
    val imageUrl: String get() = coverImage.bestUrl.ifBlank { if (cover.startsWith("http")) cover else if (cover.isNotBlank()) "https://api.reanime.to/img/poster/$cover" else "" }
    val bannerUrl: String? get() = bannerImage.ifBlank { null } ?: banner?.let { if (it.startsWith("http")) it else if (it.isNotBlank()) "https://api.reanime.to/img/banner/$it" else null }
    val activeSlug: String get() = animeId.ifBlank { id }
    val displayTitle: String get() = title.displayTitle
}
