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
import to.kuudere.anisuge.data.models.RecommendationsResponse
import to.kuudere.anisuge.data.models.TopAnimeResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
        latestPerAnime: Boolean = false,
    ): ContinueWatchingResponse? {
        val stored = sessionStore.get() ?: return null
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/watch/continue") {
                applyAnisurgeAuth(stored)
                parameter("limit", limit)
                if (offset > 0) parameter("offset", offset)
                if (latestPerAnime) parameter("latestPerAnime", true)
            }
            response.body<ContinueWatchingResponse>()
        } catch (e: Exception) {
            println("[HomeService] fetchContinueWatchingPage error: ${e.message}")
            null
        }
    }

    suspend fun fetchHomeContinueWatching(limit: Int = 20): List<ContinueWatchingItem> =
        fetchContinueWatchingPage(limit = limit, latestPerAnime = true)?.data.orEmpty()

    /** Loads every saved continue row from the BFF (not capped like ReAnime's list). */
    suspend fun fetchAllContinueWatching(): List<ContinueWatchingItem> = kotlinx.coroutines.coroutineScope {
        val pageSize = 100
        val firstPage = fetchContinueWatchingPage(limit = pageSize, offset = 0) ?: return@coroutineScope emptyList<ContinueWatchingItem>()
        val total = firstPage.total
        val all = mutableListOf<ContinueWatchingItem>()
        all.addAll(firstPage.data)
        if (firstPage.data.isEmpty() || all.size >= total || firstPage.data.size < pageSize) {
            return@coroutineScope all
        }

        val remainingCount = total - firstPage.data.size
        val pagesToFetch = (remainingCount + pageSize - 1) / pageSize
        val deferreds = (1..pagesToFetch).map { pageIdx ->
            async {
                fetchContinueWatchingPage(limit = pageSize, offset = pageIdx * pageSize)
            }
        }
        deferreds.forEach { deferred ->
            deferred.await()?.let { page ->
                all.addAll(page.data)
            }
        }
        all
    }

    /**
     * Publishes the newest page immediately, then loads older history only when needed.
     * Home can render from the first callback instead of waiting for every saved episode.
     */
    suspend fun fetchContinueWatchingProgressively(
        onFirstPage: (List<ContinueWatchingItem>) -> Unit,
    ): List<ContinueWatchingItem> = kotlinx.coroutines.coroutineScope {
        val initialPageSize = 30
        val remainingPageSize = 100
        val firstPage = fetchContinueWatchingPage(limit = initialPageSize, offset = 0)
            ?: return@coroutineScope emptyList()
        onFirstPage(firstPage.data)

        if (
            firstPage.data.isEmpty() ||
            firstPage.data.size >= firstPage.total ||
            firstPage.data.size < initialPageSize
        ) {
            return@coroutineScope firstPage.data
        }

        val remainingCount = firstPage.total - firstPage.data.size
        val pagesToFetch = (remainingCount + remainingPageSize - 1) / remainingPageSize
        val olderPages = (0 until pagesToFetch).map { pageIdx ->
            async {
                fetchContinueWatchingPage(
                    limit = remainingPageSize,
                    offset = initialPageSize + pageIdx * remainingPageSize,
                )
                    ?.data
                    .orEmpty()
            }
        }.flatMap { it.await() }

        firstPage.data + olderPages
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

    /** Trending/top anime for a period: `this hour`, `today`, `week`, `month`. */
    suspend fun fetchTopAnime(period: String = "week", limit: Int = 20): TopAnimeResponse? {
        return try {
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/top/anime") {
                parameter("period", period)
                parameter("limit", limit)
            }
            response.body<TopAnimeResponse>()
        } catch (e: Exception) {
            println("[HomeService] fetchTopAnime error: ${e.message}")
            null
        }
    }

    /** Content-based recommendations for a given anime slug/id. */
    suspend fun fetchRecommendations(slug: String): RecommendationsResponse? {
        return try {
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/anime/$slug/recommendations")
            response.body<RecommendationsResponse>()
        } catch (e: Exception) {
            println("[HomeService] fetchRecommendations error: ${e.message}")
            null
        }
    }
}
