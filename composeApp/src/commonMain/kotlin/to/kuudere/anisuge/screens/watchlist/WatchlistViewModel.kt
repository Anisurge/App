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
    val sort: String = "Recently Updated",
    val selectedGenres: List<String> = emptyList(),
    val format: String = "All formats",
    val status: String = "All statuses",
    val items: List<AnimeItem> = emptyList(),
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isPaginating: Boolean = false,
    val isOffline: Boolean = false,
)

class WatchlistViewModel : ViewModel() {
    private val watchlistService = AppComponent.watchlistService

    private val _uiState = MutableStateFlow(WatchlistState())
    val uiState: StateFlow<WatchlistState> = _uiState.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null
    private var loadedSessionId: String? = null

    init {
        viewModelScope.launch {
            AppComponent.authService.authState.collect { result ->
                when (result) {
                    is SessionCheckResult.Valid -> {
                        if (loadedSessionId != result.session.sessionId) {
                            loadedSessionId = result.session.sessionId
                            refresh()
                        }
                    }
                    SessionCheckResult.NoSession,
                    SessionCheckResult.Expired -> {
                        loadedSessionId = null
                        _uiState.update { WatchlistState() }
                    }
                    SessionCheckResult.NetworkError -> Unit
                }
            }
        }

        fetchWatchlist()
    }

    fun refresh() {
        _uiState.update { it.copy(items = emptyList(), currentPage = 1, totalPages = 1) }
        fetchWatchlist(1)
    }

    fun onFolderChange(folder: String) {
        _uiState.update { it.copy(selectedFolder = folder, items = emptyList(), currentPage = 1, totalPages = 1) }
        fetchWatchlist(1)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(items = emptyList(), currentPage = 1, totalPages = 1) }
            fetchWatchlist(1)
        }
    }

    fun onGenreToggle(genre: String) {
        _uiState.update { state ->
            val newList = if (state.selectedGenres.contains(genre)) {
                state.selectedGenres - genre
            } else {
                state.selectedGenres + genre
            }
            state.copy(selectedGenres = newList, items = emptyList(), currentPage = 1, totalPages = 1)
        }
        fetchWatchlist(1)
    }

    fun clearGenres() {
        _uiState.update { it.copy(selectedGenres = emptyList(), items = emptyList(), currentPage = 1, totalPages = 1) }
        fetchWatchlist(1)
    }

    fun updateFilters(newSort: String? = null, newFormat: String? = null, newStatus: String? = null) {
        _uiState.update {
            it.copy(
                sort = newSort ?: it.sort,
                format = newFormat ?: it.format,
                status = newStatus ?: it.status,
                items = emptyList(),
                currentPage = 1,
                totalPages = 1
            )
        }
        fetchWatchlist(1)
    }

    fun resetAllFilters() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedFolder = "All lists",
                searchQuery = "",
                sort = "Recently Updated",
                selectedGenres = emptyList(),
                format = "All formats",
                status = "All statuses",
                items = emptyList(),
                currentPage = 1,
                totalPages = 1
            )
        }
        fetchWatchlist(1)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isPaginating || state.currentPage >= state.totalPages) return
        fetchWatchlist(state.currentPage + 1)
    }

    private fun fetchWatchlist(page: Int = 1) {
        viewModelScope.launch {
            if (page == 1) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(isPaginating = true, error = null) }
            }
            try {
                val state = _uiState.value
                val folderParam = if (state.selectedFolder == "All" || state.selectedFolder == "All lists") null else state.selectedFolder
                val sortParam = if (state.sort == "Recently Updated") null else state.sort
                val genreParam = if (state.selectedGenres.isEmpty()) null else state.selectedGenres.joinToString(",")
                val formatParam = if (state.format == "All formats") null else state.format
                val statusParam = if (state.status == "All statuses") null else state.status

                val response = watchlistService.getWatchlist(
                    page = page, 
                    folder = folderParam,
                    keyword = state.searchQuery,
                    sort = sortParam,
                    genres = genreParam,
                    type = formatParam,
                    status = statusParam
                )
                if (response != null) {
                    _uiState.update { it.copy(
                        items = if (page == 1) response.items else it.items + response.items,
                        isLoading = false,
                        isPaginating = false,
                        isOffline = false,
                        currentPage = response.page,
                        totalPages = response.total
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
            if (response != null && response.success) {
                // AniList Sync
                response.data?.token?.let { token ->
                    response.data.anilist?.let { anilistId ->
                        println("[WatchlistVM] Triggering AniList sync for $anilistId to $newFolder")
                        viewModelScope.launch {
                            val syncResult = AppComponent.aniListService.updateStatus(token, anilistId, newFolder)
                            println("[WatchlistVM] AniList sync result for $anilistId: $syncResult")
                        }
                    } ?: println("[WatchlistVM] No anilistId returned for sync")
                } ?: println("[WatchlistVM] No token returned for sync")

                // Remove from list if current folder doesn't match new status
                val currentFolder = _uiState.value.selectedFolder
                if (currentFolder != "All" && currentFolder != "All lists" && currentFolder != newFolder) {
                    _uiState.update { state -> 
                        state.copy(items = state.items.filter { it.activeId != animeId && it.id != animeId }) 
                    }
                } else {
                    // Update item status in place
                    _uiState.update { state ->
                        val updatedItems = state.items.map { 
                            if (it.activeId == animeId || it.id == animeId) it.copy(folder = newFolder) else it 
                        }
                        state.copy(items = updatedItems)
                    }
                }
            }
            _uiState.update { it.copy(isUpdating = false) }
        }
    }
}
