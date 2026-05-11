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
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.CommunityCategoriesResponse
import to.kuudere.anisuge.data.models.CommunityFlatCommentRequest
import to.kuudere.anisuge.data.models.CommunityNestedCommentRequest
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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CommunityService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private companion object {
        private val ErrorBodyJson = Json { ignoreUnknownKeys = true }
    }

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

        val nestedBody = CommunityNestedCommentRequest(
            content = payload.content,
            isSpoiller = payload.spoiler,
            parentCommentId = payload.parentCommentId?.takeIf { it.isNotBlank() },
        )
        val flatBody = CommunityFlatCommentRequest(
            post = postId,
            content = payload.content,
            spoiler = payload.spoiler,
            parentCommentId = payload.parentCommentId?.takeIf { it.isNotBlank() },
        )

        return try {
            // Prefer REST shape (same tree as GET .../posts/{id}/comments). Use kotlinx objects so the JSON
            // plugin emits real application/json objects (buildJsonObject.toString() can mis-serialize).
            var response = httpClient.post("$baseUrl/posts/$postId/comments") {
                header("Authorization", "Bearer $credential")
                contentType(ContentType.Application.Json)
                setBody(nestedBody)
            }

            if (!response.status.isSuccess()) {
                val tryFlat =
                    response.status == HttpStatusCode.NotFound ||
                        response.status == HttpStatusCode.MethodNotAllowed
                if (tryFlat) {
                    response = httpClient.post("$baseUrl/comment") {
                        header("Authorization", "Bearer $credential")
                        contentType(ContentType.Application.Json)
                        setBody(flatBody)
                    }
                }
            }

            if (!response.status.isSuccess()) {
                val err = response.runCatching { bodyAsText() }.getOrNull().orEmpty().trim()
                println("[CommunityService] createPostComment status=${response.status} body=${err.take(400)}")
                val message = formatCommunityError(err, response.status)
                throw IllegalStateException(message)
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

    /** Parses `{"error":"..."}` when present; maps 401 to a clearer session hint. */
    private fun formatCommunityError(rawBody: String, status: HttpStatusCode): String {
        val fromJson = runCatching {
            val o = ErrorBodyJson.parseToJsonElement(rawBody).jsonObject
            o["error"]?.jsonPrimitive?.contentOrNull ?: o["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val base = fromJson?.takeIf { it.isNotBlank() } ?: rawBody
        val detail = base.ifBlank { "Request failed (${status.value})" }.take(220)
        return when (status) {
            HttpStatusCode.Unauthorized ->
                if (detail.contains("unauthorised", ignoreCase = true) ||
                    detail.contains("unauthorized", ignoreCase = true)
                ) {
                    "Not signed in or session expired — sign out and sign in again, then retry. ($detail)"
                } else {
                    detail
                }
            else -> detail
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
