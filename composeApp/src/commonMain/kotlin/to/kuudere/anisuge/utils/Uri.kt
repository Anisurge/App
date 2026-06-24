package to.kuudere.anisuge.utils

object Uri {
    fun parseQueryParam(url: String, key: String): String? {
        return parseQueryParams(url)[key]
    }

    fun parseQueryParams(urlOrLink: String): Map<String, String> {
        val q = urlOrLink.substringAfter("?", "").substringBefore("#")
        if (q.isEmpty()) return emptyMap()
        return q.split("&")
            .filter { it.isNotBlank() }
            .associate { part ->
                val eq = part.indexOf('=')
                if (eq < 0) {
                    part to ""
                } else {
                    val k = part.take(eq)
                    val v = part.drop(eq + 1)
                    k to decodePercent(v)
                }
            }
    }

    /** Minimal percent-decoder safe for KMP (JVM + native). Good for our internal deeplinks. */
    fun decodePercent(encoded: String): String {
        val sb = StringBuilder(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '%' && i + 2 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 3)
                val ch = hex.toIntOrNull(16)?.toChar()
                if (ch != null) {
                    sb.append(ch)
                    i += 3
                    continue
                }
            }
            sb.append(if (c == '+') ' ' else c)
            i++
        }
        return sb.toString()
    }

    /** Basic percent-encoder for query values (safe for KMP). */
    fun encodeForQuery(value: String): String {
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return buildString {
            for (ch in value) {
                if (allowed.indexOf(ch) >= 0) {
                    append(ch)
                } else {
                    val bytes = ch.toString().encodeToByteArray()
                    for (b in bytes) {
                        append('%')
                        append(b.toUByte().toString(16).uppercase().padStart(2, '0'))
                    }
                }
            }
        }
    }
}
