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
                    state = state.copy(
                        loading = false,
                        status = status,
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(
                        loading = false,
                        error = e.message ?: "Failed to load game status",
                    )
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
                        status = state.status?.copy(coins = result.coins, canFreeWheel = freeSpin && result.prize > 0),
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(
                        spinning = false,
                        error = e.message ?: "Spin failed",
                    )
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
                    state = state.copy(
                        flipping = false,
                        error = e.message ?: "Flip failed",
                    )
                },
            )
        }
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    fun clearWheelResult() {
        state = state.copy(wheelResult = null)
    }

    fun clearCoinFlipResult() {
        state = state.copy(coinFlipResult = null)
    }
}

data class GamesUiState(
    val loading: Boolean = true,
    val status: BffGameStatusResponse? = null,
    val error: String? = null,
    val spinning: Boolean = false,
    val wheelResult: BffWheelSpinResponse? = null,
    val flipping: Boolean = false,
    val coinFlipResult: BffCoinFlipResponse? = null,
)
