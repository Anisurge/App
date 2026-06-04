package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import to.kuudere.anisuge.data.models.CommentLikeRequest
import to.kuudere.anisuge.data.models.CommentsResponse
import to.kuudere.anisuge.data.models.CreateCommentRequest
import to.kuudere.anisuge.data.models.PostCommentResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

/**
 * Anisurge-owned episode comments (BFF). Replaces the legacy ReAnime comment API so comments use
 * our profiles, custom pfps, and equipped shop avatar frames. Requires the Anisurge JWT.
 */
class CommentService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val baseUrl = AnisurgeApi.v1Base

    suspend fun getComments(
        slug: String,
        episodeNumber: Int,
        page: Int = 1,
        sort: String = "new",
        nid: String? = null,
    ): CommentsResponse? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("$baseUrl/comments/$slug/$episodeNumber") {
                parameter("page", page)
                parameter("sort", sort)
                nid?.let { parameter("nid", it) }
                if (stored?.anisurgeToken?.isNotBlank() == true) applyAnisurgeAuth(stored)
            }
            response.body()
        } catch (e: Exception) {
            println("[CommentService] getComments error: ${e.message}")
            null
        }
    }

    suspend fun getReplies(
        slug: String,
        episodeNumber: Int,
        parentId: String,
        page: Int = 1,
    ): CommentsResponse? {
        return try {
            val stored = sessionStore.get()
            val response = httpClient.get("$baseUrl/comments/$slug/$episodeNumber") {
                parameter("parent_id", parentId)
                parameter("page", page)
                if (stored?.anisurgeToken?.isNotBlank() == true) applyAnisurgeAuth(stored)
            }
            response.body()
        } catch (e: Exception) {
            println("[CommentService] getReplies error: ${e.message}")
            null
        }
    }

    suspend fun postComment(
        anime: String,
        episodeNumber: Int,
        content: String,
        parentCommentId: String? = null,
        spoiler: Boolean = false,
        stickerId: String? = null,
    ): PostCommentResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            if (stored.anisurgeToken.isNullOrBlank()) {
                return PostCommentResponse(success = false, message = "Please log in to comment.")
            }
            val response = httpClient.post("$baseUrl/comments") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(
                    CreateCommentRequest(
                        anime = anime,
                        ep = episodeNumber,
                        content = content,
                        parentCommentId = parentCommentId,
                        spoiler = spoiler,
                        stickerId = stickerId,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return PostCommentResponse(success = false, message = "HTTP ${response.status.value}")
            }
            try {
                response.body<PostCommentResponse>()
            } catch (e: Exception) {
                PostCommentResponse(success = true)
            }
        } catch (e: Exception) {
            println("[CommentService] postComment error: ${e.message}")
            null
        }
    }

    suspend fun toggleLike(commentId: String, likeState: String): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            if (stored.anisurgeToken.isNullOrBlank()) return false
            val response = httpClient.post("$baseUrl/comments/like") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(CommentLikeRequest(commentId = commentId, likeState = likeState))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("[CommentService] toggleLike error: ${e.message}")
            false
        }
    }

    suspend fun deleteComment(commentId: String): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            if (stored.anisurgeToken.isNullOrBlank()) return false
            val response = httpClient.delete("$baseUrl/comments/$commentId") {
                applyAnisurgeAuth(stored)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("[CommentService] deleteComment error: ${e.message}")
            false
        }
    }
}
