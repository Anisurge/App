package to.kuudere.anisuge.extensions

import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.models.SourceData
import to.kuudere.anisuge.data.models.StreamingData
import to.kuudere.anisuge.data.models.SubtitleData

@Serializable
enum class ExtensionEngine(val displayName: String, val nativeRuntime: Boolean) {
    ANIYOMI("Aniyomi", true),
    CLOUDSTREAM("CloudStream", true),
    MANGAYOMI("Mangayomi", true),
    SORA("Sora", true),
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
    val pkgName: String? = null,
    val isAnime: Boolean = true,
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
    val sortMap: Map<String, String> = emptyMap(),
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
data class ExtensionEpisodeCache(
    val animeId: String,
    val sourceId: String,
    val mappedTitle: String,
    val media: ExtensionMedia,
    val episodes: List<ExtensionEpisode>,
    val updatedAt: Long = 0L,
)

@Serializable
data class ExtensionBackupConfig(
    val acceptedWarning: Boolean = false,
    val repositories: List<ExtensionRepository> = emptyList(),
    val installedSourceIds: List<String> = emptyList(),
    val sourceOrder: List<String> = emptyList(),
    val mappings: List<ExtensionMapping> = emptyList(),
    val episodeCaches: List<ExtensionEpisodeCache> = emptyList(),
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

data class ExtensionPrefetchResult(
    val source: ExtensionSource,
    val media: ExtensionMedia,
    val episodes: List<ExtensionEpisode>,
    val mappedTitle: String,
)

data class ExtensionStreamResult(
    val source: ExtensionSource,
    val media: ExtensionMedia,
    val episode: ExtensionEpisode,
    val videos: List<ExtensionVideo>,
) {
    fun toStreamingData(selectedVideo: ExtensionVideo? = null): StreamingData {
        val playable = selectedVideo?.let { listOf(it) } ?: videos
        return StreamingData(
            sources = playable.map {
                SourceData(quality = it.quality, url = it.url, headers = it.headers)
            },
            subtitles = playable.flatMap { it.subtitles }
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
}

/** Selectable watch/settings id — uses bridge runtime id and `-dub` suffix (catalog server parity). */
fun ExtensionSource.selectableServerId(dub: Boolean = false): String {
    val base = "ext:${engine.name.lowercase()}:${bridgeSourceId()}"
    return if (dub) "$base-dub" else base
}

fun ExtensionSource.serverId(dub: Boolean = false): String = selectableServerId(dub)

/**
 * AnymeX treats `lang` as the extension's catalog/subtitle language — not player dub.
 * Only explicit dub extensions (name/lang contains "dub") are dub-only sources.
 */
fun extensionVariantIsExplicitDub(
    source: ExtensionSource,
    siblings: List<ExtensionSource> = listOf(source),
): Boolean {
    val lang = source.language.lowercase()
    val name = source.name.lowercase()
    if (Regex("""\bdub\b""").containsMatchIn(name)) return true
    if (Regex("""\bdub\b""").containsMatchIn(lang)) return true
    return false
}

fun extensionEpisodeLooksDub(episode: ExtensionEpisode): Boolean {
    val blob = buildString {
        append(episode.name.lowercase())
        append(' ')
        append(episode.url.lowercase())
        episode.sortMap.forEach { (key, value) ->
            append(' ')
            append(key.lowercase())
            append(' ')
            append(value.lowercase())
        }
    }
    return Regex("""\bdub\b""").containsMatchIn(blob)
}

fun extensionLanguageLabel(code: String): String = when (code.lowercase()) {
    "all" -> "All"
    "en", "en-us" -> "English"
    "de" -> "German"
    "fr" -> "French"
    "es", "es-419" -> "Spanish"
    "pt", "pt-br" -> "Portuguese"
    "it" -> "Italian"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "zh", "zh-cn", "zh-tw" -> "Chinese"
    else -> code.uppercase()
}

fun buildExtensionServerLabel(source: ExtensionSource, siblings: List<ExtensionSource>): String {
    val langSuffix = if (siblings.size > 1) " · ${extensionLanguageLabel(source.language)}" else ""
    return "[EXT] ${source.name}$langSuffix · ${source.engine.displayName}"
}

/** Numeric bridge id for Aniyomi/Mangayomi APK sources; provider name for CloudStream. */
fun ExtensionSource.bridgeSourceId(): String = runtimeId?.takeIf { it.isNotBlank() } ?: id

fun ExtensionSource.hasNumericBridgeId(): Boolean =
    bridgeSourceId().all { it.isDigit() }

/** Android bridge methods take source id as [String] (AnymeX parity). */
fun ExtensionSource.bridgeSourceIdArg(): String = bridgeSourceId()

fun ExtensionSource.isBridgeCapableOnPlatform(): Boolean {
    if (!engine.nativeRuntime || !installed) return false
    if (!ExtensionPlatformFiles.isAndroid) return true
    return when (engine) {
        ExtensionEngine.ANIYOMI, ExtensionEngine.CLOUDSTREAM -> true
        ExtensionEngine.MANGAYOMI, ExtensionEngine.SORA -> {
            val ext = artifactExtension()
            ext == "apk" || ext == "js" || ext == "dart"
        }
    }
}

fun ExtensionSource.uninstallKey(): String = pkgName?.takeIf { it.isNotBlank() } ?: id

fun ExtensionSource.artifactExtension(): String {
    val url = downloadUrl?.substringBefore('?').orEmpty().lowercase()
    return when {
        url.endsWith(".apk") -> "apk"
        url.endsWith(".js") -> "js"
        url.endsWith(".dart") -> "dart"
        url.endsWith(".cs3") -> "cs3"
        engine == ExtensionEngine.CLOUDSTREAM -> "cs3"
        engine == ExtensionEngine.MANGAYOMI -> "dart"
        else -> if (ExtensionPlatformFiles.isAndroid) "apk" else "jar"
    }
}

/** Bridge `exts/` vs `exts_manga/` — Mangayomi scripts are always manga-side (AnymeX parity). */
fun ExtensionSource.installUsesAnimeDir(): Boolean = when (engine) {
    ExtensionEngine.MANGAYOMI -> artifactExtension() == "apk" && isAnime
    else -> isAnime
}

fun ExtensionSource.bridgeStorageDir(): String =
    if (installUsesAnimeDir()) "exts" else "exts_manga"

/** Stable per bridge source (AnymeX uses numeric source id, not pkg alone). */
fun compositeSourceId(source: ExtensionSource): String =
    "${source.engine.name}:${source.runtimeId?.takeIf { it.isNotBlank() } ?: source.id}"

data class ParsedExtensionServerId(
    val engine: ExtensionEngine,
    val sourceId: String,
    val dub: Boolean,
)

fun parseExtensionServerId(value: String): ParsedExtensionServerId? {
    if (!value.startsWith("ext:")) return null
    val dub = value.endsWith("-dub", ignoreCase = true) || value.endsWith(":dub", ignoreCase = true)
    val normalized = value
        .removeSuffix("-dub")
        .removeSuffix("-DUB")
        .removeSuffix(":dub")
        .removeSuffix(":DUB")
    val parts = normalized.split(":")
    if (parts.size < 3) return null
    val engine = ExtensionEngine.entries.firstOrNull {
        it.name.equals(parts[1], ignoreCase = true)
    } ?: return null
    return ParsedExtensionServerId(
        engine = engine,
        sourceId = parts.drop(2).joinToString(":"),
        dub = dub,
    )
}

fun isDubSelectableServerId(id: String): Boolean =
    id.endsWith("-dub", ignoreCase = true) || id.endsWith(":dub", ignoreCase = true)

fun findExtensionEpisode(
    episodes: List<ExtensionEpisode>,
    episodeNumber: Int,
    preferDub: Boolean = false,
): ExtensionEpisode? {
    if (episodes.isEmpty()) return null

    fun matchesNumber(episode: ExtensionEpisode): Boolean {
        if (kotlin.math.abs(episode.number - episodeNumber) < 0.01) return true
        if (episode.number.toInt() == episodeNumber) return true
        val token = episodeNumber.toString()
        return episode.name.contains(Regex("""\b${Regex.escape(token)}\b"""))
    }

    val candidates = episodes.filter(::matchesNumber)
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()

    val dubEpisodes = candidates.filter(::extensionEpisodeLooksDub)
    val subEpisodes = candidates.filterNot(::extensionEpisodeLooksDub)
    return when {
        preferDub && dubEpisodes.isNotEmpty() -> dubEpisodes.first()
        !preferDub && subEpisodes.isNotEmpty() -> subEpisodes.first()
        preferDub -> candidates.first()
        else -> subEpisodes.firstOrNull() ?: candidates.first()
    }
}
