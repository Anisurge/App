package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementAuthor(
    val id: String = "",
    val username: String = "Staff",
    @SerialName("avatarUrl") val avatarUrl: String? = null,
)

@Serializable
data class AnnouncementMedia(
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("videoUrl") val videoUrl: String? = null,
)

@Serializable
data class AnnouncementPollOption(
    val id: String = "",
    val text: String = "",
    val votes: Int = 0,
)

@Serializable
data class AnnouncementPoll(
    val question: String = "",
    val options: List<AnnouncementPollOption> = emptyList(),
    @SerialName("myOptionId") val myOptionId: String? = null,
)

@Serializable
data class Announcement(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val status: String = "published",
    val pinned: Boolean = false,
    val media: AnnouncementMedia = AnnouncementMedia(),
    val poll: AnnouncementPoll? = null,
    @SerialName("upvoteCount") val upvoteCount: Int = 0,
    val upvoted: Boolean = false,
    val read: Boolean = false,
    val author: AnnouncementAuthor = AnnouncementAuthor(),
    @SerialName("publishedAt") val publishedAt: String? = null,
    @SerialName("createdAt") val createdAt: String = "",
    @SerialName("updatedAt") val updatedAt: String = "",
)

@Serializable
data class AnnouncementsResponse(
    val announcements: List<Announcement> = emptyList(),
    @SerialName("hasMore") val hasMore: Boolean = false,
    @SerialName("unreadCount") val unreadCount: Int = 0,
    @SerialName("isStaff") val isStaff: Boolean = false,
)

@Serializable
data class AnnouncementStatusResponse(
    @SerialName("unreadCount") val unreadCount: Int = 0,
    @SerialName("latestId") val latestId: String? = null,
    @SerialName("latestPublishedAt") val latestPublishedAt: String? = null,
)

@Serializable
data class AnnouncementCreateRequest(
    val title: String,
    val body: String,
    val status: String = "published",
    val pinned: Boolean = false,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("videoUrl") val videoUrl: String? = null,
    val poll: AnnouncementCreatePoll? = null,
)

@Serializable
data class AnnouncementCreatePoll(
    val question: String? = null,
    val options: List<AnnouncementCreatePollOption> = emptyList(),
)

@Serializable
data class AnnouncementCreatePollOption(
    val id: String? = null,
    val text: String,
)

@Serializable
data class AnnouncementCreateResponse(
    val ok: Boolean = false,
    val id: String = "",
)

@Serializable
data class AnnouncementVoteResponse(
    val upvoted: Boolean = false,
    @SerialName("upvoteCount") val upvoteCount: Int = 0,
)

@Serializable
data class AnnouncementReadResponse(
    val ok: Boolean = false,
    @SerialName("unreadCount") val unreadCount: Int = 0,
)

@Serializable
data class AnnouncementPollVoteRequest(
    @SerialName("optionId") val optionId: String,
)

@Serializable
data class AnnouncementPollVoteOptionResponse(
    val id: String = "",
    val votes: Int = 0,
)

@Serializable
data class AnnouncementPollVoteResponse(
    @SerialName("myOptionId") val myOptionId: String = "",
    val options: List<AnnouncementPollVoteOptionResponse> = emptyList(),
)
