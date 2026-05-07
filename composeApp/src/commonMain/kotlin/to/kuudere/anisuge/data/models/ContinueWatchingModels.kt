package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parses `/watch/continue` episode ids such as `ep-4`, `ep-12` (see api.md — [Save Watch Progress]).
 */
internal fun episodeNumberFromContinueEpisodeId(episodeId: String): Int {
    val s = episodeId.trim().lowercase()
    if (s.startsWith("ep-")) {
        val tail = s.removePrefix("ep-").trim()
        return tail.substringBefore('-').toDoubleOrNull()?.toInt()
            ?: tail.toIntOrNull()
            ?: 0
    }
    return s.toIntOrNull() ?: 0
}

@Serializable
data class ContinueWatchingResponse(
    val data: List<ContinueWatchingItem> = emptyList(),
    val limit: Int = 20,
    val offset: Int = 0,
    val total: Int = 0,
    @SerialName("totalPages") val totalPages: Int = 0,
)

@Serializable
data class ContinueWatchingItem(
    val anime: AnimeItem = AnimeItem(),
    @SerialName("animeId") val animeIdStr: String = "",
    @SerialName("continueId") val continueId: String = "",
    @SerialName("episodeId") val episodeId: String = "",
    @SerialName("currentTime") val progress: Double = 0.0,
    val duration: Double = 0.0,
    val server: String? = null,
    val language: String? = null,
    @SerialName("lastUpdated") val updatedAt: String? = null,
    /** Set when API includes a numeric episode; otherwise use [displayEpisode] (from [episodeId]). */
    val episode: Int = 0,
) {
    val effectiveAnimeId: String get() = animeIdStr.ifBlank { anime.animeId }
    val displayTitle: String get() = anime.displayTitle
    val imageUrl: String get() = anime.imageUrl
    val bannerUrl: String? get() = anime.bannerUrl
    val animeId: String get() = effectiveAnimeId
    val cover: String get() = imageUrl
    val banner: String? get() = bannerUrl

    /** Episode number for badges and resume; `/watch/continue` typically only returns [episodeId] (e.g. `ep-4`). */
    val displayEpisode: Int get() = if (episode > 0) episode else episodeNumberFromContinueEpisodeId(episodeId)
}
