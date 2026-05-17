package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class BffIntegrationsPayload(
    val discordToken: String? = null,
    val anilistAccessToken: String? = null,
    val anilistExpiresAt: Long? = null,
    val anilistUsername: String? = null,
    val malAccessToken: String? = null,
    val malRefreshToken: String? = null,
    val malExpiresAt: Long? = null,
    val malUsername: String? = null,
)

@Serializable
data class BffIntegrationsResponse(
    val integrations: BffIntegrationsPayload,
)
