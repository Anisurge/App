package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

@Serializable
data class CommunityCategory(
    val slug: String = "",
    val name: String = "",
    val description: String? = null,
    val postCount: Int = 0,
)

@Serializable
data class CommunityPost(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String? = null,
    val flair: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val authorPfp: String? = null,
    val pinned: Boolean = false,
    val spoiler: Boolean = false,
    val images: List<String> = emptyList(),
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val commentCount: Int = 0,
    val createdAt: String? = null,
)

@Serializable
data class CommunityPostsResponse(
    val posts: List<CommunityPost> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class CommunityStatsResponse(
    val onlineCount: Int = 0,
    val memberCount: Int = 0,
)

@Serializable
data class CreatePostRequest(
    val title: String,
    val content: String,
    val category: String? = null,
    val flair: String? = null,
    val spoiler: Boolean = false,
    val pinned: Boolean = false,
    val images: List<String> = emptyList(),
)

@Serializable
data class VoteRequest(
    val vote: Int,
)

@Serializable
data class LeaderboardUser(
    val username: String = "",
    val avatar: String? = null,
    val aura: Int = 0,
)

class CommunityService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getCategories(): List<CommunityCategory>? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/community/categories").body()
        } catch (e: Exception) {
            println("[CommunityService] getCategories error: ${e.message}")
            null
        }
    }

    suspend fun getLeaderboard(period: String = "all"): List<LeaderboardUser>? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/community/leaderboard/users") {
                parameter("period", period)
            }.body()
        } catch (e: Exception) {
            println("[CommunityService] getLeaderboard error: ${e.message}")
            null
        }
    }

    suspend fun getPosts(
        sort: String = "hot",
        category: String = "all",
        limit: Int = 10,
        offset: Int = 0,
    ): CommunityPostsResponse? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/community/posts") {
                parameter("sort", sort)
                parameter("category", category)
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()
        } catch (e: Exception) {
            println("[CommunityService] getPosts error: ${e.message}")
            null
        }
    }

    suspend fun createPost(request: CreatePostRequest): CommunityPost? {
        val stored = sessionStore.get() ?: return null
        return try {
            httpClient.post("${ApiConfig.API_BASE}/community/posts") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            println("[CommunityService] createPost error: ${e.message}")
            null
        }
    }

    suspend fun getPost(id: String): CommunityPost? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/community/posts/$id").body()
        } catch (e: Exception) {
            println("[CommunityService] getPost error: ${e.message}")
            null
        }
    }

    suspend fun pinPost(id: String): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.post("${ApiConfig.API_BASE}/community/posts/$id/pin") {
                bearer(stored.token)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[CommunityService] pinPost error: ${e.message}")
            false
        }
    }

    suspend fun votePost(id: String, vote: Int): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.post("${ApiConfig.API_BASE}/community/posts/$id/vote") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(VoteRequest(vote))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[CommunityService] votePost error: ${e.message}")
            false
        }
    }

    suspend fun getStats(): CommunityStatsResponse? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/community/stats").body()
        } catch (e: Exception) {
            println("[CommunityService] getStats error: ${e.message}")
            null
        }
    }

    suspend fun getTrending(): List<CommunityPost>? {
        return try {
            httpClient.get("${ApiConfig.API_BASE}/community/trending").body()
        } catch (e: Exception) {
            println("[CommunityService] getTrending error: ${e.message}")
            null
        }
    }

    suspend fun getUnreadCount(): Int? {
        val stored = sessionStore.get() ?: return null
        return try {
            val response: UnreadCountResponse = httpClient.get("${ApiConfig.API_BASE}/community/unread-count") {
                bearer(stored.token)
            }.body()
            response.count
        } catch (e: Exception) {
            println("[CommunityService] getUnreadCount error: ${e.message}")
            null
        }
    }
}

@Serializable
private data class UnreadCountResponse(val count: Int = 0)
