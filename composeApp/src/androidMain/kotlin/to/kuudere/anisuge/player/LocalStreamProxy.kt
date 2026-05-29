package to.kuudere.anisuge.player

actual object StreamProxy {
    private val proxy = LocalStreamProxy()

    actual fun proxyUrl(url: String, headers: Map<String, String>?): String {
        // Always route through the local proxy. flixcloud.cc (HOME / zen-1) is now CORS/Origin-based
        // (the owner removed IP-based gating), so the player must send Referer + Origin on every
        // segment request. mpv's direct path does not enforce those headers per-request reliably,
        // whereas the local proxy forwards the full header set on each upstream fetch.
        return proxy.proxyUrl(url, headers)
    }

    actual fun release(url: String) {
        proxy.release(url)
    }
}
