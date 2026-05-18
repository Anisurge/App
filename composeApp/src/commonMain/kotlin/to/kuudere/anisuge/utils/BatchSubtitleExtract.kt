package to.kuudere.anisuge.utils

import io.ktor.http.decodeURLQueryComponent
import to.kuudere.anisuge.data.models.BatchScrapeStreamData
import to.kuudere.anisuge.data.models.StreamInfo
import to.kuudere.anisuge.data.models.SubtitleData

/**
 * batch_scrape often omits top-level `subtitles` (e.g. Anitaku) but embeds caption URLs in
 * stream query strings: `?caption_1=https://…/file.vtt&sub_1=English`.
 */
object BatchSubtitleExtract {
    fun fromStreamSection(section: BatchScrapeStreamData?): List<SubtitleData> {
        if (section == null) return emptyList()
        val embed = fromEmbedStreams(section.streams)
        if (embed.isNotEmpty()) return embed
        return fromStreams(section.streams, section.subtitles)
    }

    /** Anitaku / similar: captions live on embed URLs (`caption_1=…vtt`), not on vibeplayer HLS. */
    fun fromEmbedStreams(streams: List<StreamInfo>): List<SubtitleData> {
        val embedOnly = streams.filter { stream ->
            val q = stream.url.substringAfter('?', "")
            q.contains("caption_", ignoreCase = true) ||
                stream.quality.orEmpty().contains("embed", ignoreCase = true)
        }
        return fromStreams(embedOnly, legacySubtitlesUrl = null)
    }

    fun fromStreams(
        streams: List<StreamInfo>,
        legacySubtitlesUrl: String? = null,
    ): List<SubtitleData> {
        val results = mutableListOf<SubtitleData>()
        val seen = mutableSetOf<String>()

        fun add(url: String, label: String, default: Boolean) {
            val normalized = url.trim()
            if (normalized.isBlank() || normalized in seen) return
            seen.add(normalized)
            val format = when {
                normalized.contains(".vtt", ignoreCase = true) -> "vtt"
                normalized.contains(".srt", ignoreCase = true) -> "srt"
                else -> "ass"
            }
            results.add(
                SubtitleData(
                    languageName = label,
                    title = label,
                    url = normalized,
                    format = format,
                    is_default = default,
                ),
            )
        }

        val legacy = legacySubtitlesUrl?.trim().orEmpty()
        if (legacy.isNotBlank()) {
            add(legacy, "English", default = true)
        }

        val captionPattern = Regex("""caption_(\d+)=([^&]+)""", RegexOption.IGNORE_CASE)
        val namePattern = Regex("""sub_(\d+)=([^&]+)""", RegexOption.IGNORE_CASE)

        for (stream in streams) {
            val query = stream.url.substringAfter('?', "")
            if (query.isEmpty()) continue

            val captions = captionPattern.findAll(query).associate { match ->
                match.groupValues[1] to decodeQueryValue(match.groupValues[2])
            }
            val names = namePattern.findAll(query).associate { match ->
                match.groupValues[1] to decodeQueryValue(match.groupValues[2])
            }

            captions.forEach { (index, captionUrl) ->
                val label = names[index]?.ifBlank { null } ?: "English"
                add(captionUrl, label, default = results.isEmpty())
            }
        }

        return results
    }

    fun trackUrls(section: BatchScrapeStreamData?): List<Pair<String, String>> =
        fromStreamSection(section).mapNotNull { sub ->
            sub.url?.let { url -> url to (sub.title ?: sub.resolvedLang ?: "Default") }
        }

    private fun decodeQueryValue(raw: String): String =
        runCatching { raw.decodeURLQueryComponent() }.getOrDefault(raw)
}
