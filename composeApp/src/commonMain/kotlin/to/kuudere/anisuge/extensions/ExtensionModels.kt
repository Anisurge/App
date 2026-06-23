package to.kuudere.anisuge.extensions

import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.models.SourceData
import to.kuudere.anisuge.data.models.StreamingData
import to.kuudere.anisuge.data.models.SubtitleData

@Serializable
enum class ExtensionEngine(val displayName: String, val nativeRuntime: Boolean) {
    ANIYOMI("Aniyomi", true),
    CLOUDSTREAM("CloudStream", true),
    MANGAYOMI("Mangayomi", false),
    SORA("Sora", false),
}

@Serializable
data class ExtensionRepository(
    val url: String,
    val engine: ExtensionEngine,
    val name: String? = null,
)

@Serializable
data class ExtensionSource(
    val id: String,
    val name: String,
    val engine: ExtensionEngine,
    val language: String = "all",
    val version: String = "",
    val latestVersion: String = "",
    val iconUrl: String? = null,
    val downloadUrl: String? = null,
    val repositoryUrl: String = "",
    val installedPath: String? = null,
    val runtimeId: String? = null,
    val installed: Boolean = false,
    val hasUpdate: Boolean = false,
    val isNsfw: Boolean = false,
)

@Serializable
data class ExtensionMedia(
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
)

@Serializable
data class ExtensionEpisode(
    val name: String,
    val url: String,
    val number: Double = 0.0,
)

@Serializable
data class ExtensionVideo(
    val url: String,
    val quality: String = "Auto",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<ExtensionSubtitle> = emptyList(),
)

@Serializable
data class ExtensionSubtitle(
    val url: String,
    val label: String = "Default",
    val language: String? = null,
)

@Serializable
data class ExtensionMapping(
    val animeId: String,
    val sourceId: String,
    val media: ExtensionMedia,
    val confidence: Double,
)

@Serializable
data class ExtensionBackupConfig(
    val acceptedWarning: Boolean = false,
    val repositories: List<ExtensionRepository> = emptyList(),
    val installedSourceIds: List<String> = emptyList(),
    val sourceOrder: List<String> = emptyList(),
    val mappings: List<ExtensionMapping> = emptyList(),
)

data class ExtensionRuntimeState(
    val installed: Boolean = false,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val version: String? = null,
    val status: String = "Not installed",
    val error: String? = null,
)

data class PendingExtensionMatch(
    val requestId: String,
    val sourceName: String,
    val animeTitle: String,
    val candidates: List<Pair<ExtensionMedia, Double>>,
)

data class ExtensionStreamResult(
    val source: ExtensionSource,
    val media: ExtensionMedia,
    val episode: ExtensionEpisode,
    val videos: List<ExtensionVideo>,
) {
    fun toStreamingData(): StreamingData = StreamingData(
        sources = videos.map {
            SourceData(quality = it.quality, url = it.url, headers = it.headers)
        },
        subtitles = videos.flatMap { it.subtitles }
            .distinctBy { it.url }
            .map {
                SubtitleData(
                    languageName = it.label,
                    language = it.language,
                    url = it.url,
                    format = it.url.substringBefore("?").substringAfterLast(".", ""),
                )
            },
    )
}

fun ExtensionSource.serverId(dub: Boolean = false): String =
    "ext:${engine.name.lowercase()}:$id${if (dub) ":dub" else ""}"

data class ParsedExtensionServerId(
    val engine: ExtensionEngine,
    val sourceId: String,
    val dub: Boolean,
)

fun parseExtensionServerId(value: String): ParsedExtensionServerId? {
    if (!value.startsWith("ext:")) return null
    val normalized = value.removeSuffix("-dub")
    val parts = normalized.split(":")
    if (parts.size < 3) return null
    val engine = ExtensionEngine.entries.firstOrNull {
        it.name.equals(parts[1], ignoreCase = true)
    } ?: return null
    return ParsedExtensionServerId(
        engine = engine,
        sourceId = parts.drop(2).dropLast(if (parts.last() == "dub") 1 else 0).joinToString(":"),
        dub = value.endsWith("-dub") || parts.last() == "dub",
    )
}
