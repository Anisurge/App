package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val token: String,
    val userId: String = "",
    val username: String = "",
    val expire: String = "",
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
)

@Serializable
data class SignupRequest(
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
    val email: String,
    val otp: String,
    @SerialName("new_password") val password: String,
)

@Serializable
data class BasicApiResponse(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
)

@Serializable
data class AuthResponse(
    val success: Boolean = true,
    val message: String? = null,
    val token: String? = null,
    val user: UserProfile? = null,
    val error: String? = null,
)

@Serializable
data class UserProfile(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    val username: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val name: String? = null,
    val pfp: String? = null,
    @SerialName("profile_picture")
    val profilePicture: String? = null,
    @SerialName("profilePicture")
    val profilePictureCamel: String? = null,
    val bio: String? = null,
    val location: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("join_date")
    val joinDate: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val ago: String? = null,
    @SerialName("is_email_verified")
    val isEmailVerified: Boolean? = null,
    val website: String? = null,
    val timezone: String? = null,
) {
    val effectiveId: String? get() = id ?: userId
    val effectiveDisplayName: String? get() = displayName ?: name ?: username
    val effectiveJoinDate: String? get() = joinDate ?: createdAt
    val effectiveAvatar: String? get() {
        val raw = pfp ?: avatar ?: profilePicture ?: profilePictureCamel
        return when {
            raw.isNullOrBlank() -> null
            raw.startsWith("http") -> raw
            raw.startsWith("/") -> "https://api.reanime.to$raw"
            else -> "https://api.reanime.to/$raw"
        }
    }
}

@Serializable
data class ProfileUpdateRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val timezone: String? = null,
)

@Serializable
data class PasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class PasswordChangeResponse(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
)

sealed interface SessionCheckResult {
    data object NoSession     : SessionCheckResult
    data object Expired       : SessionCheckResult
    data object NetworkError  : SessionCheckResult
    data class Valid(val session: SessionInfo, val user: UserProfile? = null) : SessionCheckResult
}

// Local TV pairing models (not API endpoints - used for phone-to-TV session transfer)
@Serializable
data class TvPairingRequest(
    val nonce: String,
    val session: SessionInfo,
)

@Serializable
data class TvPairingResponse(
    val success: Boolean = true,
    val message: String? = null,
)
