package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServersResponse(
    val servers: List<ServerInfo> = emptyList()
)

@Serializable
data class ServerInfo(
    val id: String,
    val label: String,
    val type: String // "dual", "sub", "dub"
) {
    /**
     * Returns the API server name (e.g., "zen-2" for "zen2")
     */
    val apiName: String
        get() = when (id) {
            "zen2" -> "Zen-2"
            "zen" -> "Zen"
            "hiya", "hiya-dub" -> "Hiya"
            else -> id.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    /**
     * Returns true if this server supports dub audio
     */
    val supportsDub: Boolean
        get() = type == "dual" || type == "dub" || id.endsWith("-dub")

    /**
     * Returns true if this server supports sub audio
     */
    val supportsSub: Boolean
        get() = type == "dual" || type == "sub" || (!id.endsWith("-dub") && type != "dub")

    /**
     * Returns the display name for UI
     */
    val displayName: String
        get() = label
}

/**
 * Default hardcoded servers as fallback when API fails
 */
val FALLBACK_SERVERS = listOf(
    ServerInfo(id = "animepahe", label = "AnimePahe", type = "dual"),
    ServerInfo(id = "suzu", label = "Suzu", type = "dual"),
    ServerInfo(id = "animekai", label = "AnimeKai", type = "dual"),
    ServerInfo(id = "hianime", label = "HiAnime", type = "dual")
)
