package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.models.Comment
import to.kuudere.anisuge.data.models.CommentsResponse
import to.kuudere.anisuge.data.models.PostCommentResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

@Serializable
private data class PostCommentRequest(
    val anime: String,
    val ep: Int,
    val content: String,
    val parentCommentId: String? = null,
)

@Serializable
private data class ToggleLikeRequest(
    val commentId: String,
    val likeState: String,
)

class CommentService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getComments(
        slug: String,
        episodeNumber: Int,
        page: Int = 1,
        sort: String = "new",
        nid: String? = null,
    ): CommentsResponse? {
        return try {
            val stored = sessionStore.get()
            val url = buildString {
                append("${ApiConfig.API_BASE}/anime/comments/$slug/$episodeNumber?page=$page&sort=$sort")
                if (nid != null) append("&nid=$nid")
            }
            val response = httpClient.get(url) {
                if (stored != null) bearer(stored.token)
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
            val url = "${ApiConfig.API_BASE}/anime/comments/$slug/$episodeNumber?parent_id=$parentId&page=$page"
            val response = httpClient.get(url) {
                if (stored != null) bearer(stored.token)
            }
            response.body()
        } catch (e: Exception) {
            println("[CommentService] getReplies error: ${e.message}")
            null
        }
    }

    suspend fun postComment(
        animeSlug: String,
        episodeNumber: Int,
        content: String,
        parentCommentId: String? = null,
    ): PostCommentResponse? {
        return try {
            val stored = sessionStore.get()
            if (stored == null) {
                println("[CommentService] postComment: no session stored, aborting")
                return null
            }
            val body = PostCommentRequest(animeSlug, episodeNumber, content, parentCommentId)
            val response = httpClient.post("${ApiConfig.API_BASE}/anime/comment") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.value !in 200..299) {
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

    suspend fun toggleCommentLike(commentId: String, likeState: String): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val body = ToggleLikeRequest(commentId, likeState)
            val response = httpClient.post("${ApiConfig.API_BASE}/anime/comment/like") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[CommentService] toggleCommentLike error: ${e.message}")
            false
        }
    }
}
