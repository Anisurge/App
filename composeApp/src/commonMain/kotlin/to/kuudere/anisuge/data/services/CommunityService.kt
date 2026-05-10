package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.CommunityCategoriesResponse
import to.kuudere.anisuge.data.models.CommunityCreatePostRequest
import to.kuudere.anisuge.data.models.CommunityCreatePostResponse
import to.kuudere.anisuge.data.models.CommunityLeaderboardResponse
import to.kuudere.anisuge.data.models.CommunityPostsResponse
import to.kuudere.anisuge.data.models.CommunitySinglePostResponse
import to.kuudere.anisuge.data.models.CommunityStatsResponse
import to.kuudere.anisuge.data.models.CommunityUnreadCountResponse
import to.kuudere.anisuge.data.models.CommunityVoteRequest
import to.kuudere.anisuge.data.models.CommunityVoteResponse

class CommunityService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val baseUrl = "${AppComponent.BASE_URL}/community"

    suspend fun getStats(): CommunityStatsResponse? = runCatching {
        httpClient.get("$baseUrl/stats").body<CommunityStatsResponse>()
    }.getOrNull()

    suspend fun getCategories(): CommunityCategoriesResponse? = runCatching {
        httpClient.get("$baseUrl/categories").body<CommunityCategoriesResponse>()
    }.getOrNull()

    suspend fun getTrending(): CommunityPostsResponse? = runCatching {
        httpClient.get("$baseUrl/trending").body<CommunityPostsResponse>()
    }.getOrNull()

    suspend fun getPosts(
        sort: String = "hot",
        category: String = "all",
        limit: Int = 10,
        offset: Int = 0,
    ): CommunityPostsResponse? = runCatching {
        httpClient.get("$baseUrl/posts") {
            parameter("sort", sort)
            parameter("category", category)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body<CommunityPostsResponse>()
    }.getOrNull()

    suspend fun getPost(id: String): CommunitySinglePostResponse? = runCatching {
        httpClient.get("$baseUrl/posts/$id").body<CommunitySinglePostResponse>()
    }.getOrNull()

    suspend fun getLeaderboard(period: String = "all"): CommunityLeaderboardResponse? = runCatching {
        httpClient.get("$baseUrl/leaderboard/users") {
            parameter("period", period)
        }.body<CommunityLeaderboardResponse>()
    }.getOrNull()

    suspend fun getUnreadCount(): CommunityUnreadCountResponse? = runCatching {
        val requestToken = sessionStore.get()?.token
        httpClient.get("$baseUrl/unread-count") {
            requestToken?.let { header("Authorization", "Bearer $it") }
        }.body<CommunityUnreadCountResponse>()
    }.getOrNull()

    suspend fun createPost(payload: CommunityCreatePostRequest): Result<CommunityCreatePostResponse> {
        val token = sessionStore.get()?.token
            ?: return Result.failure(IllegalStateException("Please log in to create a post."))
        return runCatching {
            val response = httpClient.post("$baseUrl/posts") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Create post failed (${response.status.value})")
            }
            response.body<CommunityCreatePostResponse>()
        }
    }

    suspend fun votePost(postId: String, vote: Int): Result<CommunityVoteResponse> {
        val token = sessionStore.get()?.token
            ?: return Result.failure(IllegalStateException("Please log in to vote."))
        return runCatching {
            val response = httpClient.post("$baseUrl/posts/$postId/vote") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(CommunityVoteRequest(vote))
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Vote failed (${response.status.value})")
            }
            response.body<CommunityVoteResponse>()
        }
    }
}
