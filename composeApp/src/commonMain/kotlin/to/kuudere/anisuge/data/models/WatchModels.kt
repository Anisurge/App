package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    @Serializable(with = FlexibleSubtitleUrlSerializer::class)
    val subtitles: String = "",
    /** Present on some sources (e.g. flix) when the request is invalid or IP is missing. */
    val error: Boolean? = null,
    val message: String? = null,
)

/**
 * batch_scrape historically returned section-level `subtitles` as a single URL string.
 * Newer sources can return an array of subtitle objects; keep the legacy string field
 * tolerant so one provider shape does not reject the whole stream response.
 */
object FlexibleSubtitleUrlSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleSubtitleUrl", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> ""
            is JsonPrimitive -> element.contentOrNull.orEmpty()
            is JsonArray -> element.firstNotNullOfOrNull { item ->
                val obj = item as? JsonObject
                obj?.get("url")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: (item as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            }.orEmpty()
            is JsonObject -> element["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class StreamSubtitle(
    val url: String = "",
    val label: String = "",
)

@Serializable
data class StreamInfo(
    val url: String = "",
    val quality: String? = null,
    val headers: StreamHeaders? = null,
    val subtitles: List<StreamSubtitle> = emptyList(),
    /** Embed/iframe page URL — set when the source returns a player page rather than a direct media URL. */
    val embedUrl: String? = null,
)

@Serializable
data class StreamHeaders(
    val Referer: String? = null,
    @SerialName("User-Agent") val userAgent: String? = null,
    /** Some CDNs (e.g. Wix/static hosts for AllAnime) require `Origin`. */
    @SerialName("Origin") val Origin: String? = null,
)

/** Headers for HTTP fetch (Referer / User-Agent / Origin). */
fun StreamHeaders?.asHttpHeaderMap(): Map<String, String> = buildMap {
    val h = this@asHttpHeaderMap ?: return@buildMap
    h.Referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
    h.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
    h.Origin?.takeIf { it.isNotBlank() }?.let { put("Origin", it) }
}

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
    val headers: Map<String, String>? = null,
    /** Embed/iframe page URL — non-null when the server uses a WebView player instead of direct media. */
    val embedUrl: String? = null,
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
