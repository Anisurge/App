package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val autoNext: Boolean = true,
    val autoPlay: Boolean = false,
    val defaultLang: Boolean = true,
    val skipIntro: Boolean = false,
    val skipOutro: Boolean = false,
    val showComments: Boolean = true,
    val publicWatchlist: Boolean = false,
    val syncPercentage: Double = 80.0,
)

@Serializable
data class UserSettingsResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val settings: UserSettings? = null,
)

@Serializable
data class PasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class PasswordChangeResponse(
    val success: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class ProfileUpdateRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val timezone: String? = null,
)

@Serializable
data class ProfileUpdateResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val user: UserProfile? = null,
)

@Serializable
data class NotificationCountResponse(
    val total: Int = 0,
    val anime: Int = 0,
    val community: Int = 0,
    val system: Int = 0,
)

@Serializable
data class NotificationsResponse(
    val notifications: List<NotificationItem> = emptyList(),
    val has_more: Boolean = false,
    val page: Int = 1,
)

@Serializable
data class NotificationItem(
    val id: String = "",
    val type: String = "",
    val title: String? = null,
    val body: String? = null,
    val animeId: String? = null,
    val episode: Int? = null,
    val read: Boolean = false,
    val createdAt: String? = null,
    val actionUrl: String? = null,
)
