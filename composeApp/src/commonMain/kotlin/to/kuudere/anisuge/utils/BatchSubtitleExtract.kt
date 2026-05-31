package to.kuudere.anisuge.utils

import io.ktor.http.decodeURLQueryComponent
import to.kuudere.anisuge.data.models.BatchScrapeStreamData
import to.kuudere.anisuge.data.models.StreamInfo
import to.kuudere.anisuge.data.models.StreamSubtitle
import to.kuudere.anisuge.data.models.SubtitleData

/**
 * batch_scrape subtitles: prefer per-stream `subtitles[]` from the scraper API (Anitaku),
 * then embed query captions (`caption_1=…vtt`), then legacy section-level URL string.
 */
object BatchSubtitleExtract {
    fun fromStreamSection(section: BatchScrapeStreamData?): List<SubtitleData> {
        if (section == null) return emptyList()
        val api = mergeStreamApiSubtitles(section.streams)
        if (api.isNotEmpty()) return api
        val embed = fromEmbedStreams(section.streams)
        if (embed.isNotEmpty()) return embed
        return fromStreams(section.streams, section.subtitles)
    }

    fun fromStream(stream: StreamInfo?): List<SubtitleData> {
        if (stream == null) return emptyList()
        val api = stream.subtitles.mapNotNull { it.toSubtitleData() }
        if (api.isNotEmpty()) return api
        return fromStreams(listOf(stream), legacySubtitlesUrl = null)
    }

    fun findStream(
        section: BatchScrapeStreamData?,
        quality: String? = null,
        m3u8Url: String? = null,
    ): StreamInfo? {
        if (section == null) return null
        m3u8Url?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            section.streams.find { it.url == url }?.let { return it }
        }
        quality?.let { q ->
            section.streams.find { (it.quality ?: "Auto") == q }?.let { return it }
        }
        return section.streams.firstOrNull()
    }

    fun forPlayback(
        section: BatchScrapeStreamData?,
        quality: String?,
    ): List<SubtitleData> {
        // Always return ALL subtitles from the section, merged across all streams,
        // so the user sees every available subtitle option regardless of selected quality.
        return fromStreamSection(section)
    }

    fun defaultUrl(subtitles: List<SubtitleData>): String? =
        subtitles.firstOrNull { sub ->
            val name = sub.title ?: sub.resolvedLang ?: ""
            name.equals("english", ignoreCase = true) || name.contains("english", ignoreCase = true)
        }?.url
            ?: subtitles.firstOrNull { it.is_default == true }?.url
            ?: subtitles.firstOrNull()?.url

    fun trackUrls(
        section: BatchScrapeStreamData?,
        quality: String? = null,
        m3u8Url: String? = null,
    ): List<Pair<String, String>> {
        // Always return ALL subtitles from the section, merged across all streams.
        val subs = fromStreamSection(section)
        return subs.mapNotNull { sub ->
            sub.url?.let { url -> url to (sub.title ?: sub.resolvedLang ?: "Default") }
        }
    }

    fun trackUrls(stream: StreamInfo?): List<Pair<String, String>> =
        fromStream(stream).mapNotNull { sub ->
            sub.url?.let { url -> url to (sub.title ?: sub.resolvedLang ?: "Default") }
        }

    /** Anitaku legacy: captions on embed URLs (`caption_1=…vtt`), not on vibeplayer HLS. */
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

    private fun mergeStreamApiSubtitles(streams: List<StreamInfo>): List<SubtitleData> {
        val results = mutableListOf<SubtitleData>()
        val seen = mutableSetOf<String>()
        for (stream in streams) {
            for (sub in fromStream(stream)) {
                val url = sub.url ?: continue
                if (url in seen) continue
                seen.add(url)
                results.add(sub)
            }
        }
        return results
    }

    private fun StreamSubtitle.toSubtitleData(): SubtitleData? {
        val normalized = url.trim()
        if (normalized.isBlank()) return null
        val name = label.ifBlank { "English" }
        val format = when {
            normalized.contains(".vtt", ignoreCase = true) -> "vtt"
            normalized.contains(".srt", ignoreCase = true) -> "srt"
            else -> "ass"
        }
        return SubtitleData(
            languageName = name,
            title = name,
            url = normalized,
            format = format,
            is_default = name.equals("english", ignoreCase = true),
        )
    }

    private fun decodeQueryValue(raw: String): String =
        runCatching { raw.decodeURLQueryComponent() }.getOrDefault(raw)
}
