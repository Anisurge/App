package to.kuudere.anisuge.screens.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.BffCoinFlipResponse
import to.kuudere.anisuge.data.models.BffGameStatusResponse
import to.kuudere.anisuge.data.models.BffMinesCashoutResponse
import to.kuudere.anisuge.data.models.BffMinesCreateResponse
import to.kuudere.anisuge.data.models.BffMinesRevealResponse
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

    fun loadStatus() {
        viewModelScope.launch {
            state = state.copy(loading = true)
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
        state = state.copy(spinning = true, wheelResult = null)
        viewModelScope.launch {
            bffGamesService.spinWheel(freeSpin).fold(
                onSuccess = { result ->
                    state = state.copy(
                        spinning = false,
                        wheelResult = result,
                        status = state.status?.copy(
                            coins = result.coins,
                            canFreeWheel = if (freeSpin) false else (state.status?.canFreeWheel ?: false),
                        ),
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(spinning = false, error = e.message)
                },
            )
        }
    }

    fun flipCoin(bet: Int, choice: String) {
        state = state.copy(flipping = true, coinFlipResult = null)
        viewModelScope.launch {
            bffGamesService.flipCoin(bet, choice).fold(
                onSuccess = { result ->
                    state = state.copy(
                        flipping = false,
                        coinFlipResult = result,
                        status = state.status?.copy(coins = result.coins),
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(flipping = false, error = e.message)
                },
            )
        }
    }

    fun createMines(bet: Int, mines: Int) {
        state = state.copy(creatingMines = true, minesState = null)
        viewModelScope.launch {
            bffGamesService.createMines(bet, mines).fold(
                onSuccess = { result ->
                    state = state.copy(
                        creatingMines = false,
                        minesState = MinesGameState(
                            gameId = result.gameId,
                            bet = result.bet,
                            mineCount = result.mineCount,
                            gridSize = result.gridSize,
                            currentMultiplier = result.currentMultiplier,
                            state = "playing",
                            revealedTiles = emptySet(),
                        ),
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(creatingMines = false, error = e.message)
                },
            )
        }
    }

    fun revealMinesTile(tileIndex: Int) {
        val mines = state.minesState ?: return
        if (mines.state != "playing") return
        if (tileIndex in mines.revealedTiles) return

        state = state.copy(revealingMine = true)
        viewModelScope.launch {
            bffGamesService.revealMinesTile(mines.gameId, tileIndex).fold(
                onSuccess = { result ->
                    val updated = state.minesState?.copy(
                        revealedTiles = state.minesState!!.revealedTiles + tileIndex,
                        currentMultiplier = result.currentMultiplier,
                        state = result.state,
                    )
                    state = state.copy(
                        revealingMine = false,
                        minesState = updated,
                        minesRevealResult = result,
                        error = null,
                    )
                    if (result.state == "won") {
                        state = state.copy(
                            status = state.status?.copy(coins = result.coins),
                        )
                    }
                },
                onFailure = { e ->
                    state = state.copy(revealingMine = false, error = e.message)
                },
            )
        }
    }

    fun cashoutMines() {
        val mines = state.minesState ?: return
        if (mines.state != "playing") return

        state = state.copy(cashingOutMines = true)
        viewModelScope.launch {
            bffGamesService.cashoutMines(mines.gameId).fold(
                onSuccess = { result ->
                    val updated = state.minesState?.copy(
                        state = "cashed_out",
                        currentMultiplier = result.multiplier,
                    )
                    state = state.copy(
                        cashingOutMines = false,
                        minesState = updated,
                        status = state.status?.copy(coins = result.coins),
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(cashingOutMines = false, error = e.message)
                },
            )
        }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearWheelResult() { state = state.copy(wheelResult = null) }
    fun clearCoinFlipResult() { state = state.copy(coinFlipResult = null) }
    fun clearMines() { state = state.copy(minesState = null, minesRevealResult = null) }
}

data class MinesGameState(
    val gameId: String,
    val bet: Int,
    val mineCount: Int,
    val gridSize: Int,
    val currentMultiplier: Double,
    val state: String, // "playing" | "won" | "lost" | "cashed_out"
    val revealedTiles: Set<Int>,
)

data class GamesUiState(
    val loading: Boolean = true,
    val status: BffGameStatusResponse? = null,
    val error: String? = null,
    val spinning: Boolean = false,
    val wheelResult: BffWheelSpinResponse? = null,
    val flipping: Boolean = false,
    val coinFlipResult: BffCoinFlipResponse? = null,
    val creatingMines: Boolean = false,
    val minesState: MinesGameState? = null,
    val minesRevealResult: BffMinesRevealResponse? = null,
    val revealingMine: Boolean = false,
    val cashingOutMines: Boolean = false,
)
