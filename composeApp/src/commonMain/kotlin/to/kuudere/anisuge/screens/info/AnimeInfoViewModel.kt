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
import to.kuudere.anisuge.data.services.HomeService

data class EpisodeProgress(
    val currentTime: Double,
    val duration: Double
)

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
    val episodeProgress: Map<Int, EpisodeProgress> = emptyMap(),
)

class AnimeInfoViewModel(
    private val infoService: InfoService,
    private val watchlistService: WatchlistService,
    private val homeService: HomeService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeInfoUiState())
    val uiState: StateFlow<AnimeInfoUiState> = _uiState.asStateFlow()

    private var currentAnimeId: String? = null

    fun loadAnimeInfo(id: String) {
        if (currentAnimeId == id && !_uiState.value.isLoading) {
            refreshWatchProgress()
            return
        }
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
                val continueList = runCatching { homeService.fetchAllContinueWatching() }.getOrNull() ?: emptyList()
                val progressMap = continueList
                    .filter { item ->
                        val itemAnimeId = item.animeId
                        val itemAnimeIdStr = item.effectiveAnimeId
                        val itemAnilistId = item.anime.anilistId
                        val itemMalId = item.anime.malId

                        val matchId = itemAnimeId == id || itemAnimeIdStr == id
                        val matchDetailsId = details != null && (itemAnimeId == details.animeId || itemAnimeIdStr == details.animeId)
                        val matchAnilist = details?.anilistId != null && itemAnilistId != null && details.anilistId == itemAnilistId
                        val matchMal = details?.malId != null && itemMalId != null && details.malId == itemMalId

                        matchId || matchDetailsId || matchAnilist || matchMal
                    }
                    .associate { it.displayEpisode to EpisodeProgress(it.progress, it.duration) }

                if (details != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = details,
                            inWatchlist = details.inWatchlist || details.watchlist != null,
                            folder = details.folder ?: details.watchlist?.folder,
                            episodes = details.episodes ?: emptyList(),
                            episodeProgress = progressMap,
                        )
                    }
                    loadRecommendations(id)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load anime details.") }
                }
            } catch (e: Exception) {
                println("[AnimeInfoVM] loadAnimeInfo error: ${(e::class.simpleName ?: "Exception")}: ${e.message}")
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = "Error: ${(e::class.simpleName ?: "Exception")}: ${e.message}") }
            }
        }
    }

    fun refreshWatchProgress() {
        val animeId = currentAnimeId ?: return
        val details = _uiState.value.details
        viewModelScope.launch {
            try {
                val continueList = homeService.fetchAllContinueWatching()
                val progressMap = continueList
                    .filter { item ->
                        val itemAnimeId = item.animeId
                        val itemAnimeIdStr = item.effectiveAnimeId
                        val itemAnilistId = item.anime.anilistId
                        val itemMalId = item.anime.malId

                        val matchId = itemAnimeId == animeId || itemAnimeIdStr == animeId
                        val matchDetailsId = details != null && (itemAnimeId == details.animeId || itemAnimeIdStr == details.animeId)
                        val matchAnilist = details?.anilistId != null && itemAnilistId != null && details.anilistId == itemAnilistId
                        val matchMal = details?.malId != null && itemMalId != null && details.malId == itemMalId

                        matchId || matchDetailsId || matchAnilist || matchMal
                    }
                    .associate { it.displayEpisode to EpisodeProgress(it.progress, it.duration) }
                _uiState.update { it.copy(episodeProgress = progressMap) }
            } catch (e: Exception) {
                println("[AnimeInfoVM] refreshWatchProgress error: ${e.message}")
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
                val details = _uiState.value.details
                val response = watchlistService.updateStatus(
                    animeId = animeId,
                    folder = folder,
                    anilistId = details?.anilistId,
                    malId = details?.malId,
                )
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
