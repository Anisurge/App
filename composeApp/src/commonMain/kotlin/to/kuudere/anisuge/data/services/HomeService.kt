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
            cachedHomeData = result
            result
        } catch (e: Exception) {
            println("[HomeService] fetchHomeData error: ${e.message}")
            null
        }
    }
}
