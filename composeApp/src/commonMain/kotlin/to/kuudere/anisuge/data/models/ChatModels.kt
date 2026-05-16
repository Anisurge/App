package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomResponse(
    val slug: String = "",
    val name: String = "",
    @SerialName("onlineCount") val onlineCount: Int = 0,
)

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("roomSlug") val roomSlug: String = "",
    val body: String = "",
    @SerialName("userId") val userId: String = "",
    val username: String = "",
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("createdAt") val createdAt: String = "",
)

@Serializable
data class ChatMessagesResponse(
    val messages: List<ChatMessage> = emptyList(),
    @SerialName("hasMore") val hasMore: Boolean = false,
)

@Serializable
data class ChatPostMessageRequest(
    val body: String,
)

@Serializable
data class ChatWsEnvelope(
    val type: String = "",
    val data: ChatMessage? = null,
    val message: String? = null,
)

sealed class ChatLiveEvent {
    data class Message(val message: ChatMessage) : ChatLiveEvent()
    data class Error(val message: String) : ChatLiveEvent()
    data object Connected : ChatLiveEvent()
    data object Disconnected : ChatLiveEvent()
}
