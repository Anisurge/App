package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer
import kotlinx.serialization.Serializable

@Serializable
data class PublicProfile(
    val username: String = "",
    val displayName: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val joinDate: String? = null,
    val isPrivate: Boolean = false,
)

@Serializable
data class PublicWatchlistResponse(
    val data: List<AnimeItem> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
)

class PublicService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getPublicProfile(username: String): PublicProfile? {
        val name = username.removePrefix("@")
        return try {
            httpClient.get("${ApiConfig.API_BASE}/public/$name").body()
        } catch (e: Exception) {
            println("[PublicService] getPublicProfile error: ${e.message}")
            null
        }
    }

    suspend fun getPublicWatchlist(
        username: String,
        q: String? = null,
        folder: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): PublicWatchlistResponse? {
        val name = username.removePrefix("@")
        val stored = sessionStore.get()
        return try {
            httpClient.get("${ApiConfig.API_BASE}/public/$name/watchlist") {
                if (stored != null) bearer(stored.token)
                q?.let { parameter("q", it) }
                folder?.let { parameter("folder", it) }
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()
        } catch (e: Exception) {
            println("[PublicService] getPublicWatchlist error: ${e.message}")
            null
        }
    }
}
