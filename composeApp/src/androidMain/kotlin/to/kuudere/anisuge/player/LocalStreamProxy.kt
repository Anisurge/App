package to.kuudere.anisuge.player

actual object StreamProxy {
    private val proxy = LocalStreamProxy()

    actual fun proxyUrl(url: String, headers: Map<String, String>?): String = proxy.proxyUrl(url, headers)

    actual fun release(url: String) {
        proxy.release(url)
    }
}
