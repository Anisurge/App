package to.kuudere.anisuge.screens.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.ContinueWatchingItem
import to.kuudere.anisuge.data.models.LayoutConfig
import to.kuudere.anisuge.data.models.RowId
import to.kuudere.anisuge.data.models.toAnimeItem
import to.kuudere.anisuge.data.services.HomeService
import to.kuudere.anisuge.data.services.SearchService
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.services.AuthService
import kotlinx.coroutines.flow.update
import to.kuudere.anisuge.data.services.LibrarySyncService
import to.kuudere.anisuge.data.services.SettingsStore
import to.kuudere.anisuge.data.services.SessionStore
import to.kuudere.anisuge.data.services.WatchlistService
import to.kuudere.anisuge.utils.latestPerAnime
import to.kuudere.anisuge.utils.sortedByRecent

data class HomeUiState(
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val isOffline: Boolean = false,
    val userProfile: UserProfile? = null,
    val latestAired: List<AnimeItem> = emptyList(),
    val newOnSite: List<AnimeItem> = emptyList(),
    val upcoming: List<AnimeItem> = emptyList(),
    val trendingWeek: List<AnimeItem> = emptyList(),
    val newSeasons: List<AnimeItem> = emptyList(),
    val recommended: List<AnimeItem> = emptyList(),
    val hiddenGems: List<AnimeItem> = emptyList(),
    /** Home row: one entry per anime (latest episode). */
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    /** Full list screen source: every saved episode row from BFF. */
    val continueWatchingAll: List<ContinueWatchingItem> = emptyList(),
    val layout: LayoutConfig = LayoutConfig.DEFAULT,
    val isUpdatingWatchlist: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val homeService: HomeService,
    private val authService: AuthService,
    private val watchlistService: WatchlistService,
    private val sessionStore: SessionStore,
    private val librarySyncService: LibrarySyncService,
    private val settingsStore: SettingsStore,
    private val searchService: SearchService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var homeDataJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        scope.launch {
            settingsStore.homeLayoutFlow.collect { layout ->
                _uiState.update { it.copy(layout = layout) }
            }
        }
        scope.launch {
            val firstLayout = settingsStore.homeLayoutFlow.first()
            settingsStore.healHomeLayoutIfNeeded(firstLayout)
        }
        scope.launch {
            authService.authState.collect { result ->
                val userProfile = (result as? SessionCheckResult.Valid)?.user
                _uiState.update { it.copy(userProfile = userProfile) }

                if (userProfile != null) {
                    if (!itHasHomeContent()) {
                        loadHomeData(showLoading = true)
                    }
                    fetchPersonalizedData()
                }
            }
        }
        refresh()
    }

    fun visibleRowsForMobile(state: HomeUiState = _uiState.value): List<RowId> =
        state.layout.rows.filter { it.visible }.map { it.id }

    fun visibleRowsForTv(state: HomeUiState = _uiState.value): List<RowId> =
        state.layout.rows.filter { it.visible && it.id in RowId.TV_SUPPORTED }.map { it.id }

    private fun itHasHomeContent(): Boolean {
        val state = _uiState.value
        return state.latestAired.isNotEmpty() ||
                state.newOnSite.isNotEmpty() ||
                state.upcoming.isNotEmpty()
    }

    private fun loadHomeData(force: Boolean = false, showLoading: Boolean = false) {
        homeDataJob?.cancel()
        homeDataJob = scope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, isOffline = false, error = null) }
            }

            try {
                val homeData = homeService.fetchHomeData(forceRefresh = force)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOffline = false,
                        error = null,
                        latestAired = homeData?.latestAired ?: emptyList(),
                        newOnSite = homeData?.newOnSite ?: emptyList(),
                        upcoming = homeData?.upcoming ?: emptyList(),
                    )
                }
            } catch (e: Exception) {
                handleHomeLoadError(e)
            }
        }
        loadDiscoveryRows()
    }

    /**
     * Loads the non-personalized discovery rows (Trending This Week, New Seasons,
     * Hidden Gems) independently of the main /home payload so a slow/failed row
     * never blocks the rest of the page. Each failure leaves its row empty, which
     * [HomeUiState.hasDataForRow] then hides.
     */
    private fun loadDiscoveryRows() {
        scope.launch {
            val top = async {
                discoveryRequest("trending anime") {
                    homeService.fetchTopAnime(period = "week", limit = 20)?.data
                }.orEmpty()
            }
            val (season, year) = currentSeasonAndYear()
            val seasons = async {
                discoveryRequest("new seasons") {
                    searchService.search(
                        season = season,
                        year = year,
                        sort = "popularity_desc",
                        limit = 24,
                    )?.results
                }.orEmpty()
            }
            val gems = async {
                discoveryRequest("hidden gems") {
                    searchService.search(
                        sort = "score_desc",
                        status = "Finished",
                        limit = 40,
                    )?.results
                }.orEmpty()
            }

            val topItems = top.await()
            val seasonItems = seasons.await()
            // Hidden gems = highly rated but under a popularity threshold (underrated).
            val gemItems = gems.await()
                .filter { (it.popularity ?: Int.MAX_VALUE) < HIDDEN_GEM_MAX_POPULARITY }
                .take(20)

            _uiState.update {
                it.copy(
                    trendingWeek = if (topItems.isNotEmpty()) topItems else it.trendingWeek,
                    newSeasons = if (seasonItems.isNotEmpty()) seasonItems else it.newSeasons,
                    hiddenGems = if (gemItems.isNotEmpty()) gemItems else it.hiddenGems,
                )
            }
        }
    }

    private suspend fun <T> discoveryRequest(label: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[HomeVM] Failed to fetch $label: ${e.message}")
            null
        }
    }

    /** Recommendations seeded from the user's most recently watched anime. */
    private suspend fun loadRecommendations() {
        val seedSlug = _uiState.value.continueWatching.firstOrNull()
            ?.let { it.effectiveAnimeId.ifBlank { it.animeId } }
            ?.takeIf { it.isNotBlank() }
            ?: return
        try {
            val recs = homeService.fetchRecommendations(seedSlug)
                ?.recommendations
                .orEmpty()
                .map { it.toAnimeItem() }
            if (recs.isNotEmpty()) {
                _uiState.update { it.copy(recommended = recs) }
            }
        } catch (e: Exception) {
            println("[HomeVM] Failed to fetch recommendations: ${e.message}")
        }
    }

    private fun fetchPersonalizedData() {
        scope.launch { loadContinueWatching() }
    }

    fun refreshContinueWatching() {
        fetchPersonalizedData()
    }

    fun refreshAllContinueWatching() {
        scope.launch {
            try {
                val all = homeService.fetchAllContinueWatching().sortedByRecent()
                applyContinueWatching(all)
            } catch (e: Exception) {
                println("[HomeVM] Failed to fetch full continue history: ${e.message}")
            }
        }
    }

    private suspend fun loadContinueWatching() {
        val session = sessionStore.get() ?: return
        if (!sessionStore.isValid(session) || authService.authState.value !is SessionCheckResult.Valid) {
            return
        }

        try {
            val latest = homeService.fetchHomeContinueWatching().sortedByRecent()
            applyContinueWatching(latest)
        } catch (e: Exception) {
            println("[HomeVM] Failed to fetch continue watching: ${e.message}")
        }
    }

    /** Pull ReAnime library merge then refresh continue (watchlist screen / explicit refresh). */
    fun syncLibraryAndRefreshContinue() {
        scope.launch {
            val synced = librarySyncService.syncWithReanime(force = true)
            if (synced) {
                try {
                    val all = homeService.fetchAllContinueWatching().sortedByRecent()
                    applyContinueWatching(all)
                } catch (e: Exception) {
                    println("[HomeVM] Failed to refresh continue after sync: ${e.message}")
                }
            }
        }
    }

    private fun applyContinueWatching(
        all: List<ContinueWatchingItem>,
        loadRecommendations: Boolean = true,
    ) {
        _uiState.update {
            it.copy(
                continueWatchingAll = all,
                continueWatching = all.latestPerAnime(),
            )
        }
        if (loadRecommendations) {
            scope.launch { loadRecommendations() }
        }
    }

    fun removeContinueItem(item: ContinueWatchingItem) {
        val animeId = item.effectiveAnimeId.ifBlank { item.animeId }
        val episodeId = item.episodeId.ifBlank { item.displayEpisode.toString() }
        val previous = _uiState.value.continueWatchingAll
        applyContinueWatching(previous.filterNot { existing ->
            val existingAnimeId = existing.effectiveAnimeId.ifBlank { existing.animeId }
            val existingEpisodeId = existing.episodeId.ifBlank { existing.displayEpisode.toString() }
            existingAnimeId == animeId && existingEpisodeId == episodeId
        })
        scope.launch {
            val deleted = homeService.deleteContinueWatching(animeId, episodeId)
            if (!deleted) {
                applyContinueWatching(previous)
            } else {
                loadContinueWatching()
            }
        }
    }

    fun refresh(force: Boolean = false) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, isOffline = false, error = null) }
            try {
                val authCheck = async { authService.checkSession() }
                val homeData = async { homeService.fetchHomeData(forceRefresh = force) }
                loadDiscoveryRows()
                homeData.await()?.let { data ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            error = null,
                            latestAired = data.latestAired,
                            newOnSite = data.newOnSite,
                            upcoming = data.upcoming,
                        )
                    }
                } ?: _uiState.update { it.copy(isLoading = false) }

                when (val authResult = authCheck.await()) {
                    is SessionCheckResult.Valid -> {
                        _uiState.update { it.copy(userProfile = authResult.user) }
                        if (force) {
                            syncLibraryAndRefreshContinue()
                        } else {
                            loadContinueWatching()
                        }
                    }

                    else -> Unit
                }
            } catch (e: Exception) {
                handleHomeLoadError(e)
            }
        }
    }

    private fun handleHomeLoadError(e: Exception) {
        val isNetworkError = generateSequence(e as Throwable) { it.cause }.any { cause ->
            val msg = cause.message ?: ""
            msg.contains("UnknownHostException", ignoreCase = true)
                    || msg.contains("ConnectException", ignoreCase = true)
                    || msg.contains("SocketTimeoutException", ignoreCase = true)
                    || msg.contains("NoRouteToHostException", ignoreCase = true)
                    || msg.contains("Unable to resolve host", ignoreCase = true)
                    || msg.contains("Failed to connect", ignoreCase = true)
                    || msg.contains("timeout", ignoreCase = true)
                    || msg.contains("Network is unreachable", ignoreCase = true)
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isOffline = isNetworkError,
                error = if (isNetworkError) null else e.message
            )
        }
    }

    fun updateWatchlist(
        animeId: String,
        folder: String,
        anilistId: Int? = null,
        malId: Int? = null,
    ) {
        scope.launch {
            _uiState.update { it.copy(isUpdatingWatchlist = true) }
            try {
                watchlistService.updateStatus(
                    animeId = animeId,
                    folder = folder,
                    anilistId = anilistId,
                    malId = malId,
                )
            } finally {
                _uiState.update { it.copy(isUpdatingWatchlist = false) }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            try {
                authService.logout()
            } finally {
                _uiState.update { it.copy(isLoggingOut = false) }
                onComplete()
            }
        }
    }

    /** Current anime season (`WINTER|SPRING|SUMMER|FALL`) and year in UTC. */
    private fun currentSeasonAndYear(): Pair<String, Int> {
        val now = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        val season = when (now.monthNumber) {
            12, 1, 2 -> "WINTER"
            3, 4, 5 -> "SPRING"
            6, 7, 8 -> "SUMMER"
            else -> "FALL"
        }
        // December belongs to next year's Winter season.
        val year = if (now.monthNumber == 12) now.year + 1 else now.year
        return season to year
    }

    companion object {
        /** Below this AniList popularity a highly-scored show counts as a "hidden gem". */
        private const val HIDDEN_GEM_MAX_POPULARITY = 50_000
    }
}
