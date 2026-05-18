package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomResponse(
    val slug: String = "",
    val name: String = "",
    @SerialName("onlineCount") val onlineCount: Int = 0,
)

/** Premium gradient username (Discord-style); rendered when `isPremium` and colors are set. */
@Serializable
data class ChatNameStyle(
    @SerialName("gradientStart") val gradientStart: String = "",
    @SerialName("gradientEnd") val gradientEnd: String = "",
    val animated: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("roomSlug") val roomSlug: String = "",
    val body: String = "",
    @SerialName("userId") val userId: String = "",
    val username: String = "",
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("avatarFrameUrl") val avatarFrameUrl: String? = null,
    @SerialName("avatarOuterUrl") val avatarOuterUrl: String? = null,
    @SerialName("userColor") val userColor: String? = null,
    @SerialName("createdAt") val createdAt: String = "",
    @SerialName("joinedAt") val joinedAt: String? = null,
    @SerialName("isPremium") val isPremium: Boolean = false,
    @SerialName("nameStyle") val nameStyle: ChatNameStyle? = null,
    val coins: Int = 0,
) {
    fun toMemberProfile(): ChatMemberProfile = ChatMemberProfile(
        userId = userId,
        username = username.ifBlank { "User" },
        avatarUrl = avatarUrl,
        avatarFrameUrl = avatarFrameUrl,
        avatarOuterUrl = avatarOuterUrl,
        joinedAt = joinedAt,
        isPremium = isPremium,
        nameStyle = nameStyle,
        coins = coins,
    )
}

/** Shown when tapping a chat avatar or username. */
data class ChatMemberProfile(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val avatarFrameUrl: String? = null,
    val avatarOuterUrl: String? = null,
    val joinedAt: String?,
    val isPremium: Boolean,
    val nameStyle: ChatNameStyle?,
    val coins: Int = 0,
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
