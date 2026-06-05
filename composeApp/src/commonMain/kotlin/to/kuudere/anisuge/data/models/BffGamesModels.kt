package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class BffGameStatusResponse(
    val coins: Int = 0,
    val canFreeWheel: Boolean = false,
    val lastFreeWheelDay: String? = null,
    val totalEarned: Int = 0,
    val totalLost: Int = 0,
    val bestStreak: Int = 0,
)

@Serializable
data class BffWheelSpinResponse(
    val prize: Int = 0,
    val prizeLabel: String = "",
    val coins: Int = 0,
    val freeSpin: Boolean = true,
    val cost: Int = 0,
)

@Serializable
data class BffCoinFlipResponse(
    val won: Boolean = false,
    val choice: String = "",
    val result: String = "",
    val bet: Int = 0,
    val payout: Int = 0,
    val coins: Int = 0,
)

@Serializable
data class BffCoinFlipRequest(
    val bet: Int,
    val choice: String,
)

@Serializable
data class BffWheelSpinRequest(
    val freeSpin: Boolean = true,
)
