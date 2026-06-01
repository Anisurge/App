package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SURGE_BOT_USER_ID = "surge-ai"
const val SURGE_BOT_AVATAR_URL = "https://files.catbox.moe/j3e1xt.mp4"

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
    val metadata: ChatMessageMetadata = ChatMessageMetadata(),
    @SerialName("userId") val userId: String = "",
    val username: String = "",
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("avatarFrameUrl") val avatarFrameUrl: String? = null,
    @SerialName("avatarOuterUrl") val avatarOuterUrl: String? = null,
    @SerialName("userColor") val userColor: String? = null,
    @SerialName("createdAt") val createdAt: String = "",
    @SerialName("joinedAt") val joinedAt: String? = null,
    @SerialName("isPremium") val isPremium: Boolean = false,
    @SerialName("isStaff") val isStaff: Boolean = false,
    @SerialName("nameStyle") val nameStyle: ChatNameStyle? = null,
    val coins: Int = 0,
    val isBot: Boolean = userId == SURGE_BOT_USER_ID,
) {
    /** Bots are always treated as premium for display purposes. */
    val effectivePremium: Boolean get() = isPremium || isBot

    val effectiveAvatarUrl: String?
        get() = avatarUrl?.takeIf { it.isNotBlank() } ?: if (isBot) SURGE_BOT_AVATAR_URL else null

    fun toMemberProfile(): ChatMemberProfile = ChatMemberProfile(
        userId = userId,
        username = username.ifBlank { "User" },
        avatarUrl = effectiveAvatarUrl,
        avatarFrameUrl = avatarFrameUrl,
        avatarOuterUrl = avatarOuterUrl,
        joinedAt = joinedAt,
        isPremium = effectivePremium,
        nameStyle = nameStyle,
        coins = coins,
        isBot = isBot,
    )
}

@Serializable
data class ChatMessageMetadata(
    val kind: String? = null,
    val actions: List<ChatAction> = emptyList(),
    val anime: ChatAnimeCard? = null,
)

@Serializable
data class ChatAction(
    val label: String = "",
    val deeplink: String = "",
)

@Serializable
data class ChatAnimeCard(
    @SerialName("animeId") val animeId: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("imageUrl") val imageUrl: String = "",
    val format: String? = null,
    val year: Int? = null,
    val score: Int? = null,
    val episodes: Int? = null,
)

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
    val karmaPoints: Int = 0,
    val bio: String? = null,
    val website: String? = null,
    val isBot: Boolean = false,
    val hidden: Boolean = false,
    val watchHistory: List<ChatProfileLibraryItem> = emptyList(),
    val watchlist: List<ChatProfileLibraryItem> = emptyList(),
    val isLoadingDetails: Boolean = false,
    val detailError: String? = null,
) {
    val effectiveAvatarUrl: String?
        get() = avatarUrl?.takeIf { it.isNotBlank() } ?: if (isBot) SURGE_BOT_AVATAR_URL else null
}

@Serializable
data class ChatProfileLibraryItem(
    @SerialName("animeId") val animeId: String = "",
    val title: String = "",
    @SerialName("imageUrl") val imageUrl: String = "",
    val subtitle: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
)

@Serializable
data class ChatMemberProfileUserDto(
    val userId: String = "",
    val username: String = "",
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("avatarFrameUrl") val avatarFrameUrl: String? = null,
    @SerialName("avatarOuterUrl") val avatarOuterUrl: String? = null,
    @SerialName("joinedAt") val joinedAt: String? = null,
    @SerialName("isPremium") val isPremium: Boolean = false,
    @SerialName("isBot") val isBot: Boolean = false,
    val coins: Int = 0,
    @SerialName("karmaPoints") val karmaPoints: Int = 0,
    val bio: String? = null,
    val website: String? = null,
    val hidden: Boolean = false,
)

@Serializable
data class ChatMemberProfileResponse(
    val user: ChatMemberProfileUserDto = ChatMemberProfileUserDto(),
    val watchHistory: List<ChatProfileLibraryItem> = emptyList(),
    val watchlist: List<ChatProfileLibraryItem> = emptyList(),
) {
    fun toProfile(fallback: ChatMemberProfile? = null): ChatMemberProfile = ChatMemberProfile(
        userId = user.userId.ifBlank { fallback?.userId.orEmpty() },
        username = user.username.ifBlank { fallback?.username ?: "User" },
        avatarUrl = user.avatarUrl ?: fallback?.avatarUrl,
        avatarFrameUrl = user.avatarFrameUrl ?: fallback?.avatarFrameUrl,
        avatarOuterUrl = user.avatarOuterUrl ?: fallback?.avatarOuterUrl,
        joinedAt = user.joinedAt ?: fallback?.joinedAt,
        isPremium = user.isPremium || fallback?.isPremium == true,
        nameStyle = fallback?.nameStyle,
        coins = user.coins,
        karmaPoints = user.karmaPoints,
        bio = user.bio,
        website = user.website,
        isBot = user.isBot || fallback?.isBot == true,
        hidden = user.hidden,
        watchHistory = watchHistory,
        watchlist = watchlist,
    )
}

@Serializable
data class ChatMessagesResponse(
    val messages: List<ChatMessage> = emptyList(),
    @SerialName("hasMore") val hasMore: Boolean = false,
)

@Serializable
data class ChatPostMessageRequest(
    val body: String,
    val metadata: ChatMessageMetadata? = null,
)

@Serializable
data class ChatWsEnvelope(
    val type: String = "",
    val data: ChatMessage? = null,
    val message: String? = null,
    @SerialName("messageId") val messageId: String? = null,
)

sealed class ChatLiveEvent {
    data class Message(val message: ChatMessage) : ChatLiveEvent()
    data class Delete(val messageId: String) : ChatLiveEvent()
    data class Error(val message: String) : ChatLiveEvent()
    data object Connected : ChatLiveEvent()
    data object Disconnected : ChatLiveEvent()
}
