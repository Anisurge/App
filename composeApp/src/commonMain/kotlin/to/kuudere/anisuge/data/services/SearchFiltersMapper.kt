package to.kuudere.anisuge.data.services

/**
 * Maps search UI labels to Project-R `/search` query values (verified against live API).
 * @see api.md Search Anime
 */
object SearchFiltersMapper {
    fun formatForApi(ui: String?): String? = when (ui?.trim()) {
        null, "" -> null
        "TV" -> "TV"
        "TV Short" -> "TV_SHORT"
        "Movie" -> "MOVIE"
        "Special" -> "SPECIAL"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "Music" -> "MUSIC"
        else -> ui.uppercase().replace(' ', '_')
    }

    fun seasonForApi(ui: String?): String? =
        ui?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()

    fun yearForApi(ui: String?): Int? =
        ui?.trim()?.toIntOrNull()

    fun sortForApi(ui: String?): String? = when (ui?.trim()) {
        null, "", "Popularity" -> "popularity_desc"
        "Latest" -> "latest_desc"
        "Score" -> "score_desc"
        "Year" -> "year_desc"
        "Episodes" -> "episodes_desc"
        else -> null
    }

    fun countryForApi(ui: String?): String? = when (ui?.trim()) {
        null, "" -> null
        "Japan" -> "JP"
        "South Korea" -> "KR"
        "China" -> "CN"
        "Taiwan" -> "TW"
        else -> null
    }

    /** Multiple genres: comma-separated single `genre` param. */
    fun genresForApi(selected: List<String>): String? =
        selected.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(",")
}
