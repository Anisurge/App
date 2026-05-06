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

val FALLBACK_SERVERS = listOf(
    ServerInfo(id = "suzu", label = "Suzu", type = "sub_dub", active = true),
    ServerInfo(id = "animepahe", label = "AnimePahe", type = "sub_dub", active = true),
)
