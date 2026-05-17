package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class BffPfpUploadResponse(
    val url: String = "",
    val user: BffPublicUser? = null,
)
