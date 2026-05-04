package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val success: Boolean = true,
    val results: List<AnimeItem> = emptyList(),
    @SerialName("animeData") val animeData: List<AnimeItem>? = emptyList(),
    val data: List<AnimeItem>? = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    @SerialName("totalPages") val totalPages: Int = 0,
    @SerialName("currentPage") val currentPage: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("processing_ms") val processingMs: Int? = null,
    val query: String? = null,
    val facets: Map<String, Map<String, Int>>? = null,
) {
    val items: List<AnimeItem> get() = results.ifEmpty { animeData?.ifEmpty { data ?: emptyList() } ?: data ?: emptyList() }
}
