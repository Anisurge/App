package to.kuudere.anisuge.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.services.SearchService
import to.kuudere.anisuge.utils.isNetworkError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class SearchUiState(
    val results: List<AnimeItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val keyword: String = "",
    val selectedGenres: List<String> = emptyList(),
    val selectedSeason: String? = null,
    val selectedYear: String? = null,
    val selectedType: String? = null,
    val selectedStatus: String? = null,
    val selectedLanguage: String? = null,
    val selectedRating: String? = null,
    val selectedSort: String? = "Popularity",
    val isOffline: Boolean = false,
)

class SearchViewModel(private val searchService: SearchService) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Initial search
        search()
    }

    fun onKeywordChange(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            search()
        }
    }

    fun onGenreToggle(genre: String) {
        val current = _uiState.value.selectedGenres
        val next = if (current.contains(genre)) current - genre else current + genre
        _uiState.value = _uiState.value.copy(selectedGenres = next)
        search()
    }

    fun clearGenres() {
        _uiState.value = _uiState.value.copy(selectedGenres = emptyList())
        search()
    }

    fun onSeasonChange(season: String?) {
        _uiState.value = _uiState.value.copy(selectedSeason = season)
        search()
    }

    fun onYearChange(year: String?) {
        _uiState.value = _uiState.value.copy(selectedYear = year)
        search()
    }

    fun onTypeChange(type: String?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        search()
    }

    fun onStatusChange(status: String?) {
        _uiState.value = _uiState.value.copy(selectedStatus = status)
        search()
    }

    fun onLanguageChange(lang: String?) {
        _uiState.value = _uiState.value.copy(selectedLanguage = lang)
        search()
    }

    fun onRatingChange(rating: String?) {
        _uiState.value = _uiState.value.copy(selectedRating = rating)
        search()
    }

    fun onSortChange(sort: String?) {
        _uiState.value = _uiState.value.copy(selectedSort = sort)
        search()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedGenres = emptyList(),
            selectedSeason = null,
            selectedYear = null,
            selectedType = null,
            selectedStatus = null,
            selectedLanguage = null,
            selectedRating = null,
            selectedSort = "Popularity"
        )
        search()
    }

    fun search(loadMore: Boolean = false) = viewModelScope.launch {
        val state = _uiState.value
        if (loadMore) {
            if (state.isLoadingMore || !state.hasMore) return@launch
            _uiState.value = state.copy(isLoadingMore = true)
        } else {
            _uiState.value = state.copy(isLoading = true, results = emptyList(), currentPage = 1)
        }

        val currentState = _uiState.value
        try {
            val response = searchService.search(
                q = currentState.keyword.ifBlank { null },
                offset = (currentState.currentPage - 1) * 20,
                genres = currentState.selectedGenres.takeIf { it.isNotEmpty() }?.joinToString(","),
                format = currentState.selectedType,
                status = currentState.selectedStatus,
            )

            if (response != null && response.success) {
                val newItems = response.items
                val hasMore = response.hasMore || (response.offset + response.limit < response.total)
                _uiState.value = _uiState.value.copy(
                    results = if (loadMore) _uiState.value.results + newItems else newItems,
                    isLoading = false,
                    isLoadingMore = false,
                    isOffline = false,
                    hasMore = hasMore,
                    currentPage = if (newItems.isNotEmpty() && hasMore) currentState.currentPage + 1 else currentState.currentPage
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isOffline = false
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingMore = false,
                isOffline = e.isNetworkError()
            )
        }
    }
}
