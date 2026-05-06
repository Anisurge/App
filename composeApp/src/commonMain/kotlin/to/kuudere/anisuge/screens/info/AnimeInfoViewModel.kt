package to.kuudere.anisuge.screens.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.RecommendationItem
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.services.InfoService
import to.kuudere.anisuge.data.services.WatchlistService

data class AnimeInfoUiState(
    val isLoading: Boolean = true,
    val details: AnimeDetails? = null,
    val error: String? = null,
    val notificationCount: String = "0",
    
    val isLoadingEpisodes: Boolean = false,
    val episodes: List<EpisodeItem> = emptyList(),
    
    val isLoadingRecommendations: Boolean = false,
    val recommendations: List<RecommendationItem> = emptyList(),
    
    val isUpdatingWatchlist: Boolean = false,
    val inWatchlist: Boolean = false,
    val folder: String? = null,
)

class AnimeInfoViewModel(
    private val infoService: InfoService,
    private val watchlistService: WatchlistService
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
                    isLoadingEpisodes = false
                )
            }
            try {
                val details = infoService.getAnimeDetails(id)
                if (details != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = details,
                            inWatchlist = details.inWatchlist || details.watchlist != null,
                        folder = details.folder ?: details.watchlist?.folder,
                        episodes = details.episodes ?: emptyList(),
                    )
                }
                loadRecommendations(id)
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load anime details.") }
            }
            } catch (e: Exception) {
                System.err.println("[AnimeInfoVM] loadAnimeInfo error: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace(System.err)
                _uiState.update { it.copy(isLoading = false, error = "Error: ${e.javaClass.simpleName}: ${e.message}") }
            }
        }
    }

    fun loadEpisodes() {
        val animeId = currentAnimeId ?: return
        if (_uiState.value.episodes.isNotEmpty() || _uiState.value.isLoadingEpisodes) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            val response = infoService.getEpisodes(animeId)
            
            if (response != null) {
                _uiState.update { 
                    it.copy(
                        episodes = response.episodes,
                        isLoadingEpisodes = false
                    ) 
                }
            } else {
                _uiState.update { it.copy(isLoadingEpisodes = false) }
            }
        }
    }

    private fun loadRecommendations(slug: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecommendations = true) }
            val response = infoService.getRecommendations(slug)
            if (response != null) {
                _uiState.update { it.copy(recommendations = response.recommendations, isLoadingRecommendations = false) }
            } else {
                _uiState.update { it.copy(isLoadingRecommendations = false) }
            }
        }
    }

    fun updateWatchlist(folder: String) {
        val animeId = currentAnimeId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingWatchlist = true) }
            if (folder == "Remove") {
                val success = watchlistService.removeFromWatchlist(animeId)
                if (success) {
                    _uiState.update {
                        it.copy(
                            isUpdatingWatchlist = false,
                            inWatchlist = false,
                            folder = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(isUpdatingWatchlist = false) }
                }
            } else {
                val response = watchlistService.updateStatus(animeId, folder)
                if (response != null) {
                    _uiState.update {
                        it.copy(
                            isUpdatingWatchlist = false,
                            inWatchlist = true,
                            folder = folder
                        )
                    }
                } else {
                    _uiState.update { it.copy(isUpdatingWatchlist = false) }
                }
            }
        }
    }
}
