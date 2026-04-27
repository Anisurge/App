package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val userId: String,
    val session: String,
    val expire: String,
    val sessionId: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class VerifyResetCodeRequest(
    val email: String,
    val code: String,
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val password: String,
    val confirmPassword: String,
)

@Serializable
data class BasicApiResponse(
    val success: Boolean,
    val message: String? = null,
)

@Serializable
data class TvPairingRequest(
    val nonce: String,
    val session: SessionInfo,
)

@Serializable
data class TvPairingResponse(
    val success: Boolean,
    val message: String? = null,
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val message: String? = null,
    val session: SessionData? = null,
    val user: UserProfile? = null,
)

@Serializable
data class SessionData(
    val userId: String,
    val sessionSecret: String? = null,
    val session: String? = null,
    val expire: String,
    val sessionId: String,
) {
    fun toSessionInfo() = SessionInfo(
        userId    = userId,
        session   = sessionSecret ?: session ?: "",
        expire    = expire,
        sessionId = sessionId,
    )
}

@Serializable
data class UserProfile(
    val id: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val name: String? = null,
    val pfp: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val displayName: String? = null,
    val joinDate: String? = null,
    val ago: String? = null,
    val isEmailVerified: Boolean? = null,
) {
    val effectiveId: String? get() = id ?: userId
    val effectiveAvatar: String? get() = pfp ?: avatar
}

@Serializable
data class CurrentUserResponse(
    val success: Boolean? = null,
    val user: UserProfile? = null,
)

@Serializable
data class UpdateFileInfo(
    val key: String? = null,
    val label: String? = null,
    val url: String? = null,
    val size: Long? = null,
    val sha256: String? = null,
    val arch: String? = null,
    val installerType: String? = null,
)

@Serializable
data class UpdateResponse(
    val success: Boolean? = true,
    val updateAvailable: Boolean? = null,
    val required: Boolean? = null,
    val critical: Boolean? = false,
    val message: List<String>? = null,
    val version: String? = null,
    val latestVersion: String? = null,
    val build: Int? = null,
    val buildNumber: Int? = null,
    val minimumBuildNumber: Int? = null,
    val title: String? = null,
    val releaseNotes: String? = null,
    val changelog: List<String>? = null,
    val downloadUrl: String? = null,
    val files: Map<String, UpdateFileInfo>? = null,
    val fileList: List<UpdateFileInfo>? = null,
    val social: SocialLinks? = null,
)

@Serializable
data class SocialLinks(
    val discord: String? = null,
    val telegram: String? = null,
    val reddit: String? = null,
)

sealed interface SessionCheckResult {
    data object NoSession     : SessionCheckResult
    data object Expired       : SessionCheckResult
    data object NetworkError  : SessionCheckResult   // transient connectivity failure
    data class Valid(val session: SessionInfo, val user: UserProfile? = null) : SessionCheckResult
}
