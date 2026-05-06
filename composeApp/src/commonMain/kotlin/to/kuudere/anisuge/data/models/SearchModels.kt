package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val results: List<AnimeItem> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    @kotlinx.serialization.Transient val hasMore: Boolean = false,
)
