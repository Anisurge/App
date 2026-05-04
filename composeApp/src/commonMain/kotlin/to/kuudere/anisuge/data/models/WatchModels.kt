package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchServerResponse(
    val success: Boolean = false,
    val data: StreamingData? = null,
    val directLink: DirectLinkWrapper? = null
)

@Serializable
data class DirectLinkWrapper(
    val data: StreamingData? = null
)

@Serializable
data class StreamingData(
    val file_id: String? = null,
    val file_code: String? = null,
    val download_url: String? = null,
    val m3u8_url: String? = null,
    val subtitles: List<SubtitleData>? = null,
    val fonts: List<FontData>? = null,
    val sources: List<SourceData>? = null,
    val intro: SkipData? = null,
    val outro: SkipData? = null,
    val chapters: List<ChapterData>? = null,
    val headers: Map<String, String>? = null
)

@Serializable
data class SubtitleData(
    // zen server uses "language"/"language_name", other servers may use "lang"
    val lang: String? = null,
    val language: String? = null,
    @SerialName("language_name") val languageName: String? = null,
    val url: String? = null,
    @SerialName("default") val is_default: Boolean? = false,
    val title: String? = null,
    val format: String? = null,
) {
    // Resolved language label regardless of which field is populated
    val resolvedLang: String? get() = languageName ?: language ?: lang
}

@Serializable
data class FontData(
    val name: String? = null,
    val url: String? = null
)

@Serializable
data class SourceData(
    val quality: String? = null,
    val url: String? = null
)

@Serializable
data class SenshiSourceData(
    val url: String? = null,
    val status: String? = null
)

@Serializable
data class SkipData(
    val start: Double? = null,
    val end: Double? = null
)

@Serializable
data class ChapterData(
    val title: String? = null,
    @SerialName("start") val start: Double? = null,
    @SerialName("end") val end: Double? = null,
    val start_time: Double? = null,
    val end_time: Double? = null
) {
    val resolvedStart: Double? get() = start ?: start_time
    val resolvedEnd: Double? get() = end ?: end_time
}

// ── Fetch API response models (fetch.anisurge.lol) ──────────────────────

@Serializable
data class FetchStreamResponse(
    val sub: FetchStreamSection? = null,
    val dub: FetchStreamSection? = null
)

@Serializable
data class FetchStreamSection(
    val providerId: String? = null,
    val episodeId: String? = null,
    val streams: List<FetchStreamItem> = emptyList(),
    val subtitles: String = "",
    val notFound: Boolean = false
)

@Serializable
data class FetchStreamItem(
    val url: String? = null,
    val quality: String? = null,
    val headers: Map<String, String>? = null
)
