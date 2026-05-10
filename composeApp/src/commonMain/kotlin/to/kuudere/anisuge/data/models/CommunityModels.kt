package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityStatsResponse(
    @SerialName("online_count") val onlineCount: Int = 0,
    val members: Int = 0,
)

@Serializable
data class CommunityCategoriesResponse(
    val categories: List<CommunityCategory> = emptyList(),
)

@Serializable
data class CommunityCategory(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
    val description: String = "",
    val color: String = "",
    val icon: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    val count: Int = 0,
)

@Serializable
data class CommunityPostsResponse(
    val posts: List<CommunityPost> = emptyList(),
    val hasMore: Boolean = false,
)

@Serializable
data class CommunityPost(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val content: String = "",
    val author: String = "",
    @SerialName("author_display_name") val authorDisplayName: String? = null,
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("author_avatar") val authorAvatar: String? = null,
    @SerialName("author_aura") val authorAura: Int = 0,
    val category: String = "",
    @SerialName("category_id") val categoryId: Int? = null,
    val flair: String? = null,
    val votes: Int = 0,
    @SerialName("user_vote") val userVote: Int = 0,
    val comments: Int = 0,
    val views: Int = 0,
    val viewed: Boolean = false,
    val time: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val spoiler: Boolean = false,
)

@Serializable
data class CommunitySinglePostResponse(
    val post: CommunityPost? = null,
)

@Serializable
data class CommunityCreatePostRequest(
    val title: String,
    val content: String,
    val category: String,
    val flair: String? = null,
    val images: List<String> = emptyList(),
    val pinned: Boolean = false,
    val spoiler: Boolean = false,
)

@Serializable
data class CommunityCreatePostResponse(
    val post: CommunityPost? = null,
    val success: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class CommunityVoteRequest(
    val vote: Int,
)

@Serializable
data class CommunityVoteResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val votes: Int? = null,
    @SerialName("user_vote") val userVote: Int? = null,
)

@Serializable
data class CommunityLeaderboardResponse(
    val users: List<CommunityLeaderboardUser> = emptyList(),
)

@Serializable
data class CommunityLeaderboardUser(
    @SerialName("userId") val userId: String = "",
    val name: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val avatar: String? = null,
    val aura: Int = 0,
    val rank: Int = 0,
)

@Serializable
data class CommunityUnreadCountResponse(
    @SerialName("unread_count") val unreadCount: Int = 0,
)
