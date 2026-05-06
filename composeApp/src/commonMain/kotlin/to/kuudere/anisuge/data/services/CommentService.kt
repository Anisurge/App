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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.CommentsResponse
import to.kuudere.anisuge.data.models.PostCommentResponse

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
            val response = httpClient.get("${AppComponent.BASE_URL}/anime/comments/$slug/$episodeNumber") {
                parameter("page", page)
                parameter("sort", sort)
                nid?.let { parameter("nid", it) }
                if (stored != null) header("Authorization", "Bearer ${stored.token}")
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
            val response = httpClient.get("${AppComponent.BASE_URL}/anime/comments/$slug/$episodeNumber") {
                parameter("parent_id", parentId)
                parameter("page", page)
                if (stored != null) header("Authorization", "Bearer ${stored.token}")
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
    ): PostCommentResponse? {
        return try {
            val stored = sessionStore.get() ?: return null
            val body = buildJsonObject {
                put("anime", anime)
                put("ep", episodeNumber)
                put("content", content)
                parentCommentId?.let { put("parentCommentId", it) }
            }
            val response = httpClient.post("${AppComponent.BASE_URL}/anime/comment") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
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

    suspend fun toggleLike(commentId: String, likeState: String): Boolean {
        return try {
            val stored = sessionStore.get() ?: return false
            val body = buildJsonObject {
                put("commentId", commentId)
                put("likeState", likeState)
            }
            val response = httpClient.post("${AppComponent.BASE_URL}/anime/comment/like") {
                header("Authorization", "Bearer ${stored.token}")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[CommentService] toggleLike error: ${e.message}")
            false
        }
    }
}
