package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.platform.isDesktopPlatform

@Serializable
enum class PlayerType {
    @SerialName("mpv") MPV,
    @SerialName("webview") WEBVIEW
}

@Serializable
data class ServerInfo(
    val id: String,
    val label: String,
    val type: String = "sub_dub",
    val active: Boolean = true,
    val playerType: PlayerType = PlayerType.MPV,
) {
    val supportsDub: Boolean
        get() = type == "sub_dub" || type == "dub"

    val supportsSub: Boolean
        get() = type == "sub_dub" || type == "sub"

    val displayName: String
        get() = label

    val isExtensionServer: Boolean
        get() = id.startsWith("ext:")
}

fun List<ServerInfo>.extensionServers(): List<ServerInfo> = filter { it.isExtensionServer }

fun List<ServerInfo>.catalogServers(): List<ServerInfo> = filterNot { it.isExtensionServer }

fun Set<String>.hidesServer(id: String): Boolean =
    any { it.equals(id, ignoreCase = true) }

/** Collapse expanded Sub/Dub priority ids (`suzu-dub`) to base catalog ids for settings UI. */
fun String.baseServerIdForSettings(): String = when {
    endsWith("-dub", ignoreCase = true) -> dropLast(4)
    endsWith(":dub", ignoreCase = true) -> dropLast(4)
    else -> this
}

fun List<String>.collapseToBaseServerPriority(): List<String> {
    val result = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (id in this) {
        val base = id.baseServerIdForSettings()
        if (base !in seen) {
            result.add(base)
            seen.add(base)
        }
    }
    return result
}

fun Set<String>.isServerVisibleInSettings(server: ServerInfo): Boolean {
    val id = server.id.lowercase()
    if (hidesServer(id)) return false
    if (server.type == "sub_dub" && hidesServer("$id-dub")) return false
    val base = id.baseServerIdForSettings()
    if (base != id && hidesServer(base)) return false
    return true
}

fun List<ServerInfo>.excludingHidden(hiddenIds: Set<String>): List<ServerInfo> =
    if (hiddenIds.isEmpty()) this else filterNot { hiddenIds.hidesServer(it.id) }

fun List<ServerInfo>.excludingUnsupportedPlatformServers(): List<ServerInfo> =
    if (!isDesktopPlatform) this else filterNot { it.playerType == PlayerType.WEBVIEW }

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
    ServerInfo(id = "allmanga", label = "All anime", type = "sub_dub", active = true),
    ServerInfo(id = "suzu", label = "Suzu", type = "sub_dub", active = true),
    ServerInfo(id = "flix", label = "Flix", type = "sub_dub", active = false),
    ServerInfo(id = "anitaku-1", label = "Anitaku 1", type = "sub", active = true),
    ServerInfo(id = "anitaku", label = "Anitaku", type = "sub", active = true),
    ServerInfo(id = "anikage", label = "Anikage", type = "sub_dub", active = true),
    ServerInfo(id = "comti", label = "Comti", type = "sub_dub", active = true),
    ServerInfo(id = "oush", label = "Oush", type = "sub_dub", active = true),
    // WebView / iframe servers
    ServerInfo(id = "megaplay", label = "Megaplay", type = "sub_dub", active = true, playerType = PlayerType.WEBVIEW),
    ServerInfo(id = "anikoto", label = "Aniko", type = "sub_dub", active = true, playerType = PlayerType.WEBVIEW),
    ServerInfo(id = "flix-if", label = "Flix-IF", type = "sub_dub", active = false, playerType = PlayerType.WEBVIEW),
)
