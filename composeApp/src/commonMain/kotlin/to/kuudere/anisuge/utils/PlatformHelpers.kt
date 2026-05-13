package to.kuudere.anisuge.utils

import kotlinx.datetime.Clock

/** Cross-platform epoch millis. */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Cross-platform float formatting (replaces String.format). */
fun formatFloat(value: Double, decimals: Int): String {
    var factor = 1
    repeat(decimals) { factor *= 10 }
    val scaled = (value * factor).toInt()
    val whole = scaled / factor
    val frac = kotlin.math.abs(scaled % factor)
    val fracStr = frac.toString().padStart(decimals, '0')
    return "$whole.${fracStr}"
}

/** Cross-platform file size formatting. */
fun formatFileSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    val kb = bytes / 1024.0
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${formatFloat(gb, 2)} GB"
        bytes >= 1024 * 1024 -> "${formatFloat(mb, 2)} MB"
        bytes >= 1024 -> "${formatFloat(kb, 2)} KB"
        else -> "$bytes B"
    }
}

fun formatFileSizeCompact(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    val kb = bytes / 1024.0
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${formatFloat(gb, 1)} GB"
        bytes >= 1024 * 1024 -> "${formatFloat(mb, 1)} MB"
        bytes >= 1024 -> "${formatFloat(kb, 1)} KB"
        else -> "$bytes B"
    }
}

/** Extract host from a URL string without java.net.URI. */
fun urlHost(url: String): String? {
    val afterScheme = url.substringAfter("://", "")
    if (afterScheme == url) return null
    return afterScheme.substringBefore("/").substringBefore(":")
}

/** Extract scheme from a URL string. */
fun urlScheme(url: String): String? {
    val idx = url.indexOf("://")
    if (idx <= 0) return null
    return url.substring(0, idx)
}

/** Build a scheme+host string from a URL. */
fun urlSchemeHost(url: String): String {
    val scheme = urlScheme(url) ?: "https"
    val host = urlHost(url)
    return if (host != null) "$scheme://$host" else ""
}
