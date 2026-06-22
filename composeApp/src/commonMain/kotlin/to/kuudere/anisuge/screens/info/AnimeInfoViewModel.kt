package to.kuudere.anisuge.screens.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.RecommendationItem
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.models.AnimeThemeItem
import to.kuudere.anisuge.data.models.FranchiseEntry
import to.kuudere.anisuge.data.models.franchiseRelationRefs
import to.kuudere.anisuge.data.models.toFranchiseEntry
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
    val isLoadingFranchise: Boolean = false,
    val franchiseOrder: List<FranchiseEntry> = emptyList(),
    val isLoadingThemes: Boolean = false,
    val themes: List<AnimeThemeItem> = emptyList(),
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
                
                if (details != null) {
                    val watchlistEntry = try {
                        watchlistService.getWatchlist(limit = 100)?.entries?.firstOrNull { entry ->
                            val entryId = entry.effectiveAnimeId
                            val anime = entry.anime
                            entryId.equals(id, ignoreCase = true) ||
                                entryId.equals(details.animeId, ignoreCase = true) ||
                                (details.anilistId != null && anime.anilistId == details.anilistId) ||
                                (details.malId != null && anime.malId == details.malId)
                        }
                    } catch (e: Exception) {
                        println("[AnimeInfoVM] watchlist lookup error: ${e.message}")
                        null
                    }
                    val resolvedFolder = watchlistEntry?.displayFolder
                        ?: details.folder
                        ?: details.watchlist?.folder
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = details,
                            inWatchlist = resolvedFolder != null,
                            folder = resolvedFolder,
                            episodes = details.episodes ?: emptyList(),
                            episodeProgress = emptyMap(),
                        )
                    }
                    // Fetch progress asynchronously in the background to avoid blocking the UI
                    viewModelScope.launch {
                        refreshWatchProgress()
                    }
                    loadRecommendations(id)
                    loadFranchiseOrder(details)
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

    fun expandThemes() {
        val state = _uiState.value
        if (state.isLoadingThemes || state.themes.isNotEmpty()) return
        val anilistId = state.details?.anilistId ?: return
        if (anilistId <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingThemes = true) }
            val themes = infoService.getAnimeThemes(anilistId)?.themes.orEmpty()
            _uiState.update { it.copy(isLoadingThemes = false, themes = themes) }
        }
    }

    private fun loadFranchiseOrder(root: AnimeDetails) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFranchise = true) }
            val rootId = root.animeId.ifBlank { root.id }
            val visited = linkedSetOf(rootId)
            val entries = linkedMapOf(rootId to root.toFranchiseEntry(isCurrent = true))
            var frontier = root.franchiseRelationRefs().map { it.animeId to it.relationType }

            repeat(3) {
                if (frontier.isEmpty() || entries.size >= 30) return@repeat
                val current = frontier
                    .filter { (id, _) -> id !in visited }
                    .distinctBy { it.first }
                    .take(30 - entries.size)
                current.forEach { visited += it.first }
                val loaded = coroutineScope {
                    current.map { (id, relationType) ->
                        async {
                            Triple(id, relationType, infoService.getAnimeDetails(id, includeEpisodes = false))
                        }
                    }.awaitAll()
                }
                val next = mutableListOf<Pair<String, String?>>()
                loaded.forEach { (requestedId, relationType, details) ->
                    details ?: return@forEach
                    val resolvedId = details.animeId.ifBlank { requestedId }
                    entries[resolvedId] = details.toFranchiseEntry(
                        relationType = relationType,
                        isCurrent = resolvedId == rootId,
                    )
                    details.franchiseRelationRefs().forEach { relation ->
                        if (relation.animeId !in visited) next += relation.animeId to relation.relationType
                    }
                }
                frontier = next
            }

            val sorted = entries.values.sortedWith(
                compareBy<FranchiseEntry>(
                    { it.startDate?.year?.takeIf { year -> year > 0 } ?: Int.MAX_VALUE },
                    { it.startDate?.month?.takeIf { month -> month > 0 } ?: Int.MAX_VALUE },
                    { it.startDate?.day?.takeIf { day -> day > 0 } ?: Int.MAX_VALUE },
                    { it.title.lowercase() },
                )
            )
            _uiState.update { it.copy(isLoadingFranchise = false, franchiseOrder = sorted) }
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
                val details = _uiState.value.details
                val success = watchlistService.removeFromWatchlist(
                    animeId = animeId,
                    anilistId = details?.anilistId,
                    malId = details?.malId,
                )
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
