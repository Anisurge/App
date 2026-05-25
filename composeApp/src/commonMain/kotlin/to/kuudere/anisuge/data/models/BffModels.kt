package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class BffErrorResponse(
    val error: String? = null,
    val message: String? = null,
) {
    fun displayMessage(): String = error ?: message ?: "Request failed"
}

@Serializable
data class BffAuthResponse(
    val projectRToken: String,
    val anisurgeToken: String,
    val anisurgeUserId: String? = null,
    val user: BffPublicUser? = null,
    val library: BffLibraryCounts? = null,
)

@Serializable
data class BffLibraryCounts(
    val watchlistCount: Int = 0,
    val continueWatchingCount: Int = 0,
)

@Serializable
data class BffLibrarySyncResponse(
    val ok: Boolean = true,
    val library: BffLibraryCounts? = null,
)

@Serializable
data class BffMeResponse(
    val user: BffPublicUser,
)

@Serializable
data class BffPublicUser(
    val externalUserId: String,
    val username: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val timezone: String? = null,
    val joinDate: String? = null,
    val role: String? = null,
    val emailVerified: Boolean? = null,
    val isPrivate: Boolean? = null,
    val showOnlineStatus: Boolean? = null,
    val reanimeSettings: JsonObject? = null,
    val customPfpUrl: String? = null,
    val coins: Int = 0,
    val equipped: JsonObject? = null,
    val profileExtra: JsonObject? = null,
    val reanimeConnected: Boolean? = null,
    val reanimeUsername: String? = null,
)

fun BffPublicUser.toUserProfile(): UserProfile {
    val ago = (profileExtra?.get("ago") as? JsonPrimitive)?.contentOrNull
    val resolvedAvatar = customPfpUrl?.takeIf { it.isNotBlank() } ?: avatarUrl
    val equippedMap = equipped
    val frameUrl = equippedMap.stringField("chatAvatarFrame")
        ?: equippedMap.stringField("avatarFrame")
        ?: equippedMap.stringField("frame")
    val frameItemId = equippedMap.stringField("chatAvatarFrameItemId")
    val outerUrl = equippedMap.stringField("chatAvatarOuter")
        ?: equippedMap.stringField("avatarOuter")
        ?: equippedMap.stringField("outerFrame")
    return UserProfile(
        id = externalUserId,
        userId = externalUserId,
        username = username,
        email = email,
        pfp = resolvedAvatar,
        avatar = resolvedAvatar,
        displayName = displayName,
        bio = bio,
        website = website,
        timezone = timezone,
        joinDate = joinDate,
        ago = ago,
        isEmailVerified = emailVerified,
        coins = coins,
        equippedFrameUrl = frameUrl,
        equippedOuterFrameUrl = outerUrl,
        equippedFrameItemId = frameItemId,
        equipped = equippedMap,
        reanimeConnected = reanimeConnected == true,
        reanimeUsername = reanimeUsername,
    )
}

@Serializable
data class BffConnectReanimeRequest(
    val email: String,
    val password: String,
)

@Serializable
data class BffConnectReanimeResponse(
    val success: Boolean,
    val projectRToken: String? = null,
    val user: BffPublicUser,
)

private fun JsonObject?.stringField(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
