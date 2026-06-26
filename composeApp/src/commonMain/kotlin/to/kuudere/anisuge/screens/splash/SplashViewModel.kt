package to.kuudere.anisuge.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import to.kuudere.anisuge.data.services.AnalyticsPingService
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.services.UpdateService
import to.kuudere.anisuge.data.services.HomeService

sealed interface SplashDestination {
    data object Waiting : SplashDestination
    data object GoAuth  : SplashDestination
    data object GoHome  : SplashDestination
    /** We have a stored session but no internet – go home in offline mode */
    data object GoHomeOffline : SplashDestination
}

class SplashViewModel(
    private val authService: AuthService,
    private val updateService: UpdateService,
    private val homeService: HomeService,
    private val analyticsPingService: AnalyticsPingService,
) : ViewModel() {
    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Waiting)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { analyticsPingService.sendPingIfDue() }
        }
        performInitialChecks()
    }

    private fun performInitialChecks() = viewModelScope.launch {
        // Step 1 & 2: Parallelize User Verification and Update Check
        _status.value = "Initializing..."
        
        val authJob = viewModelScope.launch {
            _status.value = "Verifying user..."
            runCatching {
                withTimeout(20_000) { authService.checkSession() }
            }
        }

        val updateJob = viewModelScope.launch {
            _status.value = "Checking for updates..."
            runCatching {
                withTimeout(12_000) { updateService.checkUpdate() }
            }
        }

        authJob.join()
        val authResult = authService.authState.value

        // Do not block splash on home data fetch — HomeScreen will load it lazily for faster perceived startup
        updateJob.join()

        _status.value = "Ready"
        
        // Final destination resolution
        _destination.value = when (authResult) {
            is SessionCheckResult.Valid        -> SplashDestination.GoHome
            is SessionCheckResult.NetworkError -> SplashDestination.GoHomeOffline
            else                               -> SplashDestination.GoAuth
        }
    }
}
