package to.kuudere.anisuge.ui

import to.kuudere.anisuge.AppComponent

/** Resolve profile / custom pfp URLs from the Anisurge BFF or absolute CDN links. */
fun resolveProfileMediaUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    if (trimmed.startsWith("//")) {
        return "https:$trimmed"
    }
    val base = AppComponent.ANISURGE_API_URL.trimEnd('/')
        .removeSuffix("/v1")
        .ifBlank { "https://db.anisurge.qzz.io" }
    return "$base${if (trimmed.startsWith("/")) trimmed else "/$trimmed"}"
}

fun isProfileVideoUrl(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".mp4") ||
        path.endsWith(".webm") ||
        path.endsWith(".m4v") ||
        ".mp4/" in path ||
        "/video/" in path
}

fun isProfileGifUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".gif")
}

fun isProfileApngUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".apng")
}

fun isAnimatedProfileOverlayUrl(url: String): Boolean =
    isProfileGifUrl(url) || isProfileApngUrl(url)
