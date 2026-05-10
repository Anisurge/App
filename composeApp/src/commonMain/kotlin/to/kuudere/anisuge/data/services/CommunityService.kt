package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import to.kuudere.anisuge.data.models.CommentsResponse
import to.kuudere.anisuge.data.models.CommunityCommentCreateRequest
import to.kuudere.anisuge.data.models.PostCommentResponse

class CommunityService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val baseUrl = "${AppComponent.BASE_URL}/community"

    /** Avoid `Authorization: Bearer Bearer …` if the stored token already included the scheme. */
    private fun sanitizedBearerCredential(rawToken: String): String {
        val t = rawToken.trim().removeSurrounding("\"")
        return Regex("^Bearer\\s*(:\\s*)?", RegexOption.IGNORE_CASE).replaceFirst(t, "")
    }

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

    suspend fun getPostComments(postId: String): CommentsResponse? = runCatching {
        val requestToken = sessionStore.get()?.token
        httpClient.get("$baseUrl/posts/$postId/comments") {
            requestToken?.let { header("Authorization", "Bearer ${sanitizedBearerCredential(it)}") }
        }.body<CommentsResponse>()
    }.getOrNull()

    suspend fun createPostComment(postId: String, payload: CommunityCommentCreateRequest): Result<PostCommentResponse> {
        val token = sessionStore.get()?.token
            ?: return Result.failure(IllegalStateException("Please log in to comment."))
        val credential = sanitizedBearerCredential(token)

        suspend fun postNested(): io.ktor.client.statement.HttpResponse {
            val json = buildJsonObject {
                put("content", payload.content)
                put("isSpoiller", payload.spoiler)
                payload.parentCommentId?.takeIf { it.isNotBlank() }?.let { put("parentCommentId", it) }
            }
            return httpClient.post("$baseUrl/posts/$postId/comments") {
                header("Authorization", "Bearer $credential")
                contentType(ContentType.Application.Json)
                setBody(json.toString())
            }
        }

        return try {
            val flatJson = buildJsonObject {
                put("post", postId)
                put("content", payload.content)
                if (payload.spoiler) put("spoiler", true)
                payload.parentCommentId?.takeIf { it.isNotBlank() }?.let { put("parentCommentId", it) }
            }

            var response = httpClient.post("$baseUrl/comment") {
                header("Authorization", "Bearer $credential")
                contentType(ContentType.Application.Json)
                setBody(flatJson.toString())
            }

            if (!response.status.isSuccess()) {
                val tryNested =
                    response.status == HttpStatusCode.NotFound ||
                        response.status == HttpStatusCode.MethodNotAllowed ||
                        response.status == HttpStatusCode.BadRequest ||
                        response.status == HttpStatusCode.Unauthorized ||
                        response.status == HttpStatusCode.Forbidden
                if (tryNested) {
                    response = postNested()
                }
            }

            if (!response.status.isSuccess()) {
                val err = response.runCatching { bodyAsText() }.getOrNull().orEmpty().trim()
                println("[CommunityService] createPostComment status=${response.status} body=${err.take(400)}")
                throw IllegalStateException(
                    err.ifBlank {
                        "Comment failed (${response.status.value}). Log out and back in if this keeps happening."
                    }.take(240),
                )
            }
            Result.success(
                try {
                    response.body<PostCommentResponse>()
                } catch (_: Exception) {
                    PostCommentResponse(success = true)
                },
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLeaderboard(period: String = "all"): CommunityLeaderboardResponse? = runCatching {
        httpClient.get("$baseUrl/leaderboard/users") {
            parameter("period", period)
        }.body<CommunityLeaderboardResponse>()
    }.getOrNull()

    suspend fun getUnreadCount(): CommunityUnreadCountResponse? = runCatching {
        val requestToken = sessionStore.get()?.token
        httpClient.get("$baseUrl/unread-count") {
            requestToken?.let { header("Authorization", "Bearer ${sanitizedBearerCredential(it)}") }
        }.body<CommunityUnreadCountResponse>()
    }.getOrNull()

    suspend fun createPost(payload: CommunityCreatePostRequest): Result<CommunityCreatePostResponse> {
        val token = sessionStore.get()?.token
            ?: return Result.failure(IllegalStateException("Please log in to create a post."))
        val credential = sanitizedBearerCredential(token)
        return runCatching {
            val response = httpClient.post("$baseUrl/posts") {
                header("Authorization", "Bearer $credential")
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
        val credential = sanitizedBearerCredential(token)
        return runCatching {
            val response = httpClient.post("$baseUrl/posts/$postId/vote") {
                header("Authorization", "Bearer $credential")
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
