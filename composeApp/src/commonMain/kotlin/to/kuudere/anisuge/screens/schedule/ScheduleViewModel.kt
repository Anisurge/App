package to.kuudere.anisuge.screens.schedule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.ScheduleAnime
import to.kuudere.anisuge.data.services.ScheduleService
import to.kuudere.anisuge.data.services.SessionStore
import to.kuudere.anisuge.data.services.WatchlistService
import to.kuudere.anisuge.utils.isNetworkError

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val schedule: Map<String, List<ScheduleAnime>> = emptyMap(),
    val error: String? = null,
    val isOffline: Boolean = false,
    val showWatchlistOnly: Boolean = false,
    val watchlistAnimeIds: Set<String> = emptySet(),
    val isAuthenticated: Boolean = false,
) {
    val displayedSchedule: Map<String, List<ScheduleAnime>> get() {
        if (!showWatchlistOnly || watchlistAnimeIds.isEmpty()) return schedule
        return schedule.mapValues { (_, animeList) ->
            animeList.filter { anime ->
                watchlistAnimeIds.contains(anime.animeId) ||
                watchlistAnimeIds.contains(anime.id) ||
                watchlistAnimeIds.contains(anime.slug)
            }
        }.filterValues { it.isNotEmpty() }
    }
}

class ScheduleViewModel(
    private val scheduleService: ScheduleService,
    private val watchlistService: WatchlistService,
    private val sessionStore: SessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    private var fullSchedule: Map<String, List<ScheduleAnime>> = emptyMap()

    init {
        refresh()
        observeAuth()
    }

    private fun observeAuth() {
        scope.launch {
            sessionStore.sessionFlow.collect { session ->
                val authenticated = session != null
                _uiState.update { it.copy(isAuthenticated = authenticated) }
                if (authenticated) {
                    fetchWatchlistIds()
                } else {
                    _uiState.update { it.copy(watchlistAnimeIds = emptySet()) }
                }
            }
        }
    }

    fun refresh() {
        scope.launch {
            _uiState.update { ScheduleUiState(isLoading = true, showWatchlistOnly = _uiState.value.showWatchlistOnly, isAuthenticated = _uiState.value.isAuthenticated) }
            try {
                val resp = scheduleService.fetchSchedule()
                fullSchedule = resp.scheduleMap
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        schedule = resp.scheduleMap,
                        isOffline = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isOffline = e.isNetworkError(), error = if (e.isNetworkError()) null else e.message) }
            }
        }
        fetchWatchlistIds()
    }

    fun loadMonth(year: Int, month: Int, tz: String = "UTC") {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val resp = scheduleService.fetchSchedule(tz = tz, year = year, month = month)
                fullSchedule = resp.scheduleMap
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        schedule = resp.scheduleMap,
                        isOffline = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isOffline = e.isNetworkError(), error = if (e.isNetworkError()) null else e.message) }
            }
        }
        fetchWatchlistIds()
    }

    fun toggleWatchlistFilter() {
        _uiState.update { it.copy(showWatchlistOnly = !it.showWatchlistOnly) }
    }

    private fun fetchWatchlistIds() {
        scope.launch {
            try {
                var allIds = mutableSetOf<String>()
                var offset = 0
                val limit = 100
                do {
                    val response = watchlistService.getWatchlist(limit = limit, offset = offset)
                    if (response == null) break
                    val entries = response.entries
                    if (entries.isEmpty()) break
                    entries.forEach { item ->
                        allIds.add(item.animeId)
                        allIds.add(item.activeId)
                        allIds.add(item.activeSlug)
                        item.slug?.let { allIds.add(it) }
                    }
                    offset += entries.size
                    if (!response.hasMore(limit, offset - entries.size)) break
                } while (true)

                _uiState.update { it.copy(watchlistAnimeIds = allIds) }
            } catch (e: Exception) {
                println("[ScheduleViewModel] fetchWatchlistIds error: ${e.message}")
            }
        }
    }
}
