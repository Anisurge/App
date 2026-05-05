package to.kuudere.anisuge.screens.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.services.InfoService

data class AnimeInfoUiState(
    val isLoading: Boolean = true,
    val details: AnimeDetails? = null,
    val error: String? = null,
    val notificationCount: String = "0",
    
    val isLoadingEpisodes: Boolean = false,
    val episodes: List<EpisodeItem> = emptyList(),
    val thumbnails: Map<String, String> = emptyMap(),
    
    val isUpdatingWatchlist: Boolean = false,
    val inWatchlist: Boolean = false,
    val folder: String? = null,
)

class AnimeInfoViewModel(
    private val infoService: InfoService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeInfoUiState())
    val uiState: StateFlow<AnimeInfoUiState> = _uiState.asStateFlow()

    private var currentAnimeId: String? = null

    fun loadAnimeInfo(id: String) {
        if (currentAnimeId == id && !_uiState.value.isLoading) return
        currentAnimeId = id
        
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    episodes = emptyList(),
                    thumbnails = emptyMap(),
                    isLoadingEpisodes = false
                )
            }
            val response = infoService.getAnimeDetails(id)
            if (response != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        details = response,
                        inWatchlist = response.isInWatchlist,
                        folder = response.displayFolder,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load anime details.") }
            }
        }
    }

    fun loadEpisodes() {
        val animeId = currentAnimeId ?: return
        if (_uiState.value.episodes.isNotEmpty() || _uiState.value.isLoadingEpisodes) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            val allEpisodes = infoService.getAllEpisodes(animeId)
            
            _uiState.update { 
                it.copy(
                    episodes = allEpisodes,
                    isLoadingEpisodes = false
                ) 
            }
        }
    }

    private fun loadThumbnails(anilistId: Int) {
        // Thumbnails endpoint removed in Project-R API
    }

    fun updateWatchlist(folder: String) {
        val animeId = currentAnimeId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingWatchlist = true) }
            val success = to.kuudere.anisuge.AppComponent.watchlistService.updateStatus(animeId, folder)
            
            if (success) {
                val displayFolder = when (folder.trim().uppercase()) {
                    "CURRENT", "WATCHING" -> "Watching"
                    "PAUSED", "ON_HOLD", "ON HOLD" -> "On Hold"
                    "PLANNING", "PLAN_TO_WATCH", "PLAN TO WATCH" -> "Plan To Watch"
                    "COMPLETED" -> "Completed"
                    "DROPPED" -> "Dropped"
                    "REMOVE" -> null
                    else -> folder
                }
                _uiState.update {
                    it.copy(
                        isUpdatingWatchlist = false,
                        inWatchlist = folder != "Remove",
                        folder = displayFolder,
                    )
                }
            } else {
                _uiState.update { it.copy(isUpdatingWatchlist = false) }
            }
        }
    }
}
