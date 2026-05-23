package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth
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
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/home") {
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

    suspend fun fetchContinueWatchingPage(
        limit: Int = 100,
        offset: Int = 0,
    ): ContinueWatchingResponse? {
        val stored = sessionStore.get() ?: return null
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/watch/continue") {
                applyAnisurgeAuth(stored)
                parameter("limit", limit)
                if (offset > 0) parameter("offset", offset)
            }
            response.body<ContinueWatchingResponse>()
        } catch (e: Exception) {
            println("[HomeService] fetchContinueWatchingPage error: ${e.message}")
            null
        }
    }

    /** Loads every saved continue row from the BFF (not capped like ReAnime's list). */
    suspend fun fetchAllContinueWatching(): List<ContinueWatchingItem> {
        val pageSize = 100
        var offset = 0
        val all = mutableListOf<ContinueWatchingItem>()
        while (true) {
            val page = fetchContinueWatchingPage(limit = pageSize, offset = offset) ?: break
            all.addAll(page.data)
            if (page.data.isEmpty() || all.size >= page.total) break
            offset += page.data.size
            if (page.data.size < pageSize) break
        }
        return all
    }

    suspend fun deleteContinueWatching(animeId: String, episodeId: String): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            httpClient.delete("${AnisurgeApi.v1Base}/watch/continue/$animeId/$episodeId") {
                applyAnisurgeAuth(stored)
            }
            true
        } catch (e: Exception) {
            println("[HomeService] deleteContinueWatching error: ${e.message}")
            false
        }
    }

    suspend fun fetchLatestAired(
        lang: String? = null,
        limit: Int = 12,
        cursor: String? = null
    ): LatestAiredResponse? {
        return try {
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/home/latest-aired") {
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
