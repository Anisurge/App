package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

/**
 * Response of `GET /top/anime?period=…`. Rows are standard [AnimeItem]s under `data`.
 * @see api.md Get Top Anime
 */
@Serializable
data class TopAnimeResponse(
    val data: List<AnimeItem> = emptyList(),
    val period: String = "",
    val cached: Boolean = false,
)

/**
 * Project the lean recommendation shape ([RecommendationItem], defined in InfoModels)
 * onto the shared [AnimeItem] used by Home rows and [to.kuudere.anisuge.ui.AnimeCard].
 */
fun RecommendationItem.toAnimeItem(): AnimeItem = AnimeItem(
    animeId = id,
    title = title,
    coverImage = coverImage,
    format = format,
    status = status,
    episodes = episodeCount,
    seasonYear = year,
    genres = genres,
    averageScore = averageScore,
    canWatch = true,
)
