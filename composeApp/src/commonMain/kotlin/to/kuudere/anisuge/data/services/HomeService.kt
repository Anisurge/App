package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.ContinueWatchingItem
import to.kuudere.anisuge.data.models.ContinueWatchingResponse
import to.kuudere.anisuge.data.models.HomeData
import to.kuudere.anisuge.data.models.LatestAiredResponse

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
            val response = httpClient.get("${AppComponent.BASE_URL}/home") {
                lang?.let { parameter("lang", it) }
                if (stored != null) header("Authorization", "Bearer ${stored.token}")
            }
            val result: HomeData = response.body()
            cachedHomeData = result
            result
        } catch (e: Exception) {
            println("[HomeService] fetchHomeData error: ${e.message}")
            null
        }
    }

    suspend fun fetchContinueWatching(
        limit: Int = 20,
        offset: Int = 0
    ): List<ContinueWatchingItem> {
        val stored = sessionStore.get() ?: return emptyList()
        return try {
            val response = httpClient.get("${AppComponent.BASE_URL}/watch/continue") {
                header("Authorization", "Bearer ${stored.token}")
                parameter("limit", limit)
                if (offset > 0) parameter("offset", offset)
            }
            val result: ContinueWatchingResponse = response.body()
            result.data
        } catch (e: Exception) {
            println("[HomeService] fetchContinueWatching error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchLatestAired(
        lang: String? = null,
        limit: Int = 12,
        cursor: String? = null
    ): LatestAiredResponse? {
        return try {
            val response = httpClient.get("${AppComponent.BASE_URL}/home/latest-aired") {
                lang?.let { parameter("lang", it) }
                parameter("limit", limit)
                cursor?.let { parameter("cursor", it) }
            }
            response.body()
        } catch (e: Exception) {
            println("[HomeService] fetchLatestAired error: ${e.message}")
            null
        }
    }
}
