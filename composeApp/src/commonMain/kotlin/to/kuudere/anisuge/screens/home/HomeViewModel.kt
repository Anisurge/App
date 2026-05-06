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
import to.kuudere.anisuge.data.services.WatchlistService

data class HomeUiState(
    val isLoading:        Boolean              = true,
    val isLoggingOut:     Boolean              = false,
    val isOffline:        Boolean              = false,
    val userProfile:      UserProfile?         = null,
    val latestAired:      List<AnimeItem>       = emptyList(),
    val newOnSite:        List<AnimeItem>       = emptyList(),
    val upcoming:         List<AnimeItem>       = emptyList(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val isUpdatingWatchlist: Boolean            = false,
    val error:            String?               = null,
)

class HomeViewModel(
    private val homeService: HomeService,
    private val authService: AuthService,
    private val watchlistService: WatchlistService,
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
                    if (!itHasHomeContent()) {
                        loadHomeData(showLoading = true)
                    }
                    fetchPersonalizedData()
                }
            }
        }
        refresh()
    }

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
                        isLoading        = false,
                        isOffline        = false,
                        error            = null,
                        latestAired      = homeData?.latestAired  ?: emptyList(),
                        newOnSite        = homeData?.newOnSite   ?: emptyList(),
                        upcoming         = homeData?.upcoming     ?: emptyList(),
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
                            latestAired      = data.latestAired,
                            newOnSite        = data.newOnSite,
                            upcoming         = data.upcoming,
                        )
                    }
                } ?: _uiState.update { it.copy(isLoading = false) }

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
                watchlistService.updateStatus(animeId, folder)
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
}
