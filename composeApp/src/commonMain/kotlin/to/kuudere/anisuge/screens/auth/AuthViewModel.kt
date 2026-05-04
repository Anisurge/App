package to.kuudere.anisuge.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.services.AuthService

enum class AuthMode { LOGIN, REGISTER, FORGOT_PASSWORD, RESET_PASSWORD }

data class AuthUiState(
    val mode: AuthMode         = AuthMode.LOGIN,
    val email: String          = "",
    val password: String       = "",
    val confirmPassword: String = "",
    val username: String       = "",
    val otp: String            = "",
    val isLoading: Boolean     = false,
    val errorMessage: String?  = null,
    val infoMessage: String?   = null,
    val isSuccess: Boolean     = false,
)

class AuthViewModel(private val authService: AuthService) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

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
    fun onUsernameChange(v: String)        { _uiState.value = _uiState.value.copy(username = v) }
    fun onOtpChange(v: String)             { _uiState.value = _uiState.value.copy(otp = v) }
    fun clearError()                       { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearInfo()                        { _uiState.value = _uiState.value.copy(infoMessage = null) }

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
                        if (state.email.isBlank() || state.password.isBlank() || state.username.isBlank()) {
                            throw Exception("Please fill in all fields")
                        }
                        authService.signup(state.email, state.password, state.username)
                        _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                    }
                    AuthMode.FORGOT_PASSWORD -> {
                        if (state.email.isBlank()) throw Exception("Please enter your email")
                        val msg = authService.forgotPassword(state.email)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            infoMessage = msg,
                            mode = AuthMode.RESET_PASSWORD
                        )
                    }
                    AuthMode.RESET_PASSWORD -> {
                        if (state.otp.isBlank()) throw Exception("Please enter the OTP code")
                        if (state.password.isBlank()) throw Exception("Please enter a new password")
                        if (state.password != state.confirmPassword) throw Exception("Passwords do not match")
                        val msg = authService.resetPassword(state.email, state.otp, state.password)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            infoMessage = msg,
                            mode = AuthMode.LOGIN,
                            password = "",
                            confirmPassword = "",
                            otp = ""
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
