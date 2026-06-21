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
import to.kuudere.anisuge.data.services.WatchlistService
import to.kuudere.anisuge.utils.isNetworkError

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val schedule: Map<String, List<ScheduleAnime>> = emptyMap(),
    val timezone: String = "UTC",
    val year: Int? = null,
    val month: Int? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
    val myListOnly: Boolean = false,
    val isLoadingWatchlist: Boolean = false,
    val watchlistAnimeIds: Set<String> = emptySet(),
    val watchlistAnilistIds: Set<Int> = emptySet(),
    val watchlistMalIds: Set<Int> = emptySet(),
)

internal fun matchesScheduleWatchlist(
    anime: ScheduleAnime,
    animeIds: Set<String>,
    anilistIds: Set<Int>,
    malIds: Set<Int>,
): Boolean =
    anime.anilistId?.let(anilistIds::contains) == true ||
        anime.malId?.let(malIds::contains) == true ||
        sequenceOf(anime.activeSlug, anime.route)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .any(animeIds::contains)

class ScheduleViewModel(
    private val scheduleService: ScheduleService,
    private val watchlistService: WatchlistService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    init {
        refresh()
        refreshWatchlist()
    }

    fun refresh() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = scheduleService.fetchSchedule(tz = _uiState.value.timezone, year = _uiState.value.year, month = _uiState.value.month)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        schedule = resp.schedule.associate { it.date to it.episodes },
                        isOffline = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isOffline = e.isNetworkError(), error = if (e.isNetworkError()) null else e.message) }
            }
        }
    }

    fun refreshWatchlist() {
        scope.launch {
            _uiState.update { it.copy(isLoadingWatchlist = true) }
            try {
                val entries = buildList {
                    var offset = 0
                    do {
                        val response = watchlistService.getWatchlist(limit = 100, offset = offset) ?: break
                        addAll(response.entries)
                        offset += response.entries.size
                    } while (response.entries.isNotEmpty() && offset < response.total)
                }.filterNot { it.displayFolder.equals("DROPPED", ignoreCase = true) }

                _uiState.update { state ->
                    state.copy(
                        isLoadingWatchlist = false,
                        watchlistAnimeIds = entries.mapNotNull { entry ->
                            entry.effectiveAnimeId.trim().lowercase().takeIf(String::isNotBlank)
                        }.toSet(),
                        watchlistAnilistIds = entries.mapNotNull { it.anime.anilistId }.toSet(),
                        watchlistMalIds = entries.mapNotNull { it.anime.malId }.toSet(),
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingWatchlist = false) }
            }
        }
    }

    fun setMyListOnly(enabled: Boolean) {
        _uiState.update { it.copy(myListOnly = enabled) }
    }

    fun isInMyList(anime: ScheduleAnime): Boolean {
        val state = _uiState.value
        return matchesScheduleWatchlist(
            anime = anime,
            animeIds = state.watchlistAnimeIds,
            anilistIds = state.watchlistAnilistIds,
            malIds = state.watchlistMalIds,
        )
    }

    fun updateWatchlist(anime: ScheduleAnime, folder: String) {
        scope.launch {
            val result = watchlistService.updateStatus(
                animeId = anime.activeSlug,
                folder = folder,
                anilistId = anime.anilistId,
                malId = anime.malId,
            )
            if (result != null) {
                val remove = folder.equals("REMOVE", ignoreCase = true) ||
                    folder.equals("DROPPED", ignoreCase = true)
                _uiState.update { state ->
                    state.copy(
                        watchlistAnimeIds = state.watchlistAnimeIds.toMutableSet().apply {
                            sequenceOf(anime.activeSlug, anime.route)
                                .map { it.trim().lowercase() }
                                .filter { it.isNotBlank() }
                                .forEach { if (remove) remove(it) else add(it) }
                        },
                        watchlistAnilistIds = state.watchlistAnilistIds.toMutableSet().apply {
                            anime.anilistId?.let { if (remove) remove(it) else add(it) }
                        },
                        watchlistMalIds = state.watchlistMalIds.toMutableSet().apply {
                            anime.malId?.let { if (remove) remove(it) else add(it) }
                        },
                    )
                }
            }
        }
    }

    fun visibleSchedule(): Map<String, List<ScheduleAnime>> {
        val state = _uiState.value
        if (!state.myListOnly) return state.schedule
        return state.schedule.mapValues { (_, episodes) -> episodes.filter(::isInMyList) }
    }

    fun setTimezone(tz: String) {
        _uiState.update { it.copy(timezone = tz) }
        refresh()
    }

    fun setYearMonth(year: Int?, month: Int?) {
        _uiState.update { it.copy(year = year, month = month) }
        refresh()
    }
}
