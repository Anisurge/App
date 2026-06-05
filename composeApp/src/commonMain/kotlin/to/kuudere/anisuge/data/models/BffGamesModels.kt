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

@Serializable
data class BffMinesCreateRequest(
    val bet: Int,
    val mines: Int,
)

@Serializable
data class BffMinesCreateResponse(
    val gameId: String = "",
    val bet: Int = 0,
    val mineCount: Int = 0,
    val gridSize: Int = 5,
    val revealedTiles: List<Int> = emptyList(),
    val currentMultiplier: Double = 1.0,
    val state: String = "",
)

@Serializable
data class BffMinesRevealRequest(
    val gameId: String,
    val tileIndex: Int,
)

@Serializable
data class BffMinesRevealResponse(
    val tileIndex: Int = 0,
    val isMine: Boolean = false,
    val state: String = "",
    val currentMultiplier: Double = 0.0,
    val winAmount: Int = 0,
    val coins: Int = 0,
)

@Serializable
data class BffMinesCashoutRequest(
    val gameId: String,
)

@Serializable
data class BffMinesCashoutResponse(
    val winAmount: Int = 0,
    val coins: Int = 0,
    val state: String = "",
    val multiplier: Double = 0.0,
)
