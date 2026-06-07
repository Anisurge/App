package to.kuudere.anisuge.screens.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.BffAnimeGameItem
import to.kuudere.anisuge.data.models.BffAnimeGuessAnswerResponse
import to.kuudere.anisuge.data.models.BffAnimeGuessChoice
import to.kuudere.anisuge.data.models.BffCoinFlipResponse
import to.kuudere.anisuge.data.models.BffCrashCashoutResponse
import to.kuudere.anisuge.data.models.BffGameStatusResponse
import to.kuudere.anisuge.data.models.BffHigherLowerAnswerResponse
import to.kuudere.anisuge.data.models.BffMinesCashoutResponse
import to.kuudere.anisuge.data.models.BffMinesRevealResponse
import to.kuudere.anisuge.data.models.BffTriviaAnswerResponse
import to.kuudere.anisuge.data.models.BffWheelSpinResponse
import to.kuudere.anisuge.data.services.BffGamesService

class GamesViewModel(
    private val bffGamesService: BffGamesService = AppComponent.bffGamesService,
) : ViewModel() {

    var state by mutableStateOf(GamesUiState())
        private set

    init {
        loadStatus()
    }

    fun loadStatus(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) state = state.copy(loading = true)
            bffGamesService.fetchStatus().fold(
                onSuccess = { status ->
                    state = state.copy(loading = false, status = status, error = null)
                },
                onFailure = { e ->
                    state = state.copy(loading = false, error = e.message)
                },
            )
        }
    }

    fun spinWheel(freeSpin: Boolean) {
        state = state.copy(busyAction = "wheel", wheelResult = null, error = null)
        viewModelScope.launch {
            bffGamesService.spinWheel(freeSpin).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        wheelResult = result,
                        status = state.status?.copy(
                            coins = result.coins,
                            canFreeWheel = if (freeSpin) false else state.status?.canFreeWheel ?: false,
                        ),
                    )
                    delay(2300)
                    if (state.wheelResult == result) {
                        state = state.copy(
                            toast = if (result.prize > 0) "+${result.prize} Berries" else "No prize this spin",
                        )
                    }
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun flipCoin(bet: Int, choice: String) {
        state = state.copy(busyAction = "coin", coinFlipResult = null, error = null)
        viewModelScope.launch {
            bffGamesService.flipCoin(bet, choice).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        coinFlipResult = result,
                        status = state.status?.copy(coins = result.coins),
                    )
                    delay(1400)
                    if (state.coinFlipResult == result) {
                        state = state.copy(
                            toast = if (result.won) "Won ${result.payout} Berries" else "Lost $bet Berries",
                        )
                    }
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun createMines(bet: Int) {
        state = state.copy(busyAction = "mines-start", minesState = null, minesRevealResult = null, minesCashoutResult = null, error = null)
        viewModelScope.launch {
            bffGamesService.createMines(bet).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        minesState = MinesGameState(
                            gameId = result.gameId,
                            bet = result.bet,
                            mineCount = result.mineCount,
                            gridSize = result.gridSize,
                            currentMultiplier = result.currentMultiplier,
                            nextMultiplier = result.nextMultiplier,
                            state = result.state,
                            revealedTiles = result.revealedTiles.toSet(),
                            mineTiles = emptySet(),
                        ),
                        status = state.status?.copy(coins = result.coins),
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun revealMinesTile(tileIndex: Int) {
        val mines = state.minesState ?: return
        if (mines.state != "playing" || tileIndex in mines.revealedTiles || state.busyAction != null) return

        state = state.copy(busyAction = "mines-reveal", error = null)
        viewModelScope.launch {
            bffGamesService.revealMinesTile(mines.gameId, tileIndex).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        minesState = mines.copy(
                            currentMultiplier = result.currentMultiplier,
                            nextMultiplier = result.nextMultiplier,
                            state = result.state,
                            revealedTiles = result.revealedTiles.toSet(),
                            mineTiles = result.mineTiles.toSet(),
                        ),
                        minesRevealResult = result,
                        status = if (result.state != "playing") state.status?.copy(coins = result.coins) else state.status,
                        toast = when {
                            result.isMine -> "Mine hit. Bet lost."
                            result.state == "won" -> "Board cleared: +${result.winAmount}"
                            else -> "Safe tile"
                        },
                    )
                    if (result.state != "playing") loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun cashoutMines() {
        val mines = state.minesState ?: return
        if (mines.state != "playing" || state.busyAction != null) return

        state = state.copy(busyAction = "mines-cashout", error = null)
        viewModelScope.launch {
            bffGamesService.cashoutMines(mines.gameId).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        minesState = mines.copy(
                            state = result.state,
                            currentMultiplier = result.multiplier,
                            nextMultiplier = 0.0,
                            revealedTiles = result.revealedTiles.toSet(),
                            mineTiles = result.mineTiles.toSet(),
                        ),
                        minesCashoutResult = result,
                        status = state.status?.copy(coins = result.coins),
                        toast = "Cashed out ${result.winAmount} Berries",
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun startCrash(bet: Int) {
        state = state.copy(busyAction = "crash-start", crashState = null, crashResult = null, error = null)
        viewModelScope.launch {
            bffGamesService.startCrash(bet).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        crashState = CrashGameState(
                            gameId = result.gameId,
                            bet = result.bet,
                            startedAt = result.startedAt,
                            growthPerSecond = result.growthPerSecond,
                            currentMultiplier = result.currentMultiplier,
                            state = result.state,
                        ),
                        status = state.status?.copy(coins = result.coins),
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun cashoutCrash() {
        val crash = state.crashState ?: return
        if (crash.state != "playing" || state.busyAction != null) return
        state = state.copy(busyAction = "crash-cashout", error = null)
        viewModelScope.launch {
            bffGamesService.cashoutCrash(crash.gameId).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        crashState = crash.copy(state = result.state, currentMultiplier = result.multiplier),
                        crashResult = result,
                        status = state.status?.copy(coins = result.coins),
                        toast = if (result.crashed) "Crashed at x${result.crashPoint}" else "Cashed x${result.multiplier}",
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun startHigherLower() {
        state = state.copy(busyAction = "higher-start", higherLowerState = null, higherLowerResult = null, error = null)
        viewModelScope.launch {
            bffGamesService.startHigherLower().fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        higherLowerState = HigherLowerGameState(
                            gameId = result.gameId,
                            current = result.current,
                            next = result.next,
                            streak = result.streak,
                            state = "playing",
                        ),
                        status = state.status?.copy(coins = result.coins),
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun answerHigherLower(answer: String) {
        val game = state.higherLowerState ?: return
        if (game.state != "playing" || state.busyAction != null) return
        state = state.copy(busyAction = "higher-answer", error = null)
        viewModelScope.launch {
            bffGamesService.answerHigherLower(game.gameId, answer).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        higherLowerResult = result,
                        higherLowerState = game.copy(
                            current = result.current,
                            next = result.next ?: result.next ?: game.next,
                            streak = result.streak,
                            state = result.state,
                        ),
                        status = state.status?.copy(coins = result.coins),
                        toast = if (result.correct) "Correct: +${result.reward}" else "Wrong. ${result.next?.title ?: game.next.title} was ${result.actual}.",
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun startAnimeGuess(mode: String) {
        state = state.copy(busyAction = "guess-start", animeGuessState = null, animeGuessResult = null, error = null)
        viewModelScope.launch {
            bffGamesService.startAnimeGuess(mode).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        animeGuessState = AnimeGuessGameState(
                            gameId = result.gameId,
                            mode = result.mode,
                            imageUrl = result.imageUrl,
                            choices = result.choices,
                            hints = result.hints,
                            state = "playing",
                        ),
                        status = state.status?.copy(coins = result.coins),
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun revealGuessHint() {
        val game = state.animeGuessState ?: return
        if (game.state != "playing" || state.busyAction != null) return
        state = state.copy(busyAction = "guess-hint", error = null)
        viewModelScope.launch {
            bffGamesService.revealAnimeGuessHint(game.gameId).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        animeGuessState = game.copy(hints = result.hints),
                        status = state.status?.copy(coins = result.coins),
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun answerAnimeGuess(answer: String = "", animeId: String? = null) {
        val game = state.animeGuessState ?: return
        if (game.state != "playing" || state.busyAction != null) return
        state = state.copy(busyAction = "guess-answer", error = null)
        viewModelScope.launch {
            bffGamesService.answerAnimeGuess(game.gameId, answer, animeId).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        animeGuessResult = result,
                        animeGuessState = game.copy(state = result.state),
                        status = state.status?.copy(coins = result.coins),
                        toast = if (result.correct) "Correct: ${result.answer.title}" else "It was ${result.answer.title}",
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun startTrivia() {
        state = state.copy(busyAction = "trivia-start", triviaState = null, triviaResult = null, error = null)
        viewModelScope.launch {
            bffGamesService.startTrivia().fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        triviaState = TriviaGameState(
                            gameId = result.gameId,
                            question = result.question,
                            choices = result.choices,
                            state = "playing",
                        ),
                        status = state.status?.copy(coins = result.coins),
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun answerTrivia(choiceIndex: Int) {
        val game = state.triviaState ?: return
        if (game.state != "playing" || state.busyAction != null) return
        state = state.copy(busyAction = "trivia-answer", error = null)
        viewModelScope.launch {
            bffGamesService.answerTrivia(game.gameId, choiceIndex).fold(
                onSuccess = { result ->
                    state = state.copy(
                        busyAction = null,
                        triviaResult = result,
                        triviaState = game.copy(state = result.state, selectedIndex = choiceIndex),
                        status = state.status?.copy(coins = result.coins),
                        toast = if (result.correct) "Correct: +${result.reward}" else "Wrong answer",
                    )
                    loadStatus(quiet = true)
                },
                onFailure = { e -> fail(e.message) },
            )
        }
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    fun clearToast() {
        state = state.copy(toast = null)
    }

    fun resetMines() {
        state = state.copy(minesState = null, minesRevealResult = null, minesCashoutResult = null)
    }

    fun resetCrash() {
        state = state.copy(crashState = null, crashResult = null)
    }

    fun resetHigherLower() {
        state = state.copy(higherLowerState = null, higherLowerResult = null)
    }

    fun resetAnimeGuess() {
        state = state.copy(animeGuessState = null, animeGuessResult = null)
    }

    fun resetTrivia() {
        state = state.copy(triviaState = null, triviaResult = null)
    }

    fun sendRetroAiMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty() || state.retroAiBusy) return
        val userMsg = RetroAiMessage(body = trimmed, isUser = true)
        val updatedChat = state.retroAiChat + userMsg
        state = state.copy(retroAiChat = updatedChat, retroAiBusy = true, error = null)

        viewModelScope.launch {
            bffGamesService.queryRetroAi(trimmed).fold(
                onSuccess = { res ->
                    val botMsg = RetroAiMessage(
                        body = res.body,
                        isUser = false,
                        animeSearchQuery = res.animeSearchQuery?.takeIf { it.isNotBlank() }
                    )
                    state = state.copy(
                        retroAiChat = state.retroAiChat + botMsg,
                        retroAiBusy = false
                    )
                },
                onFailure = { e ->
                    val errBotMsg = RetroAiMessage(
                        body = "Sorry, my retro tape got tangled! Please try saying that again.",
                        isUser = false
                    )
                    state = state.copy(
                        retroAiChat = state.retroAiChat + errBotMsg,
                        retroAiBusy = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun resetRetroAi() {
        state = state.copy(retroAiChat = emptyList(), retroAiBusy = false)
    }

    private fun fail(message: String?) {
        state = state.copy(busyAction = null, error = message ?: "Request failed")
    }
}

data class MinesGameState(
    val gameId: String,
    val bet: Int,
    val mineCount: Int,
    val gridSize: Int,
    val currentMultiplier: Double,
    val nextMultiplier: Double,
    val state: String,
    val revealedTiles: Set<Int>,
    val mineTiles: Set<Int>,
)

data class CrashGameState(
    val gameId: String,
    val bet: Int,
    val startedAt: String,
    val growthPerSecond: Double,
    val currentMultiplier: Double,
    val state: String,
)

data class HigherLowerGameState(
    val gameId: String,
    val current: BffAnimeGameItem,
    val next: BffAnimeGameItem,
    val streak: Int,
    val state: String,
)

data class AnimeGuessGameState(
    val gameId: String,
    val mode: String,
    val imageUrl: String,
    val choices: List<BffAnimeGuessChoice>,
    val hints: List<String>,
    val state: String,
)

data class TriviaGameState(
    val gameId: String,
    val question: String,
    val choices: List<String>,
    val state: String,
    val selectedIndex: Int? = null,
)

data class GamesUiState(
    val loading: Boolean = true,
    val status: BffGameStatusResponse? = null,
    val error: String? = null,
    val toast: String? = null,
    val busyAction: String? = null,
    val wheelResult: BffWheelSpinResponse? = null,
    val coinFlipResult: BffCoinFlipResponse? = null,
    val minesState: MinesGameState? = null,
    val minesRevealResult: BffMinesRevealResponse? = null,
    val minesCashoutResult: BffMinesCashoutResponse? = null,
    val crashState: CrashGameState? = null,
    val crashResult: BffCrashCashoutResponse? = null,
    val higherLowerState: HigherLowerGameState? = null,
    val higherLowerResult: BffHigherLowerAnswerResponse? = null,
    val animeGuessState: AnimeGuessGameState? = null,
    val animeGuessResult: BffAnimeGuessAnswerResponse? = null,
    val triviaState: TriviaGameState? = null,
    val triviaResult: BffTriviaAnswerResponse? = null,
    val retroAiChat: List<RetroAiMessage> = emptyList(),
    val retroAiBusy: Boolean = false,
)

data class RetroAiMessage(
    val body: String,
    val isUser: Boolean,
    val animeSearchQuery: String? = null
)
