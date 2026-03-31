package to.kuudere.anisuge.utils

/**
 * Port of Flutter's SubtitleUtils.dart.
 * Downloads, detects format (ASS/VTT/SRT), converts to styled ASS, and writes to a temp file.
 * Returns an absolute local path, or null on failure.
 */
object SubtitleUtils {

    /**
     * Downloads and converts a subtitle URL to a local temp File.
     * The CALLER is responsible for deleting the file when done.
     * Do NOT use deleteOnExit() here — files must outlive short temp scopes on episode changes.
     */
    fun prepareSubtitleFile(url: String): java.io.File? {
        return try {
            val cleanUrl = url.lowercase().substringBefore('?')
            val ext = if (cleanUrl.endsWith(".sup") || url.lowercase().contains("format=sup")) ".sup" else ".ass"
            val tmp = java.io.File.createTempFile("anisuge_sub_", ext)
            // DO NOT call tmp.deleteOnExit() — caller manages the lifetime

            if (ext == ".sup") {
                if (url.startsWith("file://")) {
                    java.io.File(java.net.URI(url)).copyTo(tmp, overwrite = true)
                } else {
                    val conn = java.net.URL(url).openConnection()
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.getInputStream().use { it.copyTo(tmp.outputStream()) }
                }
                println("[SubtitleUtils] Prepared PGS subtitle → ${tmp.absolutePath}")
                return tmp
            }

            val content = if (url.startsWith("file://")) {
                java.io.File(java.net.URI(url)).readText()
            } else {
                val conn = java.net.URL(url).openConnection()
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.getInputStream().bufferedReader().readText()
            }

            val format = detectFormat(url, content)
            val assContent = when (format) {
                SubtitleFormat.ASS -> content
                SubtitleFormat.SRT -> srtToAss(content)
                SubtitleFormat.VTT -> vttToAss(content)
                SubtitleFormat.PGS -> { tmp.delete(); return null }
                SubtitleFormat.UNKNOWN -> { tmp.delete(); return null }
            }

            tmp.writeText(assContent)
            println("[SubtitleUtils] Prepared subtitle ($format) → ${tmp.absolutePath}")
            tmp
        } catch (e: Exception) {
            println("[SubtitleUtils] Error: ${e.message}")
            null
        }
    }

    /** Convenience wrapper returning the absolute path (legacy callers). */
    fun prepareSubtitle(url: String): String? = prepareSubtitleFile(url)?.absolutePath

    private fun assHeader() = """[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,52,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2.5,1.5,2,80,80,40,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""

    private fun srtToAss(srt: String): String {
        val sb = StringBuilder(assHeader())
        val lines = srt.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.matches(Regex("""\d+"""))) {
                i++
                if (i < lines.size) {
                    val timeLine = lines[i].trim()
                    val m = Regex("""(\d{2}:\d{2}:\d{2},\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2},\d{3})""").find(timeLine)
                    if (m != null) {
                        val start = srtTimeToAss(m.groupValues[1])
                        val end = srtTimeToAss(m.groupValues[2])
                        i++
                        val textLines = mutableListOf<String>()
                        while (i < lines.size && lines[i].trim().isNotEmpty()) {
                            textLines.add(stripHtml(lines[i].trim()))
                            i++
                        }
                        if (textLines.isNotEmpty()) {
                            sb.appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,${textLines.joinToString("\\N")}")
                        }
                    }
                }
            } else {
                i++
            }
        }
        return sb.toString()
    }

    private fun vttToAss(vtt: String): String {
        val sb = StringBuilder(assHeader())
        val lines = vtt.lines()
        var i = 0
        val timingRe = Regex("""(\d{2}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})""")

        while (i < lines.size) {
            val line = lines[i].trim()
            // Skip WEBVTT header/NOTE/STYLE blocks
            if (line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.startsWith("STYLE") || line.startsWith("REGION")) {
                while (i < lines.size && lines[i].trim().isNotEmpty()) i++
                i++
                continue
            }

            var found = false
            for (attempt in 0..1) {
                if (i + attempt >= lines.size) break
                val candidate = lines[i + attempt].trim()
                val m = timingRe.find(candidate)
                if (m != null) {
                    i += attempt + 1
                    val start = vttTimeToAss(m.groupValues[1])
                    val end = vttTimeToAss(m.groupValues[2])
                    val textLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        textLines.add(stripHtml(lines[i].trim()))
                        i++
                    }
                    if (textLines.isNotEmpty()) {
                        sb.appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,${textLines.joinToString("\\N")}")
                    }
                    found = true
                    break
                }
            }
            if (!found) i++
        }
        return sb.toString()
    }

    private fun srtTimeToAss(t: String): String {
        val (hms, msStr) = t.split(",")
        val ms = msStr.toInt()
        val cs = ms / 10
        val parts = hms.split(":")
        return "${parts[0].toInt()}:${parts[1]}:${parts[2]}.${cs.toString().padStart(2, '0')}"
    }

    private fun vttTimeToAss(t: String): String {
        val dotIdx = t.lastIndexOf('.')
        val hmsStr = t.substring(0, dotIdx)
        val ms = t.substring(dotIdx + 1).toInt()
        val cs = ms / 10
        val parts = hmsStr.split(":")
        return when (parts.size) {
            3 -> "${parts[0].toInt()}:${parts[1]}:${parts[2]}.${cs.toString().padStart(2, '0')}"
            else -> "0:${parts[0]}:${parts[1]}.${cs.toString().padStart(2, '0')}"
        }
    }

    private fun stripHtml(text: String): String {
        var t = text
        t = Regex("""<i>(.*?)</i>""", RegexOption.DOT_MATCHES_ALL).replace(t) { "{\\i1}${it.groupValues[1]}{\\i0}" }
        t = Regex("""<b>(.*?)</b>""", RegexOption.DOT_MATCHES_ALL).replace(t) { "{\\b1}${it.groupValues[1]}{\\b0}" }
        t = Regex("""<[^>]+>""").replace(t, "")
        return t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
    }

    private fun detectFormat(url: String, content: String): SubtitleFormat {
        val cleanUrl = url.lowercase().substringBefore('?')
        if (cleanUrl.endsWith(".ass") || cleanUrl.endsWith(".ssa")) return SubtitleFormat.ASS
        if (cleanUrl.endsWith(".vtt")) return SubtitleFormat.VTT
        if (cleanUrl.endsWith(".srt")) return SubtitleFormat.SRT
        if (cleanUrl.endsWith(".sup")) return SubtitleFormat.PGS
        if (content.contains("[Script Info]")) return SubtitleFormat.ASS
        if (content.trimStart().startsWith("WEBVTT")) return SubtitleFormat.VTT
        return SubtitleFormat.SRT
    }

    enum class SubtitleFormat { ASS, VTT, SRT, PGS, UNKNOWN }
}
