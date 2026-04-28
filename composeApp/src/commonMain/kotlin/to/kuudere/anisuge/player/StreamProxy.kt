package to.kuudere.anisuge.player

expect object StreamProxy {
    fun proxyUrl(url: String, headers: Map<String, String>?): String
    fun release(url: String)
}
