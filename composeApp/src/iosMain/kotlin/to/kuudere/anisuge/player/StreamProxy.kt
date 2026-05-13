package to.kuudere.anisuge.player

actual object StreamProxy {
    actual fun proxyUrl(url: String, headers: Map<String, String>?): String = url
    actual fun release(url: String) {}
}
