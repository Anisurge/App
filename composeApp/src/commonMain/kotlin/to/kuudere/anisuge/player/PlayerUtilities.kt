package to.kuudere.anisuge.player

import kotlinx.serialization.Serializable

@Serializable
data class PlayerUtilitySettings(
    val subtitleDelaySeconds: Double = 0.0,
    val audioDelaySeconds: Double = 0.0,
    val doubleTapSeekSeconds: Int = 10,
    val playbackBufferMb: Int = 0,
    val subtitleFont: String = "sans-serif",
    val subtitleColor: String = "#FFFFFF",
    val subtitleOutlineColor: String = "#000000",
    val subtitleOutlineWidth: Int = 3,
    val subtitleBackgroundColor: String = "#000000",
    val subtitleBackgroundOpacity: Int = 0,
    val subtitleOpacity: Int = 100,
    val subtitleBottomMargin: Int = 5,
) {
    fun sanitized(): PlayerUtilitySettings = copy(
        subtitleDelaySeconds = subtitleDelaySeconds.coerceIn(-30.0, 30.0),
        audioDelaySeconds = audioDelaySeconds.coerceIn(-30.0, 30.0),
        doubleTapSeekSeconds = doubleTapSeekSeconds.takeIf { it in seekDurations } ?: 10,
        playbackBufferMb = playbackBufferMb.takeIf { it in bufferSizesMb } ?: 0,
        subtitleFont = subtitleFont.takeIf { it in fonts } ?: "sans-serif",
        subtitleColor = subtitleColor.normalizedColor("#FFFFFF"),
        subtitleOutlineColor = subtitleOutlineColor.normalizedColor("#000000"),
        subtitleOutlineWidth = subtitleOutlineWidth.coerceIn(0, 10),
        subtitleBackgroundColor = subtitleBackgroundColor.normalizedColor("#000000"),
        subtitleBackgroundOpacity = subtitleBackgroundOpacity.coerceIn(0, 100),
        subtitleOpacity = subtitleOpacity.coerceIn(10, 100),
        subtitleBottomMargin = subtitleBottomMargin.coerceIn(0, 25),
    )

    fun usesCustomSubtitleStyle(): Boolean = sanitized().let {
        it.subtitleFont != DEFAULT.subtitleFont ||
            it.subtitleColor != DEFAULT.subtitleColor ||
            it.subtitleOutlineColor != DEFAULT.subtitleOutlineColor ||
            it.subtitleOutlineWidth != DEFAULT.subtitleOutlineWidth ||
            it.subtitleBackgroundColor != DEFAULT.subtitleBackgroundColor ||
            it.subtitleBackgroundOpacity != DEFAULT.subtitleBackgroundOpacity ||
            it.subtitleOpacity != DEFAULT.subtitleOpacity ||
            it.subtitleBottomMargin != DEFAULT.subtitleBottomMargin
    }

    companion object {
        val DEFAULT = PlayerUtilitySettings()
        val seekDurations = listOf(5, 10, 15, 20, 30)
        val bufferSizesMb = listOf(0, 64, 128, 256, 512)
        fun bufferLabel(value: Int): String =
            if (value == 0) "Automatic" else "$value MB"
        val fonts = listOf("sans-serif", "Roboto", "Noto Sans", "Arial")
        val colors = listOf(
            "#FFFFFF" to "White",
            "#FFF176" to "Yellow",
            "#80DEEA" to "Cyan",
            "#FFAB91" to "Peach",
            "#CE93D8" to "Lavender",
            "#000000" to "Black",
        )
    }
}

private fun String.normalizedColor(fallback: String): String {
    val value = uppercase()
    return if (Regex("^#[0-9A-F]{6}$").matches(value)) value else fallback
}

private fun opacityHex(opacity: Int): String =
    ((opacity.coerceIn(0, 100) * 255) / 100).toString(16).padStart(2, '0').uppercase()

fun PlayerUtilitySettings.mpvProperties(defaultBufferMb: Int): Map<String, String> {
    val safe = sanitized()
    val bufferMb = safe.playbackBufferMb.takeIf { it > 0 }
        ?: defaultBufferMb.coerceAtLeast(64)
    return linkedMapOf(
        "sub-delay" to safe.subtitleDelaySeconds.toString(),
        "audio-delay" to safe.audioDelaySeconds.toString(),
        "sub-font" to safe.subtitleFont,
        "sub-color" to "#${opacityHex(safe.subtitleOpacity)}${safe.subtitleColor.removePrefix("#")}",
        "sub-border-color" to safe.subtitleOutlineColor,
        "sub-border-size" to safe.subtitleOutlineWidth.toString(),
        "sub-back-color" to "#${opacityHex(safe.subtitleBackgroundOpacity)}${safe.subtitleBackgroundColor.removePrefix("#")}",
        "sub-pos" to (100 - safe.subtitleBottomMargin).coerceIn(50, 100).toString(),
        "sub-ass-override" to if (safe.usesCustomSubtitleStyle()) "force" else "scale",
        "demuxer-max-bytes" to "${bufferMb}M",
        "demuxer-max-back-bytes" to "${(bufferMb / 2).coerceAtLeast(32)}M",
    )
}

fun screenshotFileName(
    animeTitle: String,
    episodeNumber: Int,
    playbackSeconds: Double,
): String {
    val safeTitle = animeTitle
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
        .replace(Regex("\\s+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-', '.')
        .take(80)
        .ifBlank { "Anime" }
    val totalSeconds = playbackSeconds.toLong().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val position = "${hours.toString().padStart(2, '0')}h" +
        "${minutes.toString().padStart(2, '0')}m" +
        "${seconds.toString().padStart(2, '0')}s"
    return "$safeTitle-EP${episodeNumber.coerceAtLeast(1)}-$position.png"
}
