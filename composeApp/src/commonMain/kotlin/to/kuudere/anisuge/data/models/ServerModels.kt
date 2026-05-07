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

/** Split catalog `sub_dub` rows into separate Sub vs Dub stream source ids (`base` and `base-dub`) for UI and priority. */
fun ServerInfo.expandToSelectable(): List<ServerInfo> = when (type) {
    "sub_dub" -> listOf(
        copy(label = "$label (Sub)", type = "sub"),
        copy(id = "${id}-dub", label = "$label (Dub)", type = "dub"),
    )
    else -> listOf(this)
}

fun List<ServerInfo>.expandForSelection(): List<ServerInfo> = flatMap { it.expandToSelectable() }

/**
 * Stable ordering of selectable server ids for settings + playback fallback.
 * [savedPriority] may contain legacy base ids only; dub variants are inserted after sub when missing.
 */
fun orderSelectableServerIds(
    catalog: List<ServerInfo>,
    savedPriority: List<String>,
    defaultBaseOrder: List<String>,
): List<String> {
    val expanded = catalog.expandForSelection()
    val expandedIdsList = expanded.map { it.id }
    if (expandedIdsList.isEmpty()) return emptyList()
    val expandedSet = expandedIdsList.toSet()
    if (savedPriority.isEmpty()) {
        val priority = mutableListOf<String>()
        for (baseId in defaultBaseOrder) {
            val s = catalog.find { it.id == baseId } ?: continue
            when (s.type) {
                "sub_dub" -> {
                    priority.add(s.id)
                    priority.add("${s.id}-dub")
                }
                else -> priority.add(s.id)
            }
        }
        for (id in expandedIdsList) {
            if (id !in priority) priority.add(id)
        }
        return priority
    }
    val result = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (pid in savedPriority) {
        if (pid !in expandedSet || pid in seen) continue
        result.add(pid)
        seen.add(pid)
        if (!pid.endsWith("-dub", ignoreCase = true)) {
            val s = catalog.find { it.id == pid }
            if (s?.type == "sub_dub") {
                val dubId = "${pid}-dub"
                if (dubId in expandedSet && dubId !in seen && savedPriority.none { it.equals(dubId, ignoreCase = true) }) {
                    result.add(dubId)
                    seen.add(dubId)
                }
            }
        }
    }
    for (id in expandedIdsList) {
        if (id !in seen) {
            result.add(id)
            seen.add(id)
        }
    }
    return result
}

/** Default catalog matches api.md Anime Streaming: one `source` id per provider; batch_scrape returns both `sub` and `dub`. */
val FALLBACK_SERVERS = listOf(
    ServerInfo(id = "zen2", label = "Zen-2", type = "sub_dub", active = false),
    ServerInfo(id = "zen", label = "Zen", type = "sub_dub", active = true),
    ServerInfo(id = "suzu", label = "Suzu", type = "sub_dub", active = true),
    ServerInfo(id = "animepahe", label = "AnimePahe", type = "sub_dub", active = true),
)
