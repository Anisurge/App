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
        _status.value = "Initializing..."

        // Launch ALL needed compute in parallel immediately.
        // The splash video plays while this work happens in the background.
        // Destination resolves as soon as auth+update finish; other work (home prefetch) continues in parallel.
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

        // Home prefetch runs in parallel (helps make home screen snappy when we navigate after splash).
        // Do NOT join on it — it must not delay splash video or destination resolution.
        viewModelScope.launch {
            runCatching {
                withTimeout(15_000) { homeService.fetchHomeData() }
            }
        }

        // Wait only for the checks that decide where to go after splash.
        authJob.join()
        updateJob.join()

        val authResult = authService.authState.value

        _status.value = "Ready"

        _destination.value = when (authResult) {
            is SessionCheckResult.Valid        -> SplashDestination.GoHome
            is SessionCheckResult.NetworkError -> SplashDestination.GoHomeOffline
            else                               -> SplashDestination.GoAuth
        }
    }
}
