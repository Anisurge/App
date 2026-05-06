package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BatchScrapeResponse(
    val sub: BatchScrapeStreamData? = null,
    val dub: BatchScrapeStreamData? = null,
)

@Serializable
data class BatchScrapeStreamData(
    @SerialName("providerId") val providerId: String? = null,
    @SerialName("episodeId") val episodeId: String? = null,
    val streams: List<StreamInfo> = emptyList(),
    val subtitles: String = "",
)

@Serializable
data class StreamInfo(
    val url: String = "",
    val quality: String? = null,
    val headers: StreamHeaders? = null,
)

@Serializable
data class StreamHeaders(
    val Referer: String? = null,
    @SerialName("User-Agent") val userAgent: String? = null,
)

@Serializable
data class SuzuEmbedStream(
    val url: String = "",
    val status: String? = null,
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
    val lang: String? = null,
    val language: String? = null,
    @SerialName("language_name") val languageName: String? = null,
    val url: String? = null,
    @SerialName("default") val is_default: Boolean? = false,
    val title: String? = null,
    val format: String? = null,
) {
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
    val url: String? = null,
    val headers: Map<String, String>? = null,
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
