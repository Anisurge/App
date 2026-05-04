package to.kuudere.anisuge.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.services.HomeService

sealed interface SplashDestination {
    data object Waiting : SplashDestination
    data object GoAuth  : SplashDestination
    data object GoHome  : SplashDestination
    data object GoHomeOffline : SplashDestination
}

class SplashViewModel(
    private val authService: AuthService,
    private val homeService: HomeService
) : ViewModel() {
    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Waiting)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        performInitialChecks()
    }

    private fun performInitialChecks() = viewModelScope.launch {
        _status.value = "Verifying user..."
        authService.checkSession()

        val authResult = authService.authState.value

        if (authResult is SessionCheckResult.Valid || authResult is SessionCheckResult.NetworkError) {
            _status.value = "Loading home data..."
            homeService.fetchHomeData()
        }

        _status.value = "Ready"

        _destination.value = when (authResult) {
            is SessionCheckResult.Valid        -> SplashDestination.GoHome
            is SessionCheckResult.NetworkError -> SplashDestination.GoHomeOffline
            else                               -> SplashDestination.GoAuth
        }
    }
}
