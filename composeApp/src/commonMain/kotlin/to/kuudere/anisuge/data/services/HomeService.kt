package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.HomeData
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

class HomeService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private var cachedHomeData: HomeData? = null

    suspend fun fetchHomeData(forceRefresh: Boolean = false, lang: String? = null): HomeData? {
        if (!forceRefresh && cachedHomeData != null) {
            return cachedHomeData
        }

        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("${ApiConfig.API_BASE}/home") {
                lang?.let { parameter("lang", it) }
                if (stored != null) bearer(stored.token)
            }
            val result: HomeData = response.body()
            // #region agent log
            println("[DEBUG-25ff72] HomeData parsed: trending=${result.trending.size} latestAired=${result.latestAired.size} newOnSite=${result.newOnSite.size}")
            if (result.trending.isNotEmpty()) println("[DEBUG-25ff72] trending[0]: animeId='${result.trending[0].animeId}' activeSlug='${result.trending[0].activeSlug}'")
            if (result.latestAired.isNotEmpty()) println("[DEBUG-25ff72] latestAired[0]: animeId='${result.latestAired[0].animeId}' activeSlug='${result.latestAired[0].activeSlug}'")
            if (result.newOnSite.isNotEmpty()) println("[DEBUG-25ff72] newOnSite[0]: animeId='${result.newOnSite[0].animeId}' activeSlug='${result.newOnSite[0].activeSlug}'")
            // #endregion agent log
            cachedHomeData = result
            result
        } catch (e: Exception) {
            // #region agent log
            println("[DEBUG-25ff72] fetchHomeData EXCEPTION: ${e.message}")
            // #endregion agent log
            println("[HomeService] fetchHomeData error: ${e.message}")
            null
        }
    }
}
