package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.SearchResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

class SearchService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun search(
        q: String? = null,
        limit: Int = 20,
        offset: Int = 0,
        format: String? = null,
        status: String? = null,
        genres: String? = null,
        character: String? = null,
        staff: String? = null,
        studio: String? = null,
        country: String? = null,
        watchlist: String? = null,
        facets: Boolean? = null,
    ): SearchResponse? {
        val stored = sessionStore.get()
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/search") {
                q?.let { parameter("q", it) }
                parameter("limit", limit)
                parameter("offset", offset)
                format?.let { parameter("format", it) }
                status?.let { parameter("status", it) }
                genres?.let { parameter("genres", it) }
                character?.let { parameter("character", it) }
                staff?.let { parameter("staff", it) }
                studio?.let { parameter("studio", it) }
                country?.let { parameter("country", it) }
                watchlist?.let { parameter("watchlist", it) }
                facets?.let { parameter("facets", it) }
                if (stored != null) bearer(stored.token)
            }
            response.body<SearchResponse>()
        } catch (e: Exception) {
            println("[SearchService] search error: ${e.message}")
            null
        }
    }

    suspend fun searchFacets(type: String, q: String? = null): Map<String, List<String>>? {
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/search/facets/$type") {
                q?.let { parameter("q", it) }
            }
            response.body()
        } catch (e: Exception) {
            println("[SearchService] searchFacets error: ${e.message}")
            null
        }
    }
}
