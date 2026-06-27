package to.kuudere.anisuge.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.services.SearchFiltersMapper
import to.kuudere.anisuge.data.services.SearchService
import to.kuudere.anisuge.utils.isNetworkError

data class SearchUiState(
    val results: List<AnimeItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentOffset: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = true,
    val keyword: String = "",
    val selectedGenres: List<String> = emptyList(),
    val selectedSeason: String? = null,
    val selectedYear: String? = null,
    val selectedType: String? = null,
    val selectedStatus: String? = null,
    val selectedSort: String? = "Popularity",
    val selectedCountry: String? = null,
    val isOffline: Boolean = false,
)

class SearchViewModel(private val searchService: SearchService) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        search()
    }

    fun applyPreset(
        keyword: String = "",
        selectedGenres: List<String> = emptyList(),
        selectedSeason: String? = null,
        selectedYear: String? = null,
        selectedType: String? = null,
        selectedStatus: String? = null,
        selectedSort: String? = "Popularity",
        selectedCountry: String? = null,
    ) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            keyword = keyword,
            selectedGenres = selectedGenres,
            selectedSeason = selectedSeason,
            selectedYear = selectedYear,
            selectedType = selectedType,
            selectedStatus = selectedStatus,
            selectedSort = selectedSort,
            selectedCountry = selectedCountry,
        )
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

    fun onSortChange(sort: String?) {
        _uiState.value = _uiState.value.copy(selectedSort = sort)
        search()
    }

    fun onCountryChange(country: String?) {
        _uiState.value = _uiState.value.copy(selectedCountry = country)
        search()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedGenres = emptyList(),
            selectedSeason = null,
            selectedYear = null,
            selectedType = null,
            selectedStatus = null,
            selectedSort = "Popularity",
            selectedCountry = null,
        )
        search()
    }

    fun search(loadMore: Boolean = false) = viewModelScope.launch {
        val state = _uiState.value
        if (loadMore) {
            if (state.isLoadingMore || !state.hasMore) return@launch
            _uiState.value = state.copy(isLoadingMore = true)
        } else {
            _uiState.value = state.copy(isLoading = true, results = emptyList(), currentOffset = 0, total = 0)
        }

        val currentState = _uiState.value
        try {
            val response = searchService.search(
                q = currentState.keyword.ifBlank { null },
                limit = 20,
                offset = if (loadMore) currentState.currentOffset else 0,
                format = SearchFiltersMapper.formatForApi(currentState.selectedType),
                status = currentState.selectedStatus?.takeIf { it.isNotBlank() },
                genre = SearchFiltersMapper.genresForApi(currentState.selectedGenres),
                season = SearchFiltersMapper.seasonForApi(currentState.selectedSeason),
                year = SearchFiltersMapper.yearForApi(currentState.selectedYear),
                sort = SearchFiltersMapper.sortForApi(currentState.selectedSort),
                country = SearchFiltersMapper.countryForApi(currentState.selectedCountry),
            )

            if (response != null) {
                val newItems = response.results
                val existingSlugs = if (loadMore) _uiState.value.results.map { it.activeSlug }.toSet() else emptySet()
                val uniqueNew = newItems.filter { it.activeSlug !in existingSlugs }
                val nextOffset = (if (loadMore) currentState.currentOffset else 0) + uniqueNew.size
                val total = response.total
                _uiState.value = _uiState.value.copy(
                    results = if (loadMore) _uiState.value.results + uniqueNew else uniqueNew,
                    isLoading = false,
                    isLoadingMore = false,
                    isOffline = false,
                    total = total,
                    hasMore = nextOffset < total && uniqueNew.isNotEmpty(),
                    currentOffset = nextOffset,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isOffline = false,
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingMore = false,
                isOffline = e.isNetworkError(),
            )
        }
    }
}
