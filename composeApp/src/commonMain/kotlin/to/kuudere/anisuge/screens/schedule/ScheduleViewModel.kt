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
import to.kuudere.anisuge.utils.isNetworkError

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val schedule: Map<String, List<ScheduleAnime>> = emptyMap(),
    val timezone: String = "UTC",
    val year: Int? = null,
    val month: Int? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
)

class ScheduleViewModel(
    private val scheduleService: ScheduleService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    init { refresh() }

    fun refresh() {
        scope.launch {
            _uiState.update { ScheduleUiState(isLoading = true) }
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

    fun setTimezone(tz: String) {
        _uiState.update { it.copy(timezone = tz) }
        refresh()
    }

    fun setYearMonth(year: Int?, month: Int?) {
        _uiState.update { it.copy(year = year, month = month) }
        refresh()
    }
}
