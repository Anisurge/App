package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class W2gRoomSummary(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("room_name") val roomName: String,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("host_username") val hostUsername: String? = null,
    @SerialName("anime_title") val animeTitle: String? = null,
    @SerialName("anime_poster") val animePoster: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
data class W2gRoomListResponse(
    val rooms: List<W2gRoomSummary> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class W2gRoomCreateRequest(
    @SerialName("room_name") val roomName: String,
    val password: String? = null,
)

@Serializable
data class W2gRoomCreateResponse(
    @SerialName("invite_code") val inviteCode: String,
    val room: W2gRoomDetail? = null,
)

@Serializable
data class W2gJoinRequest(
    val password: String? = null,
)

@Serializable
data class W2gJoinResponse(
    val room: W2gRoomDetail? = null,
)

@Serializable
data class W2gRoomUpdateRequest(
    @SerialName("anime_id") val animeId: String? = null,
    @SerialName("anime_title") val animeTitle: String? = null,
    @SerialName("anime_poster") val animePoster: String? = null,
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("mal_id") val malId: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    val server: String? = null,
    val language: String? = null,
    val quality: String? = null,
)

@Serializable
data class W2gRoomDetail(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("room_name") val roomName: String,
    @SerialName("host_user_id") val hostUserId: String,
    @SerialName("host_username") val hostUsername: String? = null,
    @SerialName("anime_id") val animeId: String? = null,
    @SerialName("anime_title") val animeTitle: String? = null,
    @SerialName("anime_poster") val animePoster: String? = null,
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("mal_id") val malId: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    val server: String? = null,
    val language: String? = null,
    val quality: String? = null,
    @SerialName("member_count") val memberCount: Int = 0,
    val members: List<W2gRoomMember> = emptyList(),
    @SerialName("player_state") val playerState: W2gPlayerState? = null,
)

@Serializable
data class W2gRoomMember(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class W2gPlayerState(
    val playing: Boolean = false,
    @SerialName("current_time") val currentTime: Double = 0.0,
    val timestamp: Long = 0,
)

@Serializable
data class W2gWsEnvelope(
    val type: String,
    val data: JsonElement? = null,
    val message: String? = null,
)
