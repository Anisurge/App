package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val id: String,
    val label: String,
    val type: String = "sub_dub",
    val active: Boolean = true,
) {
    val supportsDub: Boolean
        get() = type == "sub_dub" || type == "dub"

    val supportsSub: Boolean
        get() = type == "sub_dub" || type == "sub"

    val displayName: String
        get() = label
}

/** Default catalog matches api.md Anime Streaming: one `source` id per provider; batch_scrape returns both `sub` and `dub`. */
val FALLBACK_SERVERS = listOf(
    ServerInfo(id = "zen2", label = "Zen-2", type = "sub_dub", active = false),
    ServerInfo(id = "zen", label = "Zen", type = "sub_dub", active = true),
    ServerInfo(id = "suzu", label = "Suzu", type = "sub_dub", active = true),
    ServerInfo(id = "animepahe", label = "AnimePahe", type = "sub_dub", active = true),
)
