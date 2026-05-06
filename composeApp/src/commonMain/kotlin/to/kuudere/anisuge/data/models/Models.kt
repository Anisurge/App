package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val token: String,
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class ForgotPasswordRequest(
    val identifier: String,
)

@Serializable
data class ResetPasswordRequest(
    val identifier: String,
    val otp: String,
    val newPassword: String,
)

@Serializable
data class BasicApiResponse(
    val success: Boolean? = null,
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
    val success: Boolean? = null,
    val message: String? = null,
    val token: String? = null,
    val user: UserProfile? = null,
)

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
    val timezone: String? = null,
    val website: String? = null,
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
    data object NetworkError  : SessionCheckResult
    data class Valid(val session: SessionInfo, val user: UserProfile? = null) : SessionCheckResult
}
