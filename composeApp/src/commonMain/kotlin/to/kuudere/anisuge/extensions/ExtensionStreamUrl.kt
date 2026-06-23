package to.kuudere.anisuge.extensions

import io.ktor.http.decodeURLQueryComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ResolvedExtensionStream(
    val url: String,
    val headers: Map<String, String>,
)

/** Bridge playback URLs (`http://localhost:PORT/m3u8?url=…`) are not fetchable by Ktor after the proxy stops. */
fun resolveBridgeProxyStreamUrl(
    url: String,
    headers: Map<String, String> = emptyMap(),
): ResolvedExtensionStream {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return ResolvedExtensionStream(trimmed, headers)

    val withoutFragment = trimmed.substringBefore('#')
    val schemeEnd = withoutFragment.indexOf("://")
    if (schemeEnd < 0) return ResolvedExtensionStream(trimmed, headers)

    val hostStart = schemeEnd + 3
    val pathStart = withoutFragment.indexOf('/', hostStart).let { if (it < 0) withoutFragment.length else it }
    val host = withoutFragment.substring(hostStart, pathStart).substringBefore(':').lowercase()
    if (host !in BRIDGE_PROXY_HOSTS) return ResolvedExtensionStream(trimmed, headers)

    val path = withoutFragment.substring(pathStart).substringBefore('?').lowercase()
    if (path !in BRIDGE_PROXY_PATHS) return ResolvedExtensionStream(trimmed, headers)

    val query = withoutFragment.substringAfter('?', "")
    if (query.isBlank()) return ResolvedExtensionStream(trimmed, headers)

    val params = parseQueryParams(query)
    val upstream = params["url"]?.decodeURLQueryComponent()?.trim().orEmpty()
    if (upstream.isBlank() || !upstream.startsWith("http", ignoreCase = true)) {
        return ResolvedExtensionStream(trimmed, headers)
    }

    val merged = headers.toMutableMap()
    params["referer"]?.takeIf { it.isNotBlank() }?.let { merged.putIfAbsent("Referer", it) }
    params["origin"]?.takeIf { it.isNotBlank() }?.let { merged.putIfAbsent("Origin", it) }
    params["user-agent"]?.takeIf { it.isNotBlank() }?.let { merged.putIfAbsent("User-Agent", it) }
    params["user_agent"]?.takeIf { it.isNotBlank() }?.let { merged.putIfAbsent("User-Agent", it) }
    decodeEmbeddedHeaderBlob(params["headers"])?.forEach { (key, value) ->
        merged.putIfAbsent(key, value)
    }

    return ResolvedExtensionStream(upstream, merged)
}

fun ExtensionVideo.resolvedForDownload(): ExtensionVideo {
    val resolved = resolveBridgeProxyStreamUrl(url, headers)
    return copy(url = resolved.url, headers = resolved.headers)
}

fun pickExtensionDownloadVideo(
    videos: List<ExtensionVideo>,
    preferredQuality: String? = null,
): ExtensionVideo? {
    if (videos.isEmpty()) return null
    if (!preferredQuality.isNullOrBlank()) {
        videos.firstOrNull { it.quality.equals(preferredQuality, ignoreCase = true) }?.let { return it }
    }
    if (videos.size == 1) return videos.first()
    val qualities = videos.map { Triple(it.quality, it.url, it.headers) }
    val index = preferredDownloadQualityIndex(qualities)
    return videos.getOrNull(index) ?: videos.first()
}

fun preferredDownloadQualityIndex(qualities: List<Triple<String, String, Map<String, String>>>): Int {
    val downloadable = qualities.map { it.first.lowercase() }
    return listOf("8k", "4k", "2160p", "1440p", "1080p", "720p", "480p").firstNotNullOfOrNull { pref ->
        downloadable.indexOfFirst { it.contains(pref) }.takeIf { it >= 0 }
    } ?: run {
        val withReferer = qualities.indexOfFirst { (_, _, headers) ->
            headers["Referer"]?.isNotBlank() == true || headers["referer"]?.isNotBlank() == true
        }.takeIf { it >= 0 }
        withReferer ?: run {
            val pool = qualities.indexOfFirst { (_, url, _) ->
                val effective = resolveBridgeProxyStreamUrl(url).url
                !effective.contains(".m3u8", ignoreCase = true) && !effective.contains(".mpd", ignoreCase = true)
            }
            if (pool >= 0 && pool < qualities.size - 1) pool else 0
        }
    }
}

private val BRIDGE_PROXY_HOSTS = setOf("localhost", "127.0.0.1")
private val BRIDGE_PROXY_PATHS = setOf("/m3u8", "/segment")

private fun parseQueryParams(query: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    query.split('&').forEach { part ->
        if (part.isBlank()) return@forEach
        val eq = part.indexOf('=')
        if (eq < 0) {
            out[part] = ""
        } else {
            val key = part.substring(0, eq)
            val value = part.substring(eq + 1)
            out[key] = value
        }
    }
    return out
}

private fun decodeEmbeddedHeaderBlob(raw: String?): Map<String, String>? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    return runCatching {
        when {
            value.startsWith("{") -> {
                val json = Json { ignoreUnknownKeys = true }
                val obj = json.parseToJsonElement(value).jsonObject
                obj.mapNotNull { (key, element) ->
                    element.jsonPrimitive.contentOrNull?.let { key to it }
                }.toMap()
            }
            else -> null
        }
    }.getOrNull()
}