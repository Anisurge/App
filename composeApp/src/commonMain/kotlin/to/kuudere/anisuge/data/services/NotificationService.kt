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
data class NotificationItem(
    val id: String = "",
    val type: String = "",
    val title: String? = null,
    val body: String? = null,
    val animeId: String? = null,
    val episodeNumber: Int? = null,
    val fromUser: String? = null,
    val fromUserAvatar: String? = null,
    val read: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class NotificationsResponse(
    val notifications: List<NotificationItem> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class UnreadNotificationCountResponse(
    val total: Int = 0,
    val anime: Int = 0,
    val community: Int = 0,
    val system: Int = 0,
)

@Serializable
data class MarkReadRequest(
    val type: String = "",
)

class NotificationService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getNotifications(
        type: String = "anime",
        page: Int = 1,
        typeQuery: String? = null,
    ): NotificationsResponse? {
        val stored = sessionStore.get() ?: return null
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/notifications/$type") {
                bearer(stored.token)
                parameter("page", page)
                typeQuery?.let { parameter("typeQuery", it) }
            }
            response.body()
        } catch (e: Exception) {
            println("[NotificationService] getNotifications error: ${e.message}")
            null
        }
    }

    suspend fun getUnreadCount(): UnreadNotificationCountResponse? {
        val stored = sessionStore.get() ?: return null
        return try {
            httpClient.get("${ApiConfig.API_BASE}/notifications/count") {
                bearer(stored.token)
            }.body()
        } catch (e: Exception) {
            println("[NotificationService] getUnreadCount error: ${e.message}")
            null
        }
    }

    suspend fun markAllRead(type: String = ""): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.post("${ApiConfig.API_BASE}/notifications/mark-all-read") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(MarkReadRequest(type))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[NotificationService] markAllRead error: ${e.message}")
            false
        }
    }
}
