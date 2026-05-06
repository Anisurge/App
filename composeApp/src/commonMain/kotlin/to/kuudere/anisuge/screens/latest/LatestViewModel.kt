package to.kuudere.anisuge.screens.latest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.services.LatestService
import to.kuudere.anisuge.utils.isNetworkError

data class LatestUiState(
    val results: List<AnimeItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    val isOffline: Boolean = false,
)

class LatestViewModel(private val latestService: LatestService) : ViewModel() {
    private val _uiState = MutableStateFlow(LatestUiState())
    val uiState: StateFlow<LatestUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, results = emptyList(), nextCursor = null)
        loadPage(null)
    }

    fun loadMore() = viewModelScope.launch {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return@launch
        _uiState.value = state.copy(isLoadingMore = true)
        loadPage(state.nextCursor)
    }

    private suspend fun loadPage(cursor: String?) {
        try {
            val response = latestService.getLatestAired(cursor = cursor)
            if (response != null) {
                val newItems = response.episodes
                val currentState = _uiState.value
                _uiState.value = currentState.copy(
                    results = if (cursor == null) newItems else currentState.results + newItems,
                    isLoading = false,
                    isLoadingMore = false,
                    isOffline = false,
                    hasMore = response.nextCursor != null,
                    nextCursor = response.nextCursor
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false, isOffline = e.isNetworkError())
        }
    }
}
