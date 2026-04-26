package to.kuudere.anisuge.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.platform.TvQrPairingReceiver
import to.kuudere.anisuge.platform.generatePairingNonce

enum class AuthMode { LOGIN, REGISTER, FORGOT_PASSWORD, VERIFY_CODE, RESET_PASSWORD }

data class AuthUiState(
    val mode: AuthMode         = AuthMode.LOGIN,
    val email: String          = "",
    val password: String       = "",
    val confirmPassword: String = "",
    val displayName: String    = "",
    val resetCode: String      = "",
    val isLoading: Boolean     = false,
    val errorMessage: String?  = null,
    val infoMessage: String?   = null,
    val isSuccess: Boolean     = false,
    val tvPairingPayload: String? = null,
    val tvPairingStatus: String = "Preparing QR login...",
    val tvPairingExpiresAtMillis: Long = 0L,
)

class AuthViewModel(private val authService: AuthService) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val tvPairingReceiver = TvQrPairingReceiver()

    fun setMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            errorMessage = null,
            infoMessage = null,
        )
    }

    fun toggleMode() {
        val currentMode = _uiState.value.mode
        val newMode = if (currentMode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
        setMode(newMode)
    }

    fun onEmailChange(v: String)           { _uiState.value = _uiState.value.copy(email = v) }
    fun onPasswordChange(v: String)        { _uiState.value = _uiState.value.copy(password = v) }
    fun onConfirmPasswordChange(v: String) { _uiState.value = _uiState.value.copy(confirmPassword = v) }
    fun onDisplayNameChange(v: String)     { _uiState.value = _uiState.value.copy(displayName = v) }
    fun onResetCodeChange(v: String)       { _uiState.value = _uiState.value.copy(resetCode = v) }
    fun clearError()                       { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearInfo()                        { _uiState.value = _uiState.value.copy(infoMessage = null) }

    fun startTvPairing() {
        tvPairingReceiver.stop()
        val nonce = generatePairingNonce()
        val expiresAtMillis = Clock.System.now().toEpochMilliseconds() + 120_000L
        _uiState.value = _uiState.value.copy(
            tvPairingPayload = null,
            tvPairingStatus = "Starting secure local pairing...",
            tvPairingExpiresAtMillis = expiresAtMillis,
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                val endpoint = tvPairingReceiver.start(nonce, expiresAtMillis) { session ->
                    authService.savePairedSession(session)
                    _uiState.value = _uiState.value.copy(
                        isSuccess = true,
                        tvPairingStatus = "Paired successfully",
                        errorMessage = null,
                    )
                }
                val payload = "anisurge://tv-login?host=${endpoint.host}&port=${endpoint.port}&nonce=$nonce"
                _uiState.value = _uiState.value.copy(
                    tvPairingPayload = payload,
                    tvPairingStatus = "Scan with Anisurge on your phone",
                    tvPairingExpiresAtMillis = expiresAtMillis,
                )
                launch {
                    delay((expiresAtMillis - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L))
                    if (!_uiState.value.isSuccess && _uiState.value.tvPairingExpiresAtMillis == expiresAtMillis) {
                        tvPairingReceiver.stop()
                        _uiState.value = _uiState.value.copy(
                            tvPairingStatus = "QR expired. Generate a new code.",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tvPairingStatus = "Could not start QR login",
                    errorMessage = e.message ?: "TV pairing failed",
                )
            }
        }
    }

    fun stopTvPairing() {
        tvPairingReceiver.stop()
    }

    override fun onCleared() {
        tvPairingReceiver.stop()
        super.onCleared()
    }

    fun submit() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null, infoMessage = null)
            try {
                when (state.mode) {
                    AuthMode.LOGIN -> {
                        if (state.email.isBlank() || state.password.isBlank()) {
                            throw Exception("Please fill in all fields")
                        }
                        authService.login(state.email, state.password)
                        _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                    }
                    AuthMode.REGISTER -> {
                        if (state.email.isBlank() || state.password.isBlank() || state.displayName.isBlank()) {
                            throw Exception("Please fill in all fields")
                        }
                        authService.register(state.email, state.password, state.displayName)
                        _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                    }
                    AuthMode.FORGOT_PASSWORD -> {
                        if (state.email.isBlank()) throw Exception("Please enter your email")
                        val msg = authService.forgotPassword(state.email)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            infoMessage = msg,
                            mode = AuthMode.VERIFY_CODE
                        )
                    }
                    AuthMode.VERIFY_CODE -> {
                        if (state.resetCode.isBlank()) throw Exception("Please enter the reset code")
                        val valid = authService.verifyResetCode(state.email, state.resetCode)
                        if (valid) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                mode = AuthMode.RESET_PASSWORD
                            )
                        } else {
                            throw Exception("Invalid reset code")
                        }
                    }
                    AuthMode.RESET_PASSWORD -> {
                        if (state.password.isBlank()) throw Exception("Please enter a new password")
                        if (state.password != state.confirmPassword) throw Exception("Passwords do not match")
                        val msg = authService.resetPassword(state.email, state.resetCode, state.password)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            infoMessage = msg,
                            mode = AuthMode.LOGIN,
                            password = "",
                            confirmPassword = ""
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    errorMessage = e.message ?: "Something went wrong",
                )
            }
        }
    }
}
