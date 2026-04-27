package to.kuudere.anisuge.screens.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.ContinueWatchingItem
import to.kuudere.anisuge.data.services.HomeService

import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.services.AuthService

import kotlinx.coroutines.flow.update
import to.kuudere.anisuge.data.services.InfoService

data class HomeUiState(
    val isLoading:        Boolean              = true,
    val isLoggingOut:     Boolean              = false,
    val isOffline:        Boolean              = false,
    val userProfile:      UserProfile?         = null,
    val topAiring:        List<AnimeItem>       = emptyList(),
    val latestEpisodes:   List<AnimeItem>       = emptyList(),
    val newOnSite:        List<AnimeItem>       = emptyList(),
    val topUpcoming:      List<AnimeItem>       = emptyList(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val isUpdatingWatchlist: Boolean            = false,
    val error:            String?               = null,
)

class HomeViewModel(
    private val homeService: HomeService,
    private val authService: AuthService,
    private val infoService: InfoService,
    private val realtimeService: to.kuudere.anisuge.data.services.RealtimeService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var homeDataJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        scope.launch {
            authService.authState.collect { result ->
                val userProfile = (result as? SessionCheckResult.Valid)?.user
                _uiState.update { it.copy(userProfile = userProfile) }

                if (userProfile != null) {
                    userProfile.effectiveId?.let { uid ->
                        realtimeService.connect(
                            to.kuudere.anisuge.data.models.UserInfoData(
                                userId = uid,
                                username = userProfile.username ?: "User",
                                avatar = userProfile.avatar
                            )
                        )
                    }
                    if (!itHasHomeContent()) {
                        loadHomeData(showLoading = true)
                    }
                    fetchPersonalizedData()
                } else {
                    realtimeService.disconnect()
                }
            }
        }
        refresh()
    }

    private fun itHasHomeContent(): Boolean {
        val state = _uiState.value
        return state.topAiring.isNotEmpty() ||
            state.latestEpisodes.isNotEmpty() ||
            state.newOnSite.isNotEmpty() ||
            state.topUpcoming.isNotEmpty()
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
                        isLoading        = false,
                        isOffline        = false,
                        error            = null,
                        topAiring        = homeData?.topAired    ?: emptyList(),
                        latestEpisodes   = homeData?.latestEps   ?: emptyList(),
                        newOnSite        = homeData?.lastUpdated ?: emptyList(),
                        topUpcoming      = homeData?.topUpcoming ?: emptyList(),
                    )
                }
            } catch (e: Exception) {
                handleHomeLoadError(e)
            }
        }
    }

    private fun fetchPersonalizedData() {
        scope.launch {
            try {
                val continueWatching = homeService.fetchContinueWatching()
                _uiState.update { it.copy(continueWatching = continueWatching) }
            } catch (e: Exception) {
                println("[HomeVM] Failed to fetch personalized data: ${e.message}")
            }
        }
    }

    fun refreshContinueWatching() {
        fetchPersonalizedData()
    }

    fun refresh(force: Boolean = false) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, isOffline = false, error = null) }
            try {
                val authCheck = async { authService.checkSession() }
                val homeData = async { homeService.fetchHomeData(forceRefresh = force) }

                homeData.await()?.let { data ->
                    _uiState.update {
                        it.copy(
                            isLoading        = false,
                            isOffline        = false,
                            error            = null,
                            topAiring        = data.topAired,
                            latestEpisodes   = data.latestEps,
                            newOnSite        = data.lastUpdated,
                            topUpcoming      = data.topUpcoming,
                        )
                    }
                } ?: _uiState.update { it.copy(isLoading = false) }

                // Results flow into the auth collector, but home lists no longer wait on it.
                authCheck.await()
            } catch (e: Exception) {
                handleHomeLoadError(e)
            }
        }
    }

    private fun handleHomeLoadError(e: Exception) {
        val isNetworkError = generateSequence(e as Throwable) { it.cause }.any { cause ->
            cause is java.net.UnknownHostException
                || cause is java.net.ConnectException
                || cause is java.net.SocketTimeoutException
                || cause is java.net.NoRouteToHostException
                || cause.message?.contains("Unable to resolve host", ignoreCase = true) == true
                || cause.message?.contains("Failed to connect", ignoreCase = true) == true
                || cause.message?.contains("timeout", ignoreCase = true) == true
                || cause.message?.contains("Network is unreachable", ignoreCase = true) == true
        }
        _uiState.update { it.copy(isLoading = false, isOffline = isNetworkError, error = if (isNetworkError) null else e.message) }
    }

    fun updateWatchlist(animeId: String, folder: String) {
        scope.launch {
            _uiState.update { it.copy(isUpdatingWatchlist = true) }
            try {
                val response = infoService.updateWatchlistStatus(animeId, folder)
                if (response != null && response.success) {
                    // AniList Sync
                    response.data?.token?.let { token ->
                        response.data.anilist?.let { anilistId ->
                            println("[HomeVM] Triggering AniList sync for $anilistId to $folder")
                            scope.launch {
                                val syncResult = to.kuudere.anisuge.AppComponent.aniListService.updateStatus(token, anilistId, folder)
                                println("[HomeVM] AniList sync result for $anilistId: $syncResult")
                            }
                        } ?: println("[HomeVM] No anilistId returned for sync")
                    } ?: println("[HomeVM] No token returned for sync")
                }
            } finally {
                _uiState.update { it.copy(isUpdatingWatchlist = false) }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            try {
                realtimeService.disconnect()
                authService.logout()
            } finally {
                _uiState.update { it.copy(isLoggingOut = false) }
                onComplete()
            }
        }
    }
}
