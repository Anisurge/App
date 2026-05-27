package to.kuudere.anisuge.player

import java.net.URI

actual object StreamProxy {
    private val proxy = LocalStreamProxy()

    actual fun proxyUrl(url: String, headers: Map<String, String>?): String {
        if (canUsePlayerHeadersDirectly(url)) return url
        return proxy.proxyUrl(url, headers)
    }

    actual fun release(url: String) {
        proxy.release(url)
    }

    private fun canUsePlayerHeadersDirectly(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "fetch.flixcloud.cc" || host.endsWith(".flixcloud.cc")
    }
}
