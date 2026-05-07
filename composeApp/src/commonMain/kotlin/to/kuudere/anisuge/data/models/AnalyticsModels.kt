package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsPingRequest(
    val installId: String,
    val platform: String,
    val appVersion: String,
    val os: String? = null,
)
