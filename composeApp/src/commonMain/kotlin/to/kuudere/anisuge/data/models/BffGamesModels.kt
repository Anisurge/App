package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BffGameStatusResponse(
    val coins: Int = 0,
    val canFreeWheel: Boolean = false,
    val lastFreeWheelDay: String? = null,
    val totalEarned: Int = 0,
    val totalLost: Int = 0,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val gameWins: Int = 0,
    val gameLosses: Int = 0,
    val activeSessions: List<BffActiveGameSession> = emptyList(),
    val config: BffGameConfig = BffGameConfig(),
)

@Serializable
data class BffActiveGameSession(
    val gameId: String = "",
    val gameType: String = "",
    val state: String = "",
    val bet: Int = 0,
    val expiresAt: String = "",
    val createdAt: String = "",
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class BffGameConfig(
    val maxBet: Int = 1000,
    val wheel: BffWheelConfig = BffWheelConfig(),
    val coinFlip: BffCoinFlipConfig = BffCoinFlipConfig(),
    val mines: BffMinesConfig = BffMinesConfig(),
    val crash: BffCrashConfig = BffCrashConfig(),
    val skillGames: BffSkillGamesConfig = BffSkillGamesConfig(),
)

@Serializable
data class BffWheelConfig(
    val paidSpinCost: Int = 5,
    val segments: List<BffWheelSegment> = emptyList(),
)

@Serializable
data class BffWheelSegment(
    val prize: Int = 0,
    val label: String = "",
)

@Serializable
data class BffCoinFlipConfig(
    val payoutMultiplier: Double = 1.85,
    val minBet: Int = 1,
    val maxBet: Int = 1000,
)

@Serializable
data class BffMinesConfig(
    val gridSize: Int = 5,
    val minMines: Int = 10,
    val maxMines: Int = 10,
    val minBet: Int = 1,
    val maxBet: Int = 1000,
    val houseEdge: Double = 0.25,
)

@Serializable
data class BffCrashConfig(
    val minBet: Int = 1,
    val maxBet: Int = 1000,
    val growthPerSecond: Double = 0.42,
    val maxMultiplier: Double = 10.0,
)

@Serializable
data class BffSkillGamesConfig(
    val higherLowerEntryCost: Int = 2,
    val animeGuessEntryCost: Int = 3,
    val animeGuessHintCost: Int = 1,
    val triviaEntryCost: Int = 2,
)

@Serializable
data class BffAnimeGameItem(
    val animeId: String = "",
    val title: String = "",
    val romajiTitle: String = "",
    val nativeTitle: String = "",
    val imageUrl: String = "",
    val bannerUrl: String = "",
    val score: Int = 0,
    val year: Int? = null,
    val format: String = "",
    val genres: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
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
data class BffMinesCreateResponse(
    val gameId: String = "",
    val bet: Int = 0,
    val mineCount: Int = 0,
    val gridSize: Int = 5,
    val revealedTiles: List<Int> = emptyList(),
    val currentMultiplier: Double = 1.0,
    val nextMultiplier: Double = 1.0,
    val state: String = "",
    val coins: Int = 0,
)

@Serializable
data class BffMinesRevealResponse(
    val gameId: String = "",
    val tileIndex: Int = 0,
    val isMine: Boolean = false,
    val state: String = "",
    val revealedTiles: List<Int> = emptyList(),
    val mineTiles: List<Int> = emptyList(),
    val currentMultiplier: Double = 0.0,
    val nextMultiplier: Double = 0.0,
    val winAmount: Int = 0,
    val coins: Int = 0,
)

@Serializable
data class BffMinesCashoutResponse(
    val gameId: String = "",
    val winAmount: Int = 0,
    val coins: Int = 0,
    val state: String = "",
    val multiplier: Double = 0.0,
    val revealedTiles: List<Int> = emptyList(),
    val mineTiles: List<Int> = emptyList(),
)

@Serializable
data class BffCrashStartResponse(
    val gameId: String = "",
    val bet: Int = 0,
    val state: String = "",
    val startedAt: String = "",
    val currentMultiplier: Double = 1.0,
    val growthPerSecond: Double = 0.42,
    val coins: Int = 0,
)

@Serializable
data class BffCrashCashoutResponse(
    val gameId: String = "",
    val crashed: Boolean = false,
    val state: String = "",
    val bet: Int = 0,
    val multiplier: Double = 0.0,
    val crashPoint: Double = 0.0,
    val payout: Int = 0,
    val coins: Int = 0,
)

@Serializable
data class BffHigherLowerStartResponse(
    val gameId: String = "",
    val entryCost: Int = 0,
    val streak: Int = 0,
    val current: BffAnimeGameItem = BffAnimeGameItem(),
    val next: BffAnimeGameItem = BffAnimeGameItem(),
    val coins: Int = 0,
)

@Serializable
data class BffHigherLowerAnswerResponse(
    val gameId: String = "",
    val correct: Boolean = false,
    val answer: String = "",
    val actual: String = "",
    val reward: Int = 0,
    val streak: Int = 0,
    val coins: Int = 0,
    val state: String = "",
    val current: BffAnimeGameItem = BffAnimeGameItem(),
    val next: BffAnimeGameItem? = null,
)

@Serializable
data class BffAnimeGuessChoice(
    val animeId: String = "",
    val title: String = "",
)

@Serializable
data class BffAnimeGuessStartResponse(
    val gameId: String = "",
    val mode: String = "",
    val entryCost: Int = 0,
    val imageUrl: String = "",
    val choices: List<BffAnimeGuessChoice> = emptyList(),
    val hints: List<String> = emptyList(),
    val coins: Int = 0,
)

@Serializable
data class BffAnimeGuessHintResponse(
    val gameId: String = "",
    val hints: List<String> = emptyList(),
    val hintCost: Int = 0,
    val coins: Int = 0,
)

@Serializable
data class BffAnimeGuessAnswerResponse(
    val gameId: String = "",
    val correct: Boolean = false,
    val reward: Int = 0,
    val coins: Int = 0,
    val state: String = "",
    val answer: BffAnimeGameItem = BffAnimeGameItem(),
)

@Serializable
data class BffTriviaStartResponse(
    val gameId: String = "",
    val entryCost: Int = 0,
    val question: String = "",
    val choices: List<String> = emptyList(),
    val coins: Int = 0,
)

@Serializable
data class BffTriviaAnswerResponse(
    val gameId: String = "",
    val correct: Boolean = false,
    val reward: Int = 0,
    val coins: Int = 0,
    val state: String = "",
    val correctIndex: Int = -1,
    val explanation: String = "",
)

@Serializable
data class BffWheelSpinRequest(val freeSpin: Boolean = true)

@Serializable
data class BffCoinFlipRequest(val bet: Int, val choice: String)

@Serializable
data class BffMinesCreateRequest(val bet: Int)

@Serializable
data class BffMinesRevealRequest(val gameId: String, val tileIndex: Int)

@Serializable
data class BffGameIdRequest(val gameId: String)

@Serializable
data class BffCrashStartRequest(val bet: Int)

@Serializable
data class BffHigherLowerAnswerRequest(val gameId: String, val answer: String)

@Serializable
data class BffAnimeGuessStartRequest(val mode: String)

@Serializable
data class BffAnimeGuessAnswerRequest(
    val gameId: String,
    val answer: String = "",
    val animeId: String? = null,
)

@Serializable
data class BffTriviaAnswerRequest(val gameId: String, val choiceIndex: Int)

@Serializable
data object BffEmptyGameRequest

@Serializable
data class BffRetroAiRequest(
    val message: String
)

@Serializable
data class BffRetroAiResponse(
    val body: String,
    val animeSearchQuery: String? = null
)
