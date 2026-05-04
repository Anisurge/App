package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleDay(
    val date: String = "",
    val day: String = "",
    val episodes: List<ScheduleAnime> = emptyList(),
)

@Serializable
data class ScheduleApiResponse(
    val month: Int = 0,
    val year: Int = 0,
    val timezone: String? = null,
    val schedule: List<ScheduleDay> = emptyList(),
    // Legacy
    val data: Map<String, List<ScheduleAnime>> = emptyMap(),
) {
    /** Convert schedule list to date-keyed map for backward compatibility */
    val scheduleMap: Map<String, List<ScheduleAnime>> get() {
        if (data.isNotEmpty()) return data
        return schedule.associate { it.date to it.episodes }
    }
}

@Serializable
data class ScheduleAnime(
    @SerialName("anime_id") val animeId: String = "",
    val title: AnimeTitle? = null,
    @SerialName("cover_image") val coverImage: CoverImage? = null,
    @SerialName("banner_image") val bannerImage: String? = null,
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val genres: List<String>? = emptyList(),
    val season: String? = null,
    @SerialName("season_year") val seasonYear: Int? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    val duration: String? = null,
    val subbed: Int? = null,
    val dubbed: Int? = null,
    @SerialName("average_score") val averageScore: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("episode_date") val episodeDate: String? = null,
    @SerialName("air_type") val airType: String? = null,
    @SerialName("airing_status") val airingStatus: String? = null,
    val popularity: Int? = null,
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("mal_id") val malId: Int? = null,
    val folder: String? = null,
    // Legacy fields
    val id: String = "",
    val time: String = "",
    val cover: String = "",
    val banner: String? = null,
    val epCount: Int = 0,
    val year: Int = 0,
    val type: String = "TV",
    val slug: String? = null,
) {
    val displayTitle: String get() = title?.userPreferred ?: title?.english ?: title?.romaji ?: ""
    val imageUrl: String get() = coverImage?.extraLarge ?: coverImage?.large ?: coverImage?.medium ?: when {
        cover.startsWith("http") -> cover
        cover.isNotBlank() -> "https://api.reanime.to/img/poster/$cover"
        else -> ""
    }
    val bannerUrl: String? get() = bannerImage?.ifBlank { null } ?: when {
        banner == null -> null
        banner.startsWith("http") -> banner
        banner.isNotBlank() -> "https://api.reanime.to/img/banner/$banner"
        else -> null
    }
    val activeSlug: String get() = animeId.ifBlank { slug ?: id }
    val displayEpisodeNumber: Int get() = episodeNumber ?: 0
    val displayFormat: String get() = format ?: type.ifBlank { "TV" }
    val displayEpCount: Int get() = episodesTotal ?: epCount
    val displayFolder: String? get() = when (folder?.uppercase()) {
        "CURRENT", "WATCHING" -> "Watching"
        "PAUSED", "ON_HOLD", "ON HOLD" -> "On Hold"
        "PLANNING", "PLAN_TO_WATCH", "PLAN TO WATCH" -> "Plan To Watch"
        "COMPLETED" -> "Completed"
        "DROPPED" -> "Dropped"
        else -> folder
    }
    /** Extract time portion from episode_date (e.g. "2026-05-01T04:10:00+05:30" -> "04:10") */
    val displayTime: String get() {
        if (time.isNotBlank()) return time
        val date = episodeDate ?: return ""
        return try {
            val timePart = date.substringAfter("T").substringBefore("+").substringBefore("-")
            val (h, m) = timePart.split(":").let { it[0] to it[1] }
            "$h:$m"
        } catch (_: Exception) { "" }
    }
}
