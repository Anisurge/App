package to.kuudere.anisuge.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.utils.isNetworkError

data class WatchlistState(
    val selectedFolder: String = "All lists",
    val searchQuery: String = "",
    val sort: String = "last_updated",
    val selectedGenres: List<String> = emptyList(),
    val format: String = "All formats",
    val status: String = "All statuses",
    val items: List<AnimeItem> = emptyList(),
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val error: String? = null,
    val currentOffset: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = true,
    val isPaginating: Boolean = false,
    val isOffline: Boolean = false,
)

class WatchlistViewModel : ViewModel() {
    private val watchlistService = AppComponent.watchlistService

    private val _uiState = MutableStateFlow(WatchlistState())
    val uiState: StateFlow<WatchlistState> = _uiState.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var loadedToken: String? = null

    init {
        viewModelScope.launch {
            AppComponent.authService.authState.collect { result ->
                when (result) {
                    is SessionCheckResult.Valid -> {
                        if (loadedToken != result.session.token) {
                            loadedToken = result.session.token
                            refresh()
                        }
                    }
                    SessionCheckResult.NoSession,
                    SessionCheckResult.Expired -> {
                        loadedToken = null
                        _uiState.update { WatchlistState() }
                    }
                    SessionCheckResult.NetworkError -> Unit
                }
            }
        }

        fetchWatchlist()
    }

    fun refresh() {
        _uiState.update { it.copy(items = emptyList(), currentOffset = 0, total = 0) }
        fetchWatchlist()
    }

    fun onFolderChange(folder: String) {
        _uiState.update { it.copy(selectedFolder = folder, items = emptyList(), currentOffset = 0, total = 0) }
        fetchWatchlist()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(items = emptyList(), currentOffset = 0, total = 0) }
            fetchWatchlist()
        }
    }

    fun onGenreToggle(genre: String) {
        _uiState.update { state ->
            val newList = if (state.selectedGenres.contains(genre)) state.selectedGenres - genre else state.selectedGenres + genre
            state.copy(selectedGenres = newList, items = emptyList(), currentOffset = 0, total = 0)
        }
        fetchWatchlist()
    }

    fun clearGenres() {
        _uiState.update { it.copy(selectedGenres = emptyList(), items = emptyList(), currentOffset = 0, total = 0) }
        fetchWatchlist()
    }

    fun updateFilters(newSort: String? = null, newFormat: String? = null, newStatus: String? = null) {
        _uiState.update {
            it.copy(sort = newSort ?: it.sort, format = newFormat ?: it.format, status = newStatus ?: it.status, items = emptyList(), currentOffset = 0, total = 0)
        }
        fetchWatchlist()
    }

    fun resetAllFilters() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(selectedFolder = "All lists", searchQuery = "", sort = "last_updated", selectedGenres = emptyList(), format = "All formats", status = "All statuses", items = emptyList(), currentOffset = 0, total = 0)
        }
        fetchWatchlist()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isPaginating || !state.hasMore) return
        fetchWatchlist(append = true)
    }

    private fun fetchWatchlist(append: Boolean = false) {
        viewModelScope.launch {
            val offset = if (append) _uiState.value.currentOffset else 0
            if (!append) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(isPaginating = true, error = null) }
            }
            try {
                val state = _uiState.value
                val folderParam = if (state.selectedFolder == "All" || state.selectedFolder == "All lists") null else state.selectedFolder
                val genreParam = if (state.selectedGenres.isEmpty()) null else state.selectedGenres.joinToString(",")
                val formatParam = if (state.format == "All formats") null else state.format
                val statusParam = if (state.status == "All statuses") null else state.status

                val response = watchlistService.getWatchlist(
                    limit = 20,
                    offset = offset,
                    folder = folderParam,
                    q = state.searchQuery.ifBlank { null },
                    sort = state.sort,
                    genre = genreParam,
                    format = formatParam,
                    status = statusParam
                )
                if (response != null) {
                    _uiState.update { it.copy(
                        items = if (append) it.items + response.results.map { e -> e.anime.copy(folder = e.effectiveFolder) } else response.results.map { e -> e.anime.copy(folder = e.effectiveFolder) },
                        isLoading = false,
                        isPaginating = false,
                        isOffline = false,
                        currentOffset = offset + response.results.size,
                        total = response.total,
                        hasMore = response.results.size >= 20
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, isPaginating = false, isOffline = false, error = "Failed to load watchlist") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isPaginating = false, isOffline = e.isNetworkError(), error = if (e.isNetworkError()) null else e.message) }
            }
        }
    }

    fun updateAnimeStatus(animeId: String, newFolder: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val response = watchlistService.updateStatus(animeId, newFolder)
            if (response != null) {
                val currentFolder = _uiState.value.selectedFolder
                if (currentFolder != "All" && currentFolder != "All lists" && currentFolder != newFolder) {
                    _uiState.update { state -> state.copy(items = state.items.filter { it.activeId != animeId && it.id != animeId }) }
                } else {
                    _uiState.update { state ->
                        val updatedItems = state.items.map { if (it.activeId == animeId || it.id == animeId) it.copy(folder = newFolder) else it }
                        state.copy(items = updatedItems)
                    }
                }
            }
            _uiState.update { it.copy(isUpdating = false) }
        }
    }
}
