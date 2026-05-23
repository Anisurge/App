package to.kuudere.anisuge.utils

/**
 * Normalizes local filesystem paths and builds `file://` URIs that work on Windows and Unix.
 */
object MediaPaths {
    fun normalizeSeparators(path: String): String = path.replace('\\', '/')

    /** RFC 8089-style file URI (`file:///C:/…` on Windows, `file:///home/…` on Unix). */
    fun toFileUri(path: String): String {
        val n = normalizeSeparators(path.trim())
        return when {
            n.matches(Regex("^[A-Za-z]:/.*")) -> "file:///$n"
            n.startsWith("/") -> "file://$n"
            else -> "file://$n"
        }
    }

    fun isAbsoluteLocalPath(path: String): Boolean {
        val n = normalizeSeparators(path)
        return n.startsWith("/") || n.matches(Regex("^[A-Za-z]:/.*"))
    }
}
