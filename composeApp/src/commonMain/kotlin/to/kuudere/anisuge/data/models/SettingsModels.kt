package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val autoNext: Boolean = true,
    val autoPlay: Boolean = false,
    val defaultLang: Boolean = true,
    val publicWatchlist: Boolean = false,
    val showComments: Boolean = false,
    val skipIntro: Boolean = false,
    val skipOutro: Boolean = false,
    val syncPercentage: Double = 80.0,
)
