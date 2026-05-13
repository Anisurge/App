package to.kuudere.anisuge.utils

/** Pad a number to two digits. */
fun padTwo(n: Long): String = if (n < 10) "0$n" else "$n"
fun padTwo(n: Int): String = if (n < 10) "0$n" else "$n"

/** Format duration as HH:MM:SS or MM:SS. */
fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong().coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "$h:${padTwo(m)}:${padTwo(s)}"
    } else {
        "${padTwo(m)}:${padTwo(s)}"
    }
}

/** Format duration as H:MM:SS or M:SS (no leading zero on first group). */
fun formatDurationCompact(seconds: Double): String {
    val totalSeconds = seconds.toInt()
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (mins >= 60) {
        val hrs = mins / 60
        val remainingMins = mins % 60
        "$hrs:${padTwo(remainingMins)}:${padTwo(secs)}"
    } else {
        "$mins:${padTwo(secs)}"
    }
}

/** Format download speed. */
fun formatSpeed(bytesPerSec: Double): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> "${formatFloat(bytesPerSec / (1024 * 1024), 1)} MB/s"
        bytesPerSec >= 1024 -> "${formatFloat(bytesPerSec / 1024, 1)} KB/s"
        else -> "${formatFloat(bytesPerSec, 0)} B/s"
    }
}

/** Format ETA. */
fun formatEta(seconds: Int): String {
    return when {
        seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds}s left"
    }
}

/** Format bytes with one decimal. */
fun formatBytesOneDecimal(bytes: Long): String {
    return when {
        bytes <= 0L -> "—"
        bytes >= 1024L * 1024 * 1024 -> "${formatFloat(bytes.toDouble() / (1024 * 1024 * 1024), 1)} GB"
        bytes >= 1024L * 1024 -> "${formatFloat(bytes.toDouble() / (1024 * 1024), 1)} MB"
        else -> "${formatFloat(bytes.toDouble() / 1024, 0)} KB"
    }
}

/** Format file size for dialogs. */
fun formatFileSizeDialog(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${formatFloat(bytes.toDouble() / (1024 * 1024 * 1024), 1)} GB"
        bytes >= 1024 * 1024 -> "${formatFloat(bytes.toDouble() / (1024 * 1024), 0)} MB"
        else -> "${formatFloat(bytes.toDouble() / 1024, 0)} KB"
    }
}
