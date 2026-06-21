package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AnimeThemesResponse(
    val anilistId: Int = 0,
    val animeTitle: String = "",
    val themes: List<AnimeThemeItem> = emptyList(),
)

@Serializable
data class AnimeThemeItem(
    val id: String = "",
    val type: String = "",
    val sequence: Int? = null,
    val songTitle: String = "",
    val artists: List<String> = emptyList(),
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val resolution: Int? = null,
    val tags: List<String> = emptyList(),
    val spoiler: Boolean = false,
    val nsfw: Boolean = false,
) {
    val playableUrl: String? get() = videoUrl ?: audioUrl
    val displayType: String get() = when (type.uppercase()) {
        "OP" -> "Opening"
        "ED" -> "Ending"
        else -> "Insert song"
    }
}
