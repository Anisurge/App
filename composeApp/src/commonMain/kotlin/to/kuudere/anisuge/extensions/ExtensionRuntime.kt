package to.kuudere.anisuge.extensions

import kotlinx.coroutines.flow.StateFlow

expect class ExtensionRuntime() {
    val state: StateFlow<ExtensionRuntimeState>
    suspend fun install(force: Boolean = false)
    suspend fun remove()
    suspend fun installExtension(path: String, isAnime: Boolean = true): Boolean
    suspend fun uninstallExtension(packageName: String, isAnime: Boolean = true): Boolean
    suspend fun invalidateInstalledSourcesCache()
    suspend fun loadInstalledSources(paths: List<String>): List<ExtensionSource>
    suspend fun search(source: ExtensionSource, query: String): List<ExtensionMedia>
    suspend fun details(source: ExtensionSource, media: ExtensionMedia): Pair<ExtensionMedia, List<ExtensionEpisode>>
    suspend fun videos(source: ExtensionSource, episode: ExtensionEpisode): List<ExtensionVideo>
    suspend fun videosStream(
        source: ExtensionSource,
        episode: ExtensionEpisode,
        onVideo: (suspend (ExtensionVideo) -> Unit)? = null,
    ): List<ExtensionVideo>
    suspend fun cancel(token: String)
    fun logs(): List<String>

    // Cookie injection for Cloudflare bypass (like AnymeX)
    suspend fun setCookies(url: String, cookieString: String)
    suspend fun setUserAgent(url: String, userAgent: String)
    suspend fun getCookies(url: String): String?

    // Open Cloudflare bypass WebView (like AnymeX)
    fun openCloudflareBypass(url: String)
}
