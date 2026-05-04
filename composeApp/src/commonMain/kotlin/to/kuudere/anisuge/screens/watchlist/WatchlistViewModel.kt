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
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val isPaginating: Boolean = false,
    val isOffline: Boolean = false,
)

class WatchlistViewModel : ViewModel() {
    private val watchlistService = AppComponent.watchlistService

    private val _uiState = MutableStateFlow(WatchlistState())
    val uiState: StateFlow<WatchlistState> = _uiState.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var loadedSessionToken: String? = null

    init {
        viewModelScope.launch {
            AppComponent.authService.authState.collect { result ->
                when (result) {
                    is SessionCheckResult.Valid -> {
                        if (loadedSessionToken != result.session.token) {
                            loadedSessionToken = result.session.token
                            refresh()
                        }
                    }
                    SessionCheckResult.NoSession,
                    SessionCheckResult.Expired -> {
                        loadedSessionToken = null
                        _uiState.update { WatchlistState() }
                    }
                    SessionCheckResult.NetworkError -> Unit
                }
            }
        }

        fetchWatchlist()
    }

    fun refresh() {
        _uiState.update { it.copy(items = emptyList(), offset = 0, hasMore = true) }
        fetchWatchlist()
    }

    fun onFolderChange(folder: String) {
        _uiState.update { it.copy(selectedFolder = folder, items = emptyList(), offset = 0, hasMore = true) }
        fetchWatchlist()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(items = emptyList(), offset = 0, hasMore = true) }
            fetchWatchlist()
        }
    }

    fun onGenreToggle(genre: String) {
        _uiState.update { state ->
            val newList = if (state.selectedGenres.contains(genre)) {
                state.selectedGenres - genre
            } else {
                state.selectedGenres + genre
            }
            state.copy(selectedGenres = newList, items = emptyList(), offset = 0, hasMore = true)
        }
        fetchWatchlist()
    }

    fun clearGenres() {
        _uiState.update { it.copy(selectedGenres = emptyList(), items = emptyList(), offset = 0, hasMore = true) }
        fetchWatchlist()
    }

    fun updateFilters(newSort: String? = null, newFormat: String? = null, newStatus: String? = null) {
        _uiState.update {
            it.copy(
                sort = newSort ?: it.sort,
                format = newFormat ?: it.format,
                status = newStatus ?: it.status,
                items = emptyList(),
                offset = 0,
                hasMore = true
            )
        }
        fetchWatchlist()
    }

    fun resetAllFilters() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedFolder = "All lists",
                searchQuery = "",
                sort = "last_updated",
                selectedGenres = emptyList(),
                format = "All formats",
                status = "All statuses",
                items = emptyList(),
                offset = 0,
                hasMore = true
            )
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
            if (!append) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(isPaginating = true, error = null) }
            }
            try {
                val state = _uiState.value
                val folderParam = if (state.selectedFolder == "All" || state.selectedFolder == "All lists") null else state.selectedFolder.toApiFolder()
                val sortParam = if (state.sort == "last_updated" || state.sort == "Recently Updated") null else state.sort
                val genreParam = if (state.selectedGenres.isEmpty()) null else state.selectedGenres.joinToString(",")
                val formatParam = if (state.format == "All formats") null else state.format
                val statusParam = if (state.status == "All statuses") null else state.status

                val offset = if (append) state.offset else 0
                val response = watchlistService.getWatchlist(
                    q = state.searchQuery.ifBlank { null },
                    folder = folderParam,
                    genre = genreParam,
                    format = formatParam,
                    status = statusParam,
                    sort = sortParam,
                    limit = 20,
                    offset = offset,
                )
                if (response != null) {
                    val newItems = response.entries
                    _uiState.update { it.copy(
                        items = if (append) it.items + newItems else newItems,
                        isLoading = false,
                        isPaginating = false,
                        isOffline = false,
                        offset = offset + newItems.size,
                        hasMore = response.hasMore(limit = 20, offset = offset),
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
            val success = watchlistService.updateStatus(animeId, newFolder)
            if (success) {
                val currentFolder = _uiState.value.selectedFolder
                if (currentFolder != "All" && currentFolder != "All lists" && currentFolder != newFolder) {
                    _uiState.update { state ->
                        state.copy(items = state.items.filter { it.activeId != animeId && it.id != animeId })
                    }
                } else {
                    _uiState.update { state ->
                        val updatedItems = state.items.map {
                            if (it.activeId == animeId || it.id == animeId) it.copy(folder = newFolder.toDisplayFolder()) else it
                        }
                        state.copy(items = updatedItems)
                    }
                }
            }
            _uiState.update { it.copy(isUpdating = false) }
        }
    }

    fun removeFromWatchlist(animeId: String) {
        viewModelScope.launch {
            val success = watchlistService.removeFromWatchlist(animeId)
            if (success) {
                _uiState.update { state ->
                    state.copy(items = state.items.filter { it.activeId != animeId && it.id != animeId })
                }
            }
        }
    }

    private fun String.toApiFolder(): String = when (trim().uppercase()) {
        "WATCHING", "CURRENT" -> "CURRENT"
        "ON HOLD", "ON_HOLD", "PAUSED" -> "PAUSED"
        "PLAN TO WATCH", "PLAN_TO_WATCH", "PLANNING" -> "PLANNING"
        "COMPLETED" -> "COMPLETED"
        "DROPPED" -> "DROPPED"
        else -> trim().uppercase()
    }

    private fun String.toDisplayFolder(): String = when (trim().uppercase()) {
        "CURRENT", "WATCHING" -> "Watching"
        "PAUSED", "ON_HOLD", "ON HOLD" -> "On Hold"
        "PLANNING", "PLAN_TO_WATCH", "PLAN TO WATCH" -> "Plan To Watch"
        "COMPLETED" -> "Completed"
        "DROPPED" -> "Dropped"
        else -> this
    }
}
