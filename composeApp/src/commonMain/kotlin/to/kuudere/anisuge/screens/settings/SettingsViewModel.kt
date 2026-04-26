package to.kuudere.anisuge.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.AniListMediaListCollection
import to.kuudere.anisuge.data.models.AniListProfile
import to.kuudere.anisuge.data.models.DownloadStorageInfo
import to.kuudere.anisuge.data.models.ImportResult
import to.kuudere.anisuge.data.models.MfaStatusData
import to.kuudere.anisuge.data.models.SessionInfoResponse
import to.kuudere.anisuge.data.models.StorageInfo
import to.kuudere.anisuge.data.models.TotpSetupData
import to.kuudere.anisuge.data.models.UserPreferences
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.SettingsService
import to.kuudere.anisuge.data.services.SettingsStore
import to.kuudere.anisuge.data.services.StorageService
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.utils.isNetworkError

data class SettingsUiState(
    // Loading states
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Preferences
    val preferences: UserPreferences = UserPreferences(),
    val hasPreferencesChanges: Boolean = false,

    // Profile
    val userProfile: to.kuudere.anisuge.data.models.UserProfile? = null,
    val isLoadingProfile: Boolean = false,

    // Sessions
    val sessions: List<SessionInfoResponse> = emptyList(),
    val currentSession: SessionInfoResponse? = null,

    // MFA/Security
    val mfaStatus: MfaStatusData? = null,
    val isLoadingMfa: Boolean = false,
    val totpSetupData: TotpSetupData? = null,
    val recoveryCodes: List<String> = emptyList(),
    val showRecoveryCodes: Boolean = false,

    // Password change
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isChangingPassword: Boolean = false,

    // AniList
    val anilistConnected: Boolean = false,
    val anilistProfile: AniListProfile? = null,
    val isLoadingAniList: Boolean = false,

    // AniList Import/Export
    val isImportingFromAniList: Boolean = false,
    val isExportingToAniList: Boolean = false,
    val importProgress: Int = 0,
    val exportProgress: Int = 0,
    val importStatus: String = "",
    val exportStatus: String = "",
    val importResult: String? = null,
    val exportResult: String? = null,
    val syncLog: List<String> = emptyList(),

    // Storage
    val storageInfo: StorageInfo? = null,
    val downloadStorageInfo: DownloadStorageInfo? = null,
    val isLoadingStorage: Boolean = false,

    // Server Priority
    val serverPriority: List<String> = emptyList(),
    val hasServerPriorityChanges: Boolean = false,
    val isLoadingServers: Boolean = false,
    val availableServers: List<to.kuudere.anisuge.data.models.ServerInfo> = emptyList(),

    // Confirmation States
    val showDisconnectConfirm: Boolean = false,
    val showDeleteAllSessionsConfirm: Boolean = false,
    val deleteSessionId: String? = null,
    val deleteAnimeId: String? = null,
    val deleteAnimeTitle: String? = null,
    val showClearCacheConfirm: Boolean = false,
    val isOffline: Boolean = false,
    val downloadPath: String = "",
    val subtitleSize: Int = 100,

    // Notifications
    val notificationsEnabled: Boolean = true,
    val hasNotificationPrefsChanges: Boolean = false,
)

sealed class SettingsTab {
    data object Profile : SettingsTab()
    data object Preferences : SettingsTab()
    data object Sync : SettingsTab()
    data object Storage : SettingsTab()
    data object Sessions : SettingsTab()
    data object Security : SettingsTab()
    data object Servers : SettingsTab()
    data object Notifications : SettingsTab()
}

class SettingsViewModel(
    private val settingsService: SettingsService,
    private val settingsStore: SettingsStore,
    private val serverRepository: ServerRepository,
    private val authService: AuthService,
    private val storageService: StorageService = StorageService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var originalPreferences: UserPreferences = UserPreferences()
    private var importJob: Job? = null
    private var exportJob: Job? = null

    init {
        // Reactive Authentication: any login/logout event automatically triggers data population
        viewModelScope.launch {
            authService.authState.collect { result ->
                if (result is SessionCheckResult.Valid) {
                    _uiState.update { it.copy(userProfile = result.user, isLoadingProfile = false) }
                    loadPreferences()
                    loadSessions()
                } else if (result is SessionCheckResult.NoSession || result is SessionCheckResult.Expired) {
                    _uiState.update { it.copy(userProfile = null, sessions = emptyList()) }
                }
            }
        }

        refresh()

        // Watch for local changes (e.g. from player) so the UI stays in sync
        viewModelScope.launch {
            settingsStore.autoPlayFlow.collect { v ->
                if (!_uiState.value.hasPreferencesChanges) {
                    _uiState.update { it.copy(preferences = it.preferences.copy(autoPlay = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.autoNextFlow.collect { v ->
                if (!_uiState.value.hasPreferencesChanges) {
                    _uiState.update { it.copy(preferences = it.preferences.copy(autoNext = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.autoSkipIntroFlow.collect { v ->
                if (!_uiState.value.hasPreferencesChanges) {
                    _uiState.update { it.copy(preferences = it.preferences.copy(skipIntro = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.autoSkipOutroFlow.collect { v ->
                if (!_uiState.value.hasPreferencesChanges) {
                    _uiState.update { it.copy(preferences = it.preferences.copy(skipOutro = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.defaultLangFlow.collect { v ->
                if (!_uiState.value.hasPreferencesChanges) {
                    _uiState.update { it.copy(preferences = it.preferences.copy(defaultLang = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.syncPercentageFlow.collect { v ->
                if (!_uiState.value.hasPreferencesChanges) {
                    _uiState.update { it.copy(preferences = it.preferences.copy(syncPercentage = v)) }
                }
            }
        }

        // Watch server priority changes
        viewModelScope.launch {
            serverRepository.userPriority.collect { priority ->
                _uiState.update {
                    it.copy(
                        serverPriority = priority,
                        hasServerPriorityChanges = false
                    )
                }
            }
        }

        // Watch available servers
        viewModelScope.launch {
            serverRepository.servers.collect { servers ->
                _uiState.update { it.copy(availableServers = servers) }
            }
        }

        viewModelScope.launch {
            settingsStore.downloadPathFlow.collect { v ->
                _uiState.update { it.copy(downloadPath = v) }
            }
        }
        viewModelScope.launch {
            settingsStore.subtitleSizeFlow.collect { v ->
                _uiState.update { it.copy(subtitleSize = v) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true, isLoading = true) }
            // Triggering the auth check updates authService.authState,
            // which in turn triggers our reactive collection above.
            val result = authService.checkSession()
            if (result is SessionCheckResult.Valid) {
                // Fetch full profile info for badges and dates
                loadUserProfile()
            }
            _uiState.update { it.copy(isLoadingProfile = false, isLoading = false) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun setShowDisconnectConfirm(show: Boolean) {
        _uiState.update { it.copy(showDisconnectConfirm = show) }
    }

    fun setShowDeleteAllSessionsConfirm(show: Boolean) {
        _uiState.update { it.copy(showDeleteAllSessionsConfirm = show) }
    }

    fun setDeleteSessionId(id: String?) {
        _uiState.update { it.copy(deleteSessionId = id) }
    }

    fun setDeleteAnime(id: String?, title: String?) {
        _uiState.update { it.copy(deleteAnimeId = id, deleteAnimeTitle = title) }
    }

    fun setShowClearCacheConfirm(show: Boolean) {
        _uiState.update { it.copy(showClearCacheConfirm = show) }
    }

    fun onTabSelected(tab: SettingsTab) {
        when (tab) {
            is SettingsTab.Profile -> loadUserProfile()
            is SettingsTab.Sessions -> loadSessions()
            is SettingsTab.Security -> loadMfaStatus()
            is SettingsTab.Sync -> loadAniListStatus()
            is SettingsTab.Servers -> loadServerPriority()
            is SettingsTab.Notifications -> loadNotificationPreferences()
            else -> {}
        }
    }

    // ==================== Profile ====================

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true, isOffline = false) }
            // Guard: ensure a valid session exists before hitting API
            val sessionResult = authService.checkSession()
            if (sessionResult !is SessionCheckResult.Valid) {
                _uiState.update { it.copy(isLoadingProfile = false) }
                return@launch
            }
            try {
                val response = settingsService.getUserProfile()
                if (response?.success == true && response.user != null) {
                    _uiState.update {
                        it.copy(
                            userProfile = response.user,
                            isLoadingProfile = false,
                            isOffline = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            errorMessage = "Failed to load user profile"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingProfile = false,
                        isOffline = e.isNetworkError()
                    )
                }
            }
        }
    }

    // ==================== Server Priority ====================

    private var originalServerPriority: List<String> = emptyList()

    fun loadServerPriority() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true) }
            val priority = serverRepository.userPriority.value
            originalServerPriority = priority
            _uiState.update {
                it.copy(
                    serverPriority = priority,
                    hasServerPriorityChanges = false,
                    isLoadingServers = false
                )
            }
        }
    }

    fun updateServerPriority(newPriority: List<String>) {
        _uiState.update { state ->
            state.copy(
                serverPriority = newPriority,
                hasServerPriorityChanges = newPriority != originalServerPriority
            )
        }
    }

    fun saveServerPriority() {
        viewModelScope.launch {
            val priority = _uiState.value.serverPriority
            serverRepository.setUserPriority(priority)
            originalServerPriority = priority
            _uiState.update {
                it.copy(
                    hasServerPriorityChanges = false,
                    successMessage = "Server priority saved"
                )
            }
        }
    }

    fun resetServerPriority() {
        viewModelScope.launch {
            serverRepository.resetUserPriority()
            originalServerPriority = emptyList()
            _uiState.update {
                it.copy(
                    serverPriority = emptyList(),
                    hasServerPriorityChanges = false,
                    successMessage = "Reset to default priority"
                )
            }
        }
    }

    // ==================== Preferences ====================

    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isOffline = false) }
            try {
                val response = settingsService.getPreferences()
                if (response?.success == true && response.preferences != null) {
                    originalPreferences = response.preferences
                    _uiState.update {
                        it.copy(
                            preferences = response.preferences,
                            isLoading = false,
                            hasPreferencesChanges = false,
                            isOffline = false
                        )
                    }
                    // Also sync local settings store
                    response.preferences.let { prefs ->
                        settingsStore.setAutoPlay(prefs.autoPlay)
                        settingsStore.setAutoNext(prefs.autoNext)
                        settingsStore.setAutoSkipIntro(prefs.skipIntro)
                        settingsStore.setAutoSkipOutro(prefs.skipOutro)
                        settingsStore.setDefaultLang(prefs.defaultLang)
                        settingsStore.setSyncPercentage(prefs.syncPercentage)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response?.message ?: "Failed to load preferences"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isOffline = e.isNetworkError()
                    ) 
                }
            }
        }
    }

    fun savePreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val response = settingsService.updatePreferences(_uiState.value.preferences)
            if (response?.success == true) {
                originalPreferences = _uiState.value.preferences
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasPreferencesChanges = false,
                        successMessage = "Preferences saved successfully"
                    )
                }
                // Sync local settings
                _uiState.value.preferences.let { prefs ->
                    settingsStore.setAutoPlay(prefs.autoPlay)
                    settingsStore.setAutoNext(prefs.autoNext)
                    settingsStore.setAutoSkipIntro(prefs.skipIntro)
                    settingsStore.setAutoSkipOutro(prefs.skipOutro)
                    settingsStore.setDefaultLang(prefs.defaultLang)
                    settingsStore.setSyncPercentage(prefs.syncPercentage)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = response?.message ?: "Failed to save preferences"
                    )
                }
            }
        }
    }

    fun updatePreference(updater: (UserPreferences) -> UserPreferences) {
        _uiState.update { state ->
            val newPrefs = updater(state.preferences)
            state.copy(
                preferences = newPrefs,
                hasPreferencesChanges = newPrefs != originalPreferences
            )
        }
    }

    fun setDefaultLang(enabled: Boolean) = updatePreference { it.copy(defaultLang = enabled) }
    fun setAutoNext(enabled: Boolean) = updatePreference { it.copy(autoNext = enabled) }
    fun setAutoPlay(enabled: Boolean) = updatePreference { it.copy(autoPlay = enabled) }
    fun setSkipIntro(enabled: Boolean) = updatePreference { it.copy(skipIntro = enabled) }
    fun setSkipOutro(enabled: Boolean) = updatePreference { it.copy(skipOutro = enabled) }
    fun setSyncPercentage(percentage: Int) = updatePreference { it.copy(syncPercentage = percentage.coerceIn(50, 100)) }

    fun setDownloadPath(path: String) {
        viewModelScope.launch {
            settingsStore.setDownloadPath(path)
        }
    }

    fun setSubtitleSize(sizePercent: Int) {
        viewModelScope.launch {
            settingsStore.setSubtitleSize(sizePercent)
        }
    }

    // ==================== Sessions ====================

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Guard: ensure a valid session exists before hitting API
            val sessionResult = authService.checkSession()
            if (sessionResult !is SessionCheckResult.Valid) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val response = settingsService.getSessions()
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        sessions = response.sessions,
                        currentSession = response.currentSession,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = response?.message ?: "Failed to load sessions"
                    )
                }
            }
        }
    }

    fun deleteSession(sessionId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val response = settingsService.deleteSession(sessionId)
            if (response?.success == true) {
                // Remove from list locally
                _uiState.update { state ->
                    state.copy(
                        sessions = state.sessions.filter { it.id != sessionId && it.id != "current" },
                        isLoading = false,
                        successMessage = "Session ended successfully"
                    )
                }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = response?.message ?: "Failed to end session"
                    )
                }
            }
        }
    }

    fun deleteAllSessions(onLoggedOut: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val response = settingsService.deleteAllSessions()
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        sessions = emptyList(),
                        isLoading = false,
                        successMessage = "All sessions ended"
                    )
                }
                onLoggedOut()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = response?.message ?: "Failed to end all sessions"
                    )
                }
            }
        }
    }

    // ==================== MFA / Security ====================

    fun loadMfaStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMfa = true) }
            val response = settingsService.getMfaStatus()
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        mfaStatus = response.data,
                        isLoadingMfa = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMfa = false,
                        errorMessage = response?.message ?: "Failed to load MFA status"
                    )
                }
            }
        }
    }

    fun toggleMfa(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMfa = true) }
            val response = settingsService.toggleMfa(enabled)
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        mfaStatus = response.data,
                        isLoadingMfa = false,
                        successMessage = if (enabled) "MFA enabled" else "MFA disabled"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMfa = false,
                        errorMessage = response?.message ?: "Failed to update MFA"
                    )
                }
            }
        }
    }

    fun setupTotp(onQrCodeReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMfa = true) }
            val response = settingsService.setupTotp()
            val totpData = response?.data?.authenticator
            if (response?.success == true && totpData != null) {
                _uiState.update {
                    it.copy(
                        totpSetupData = totpData,
                        isLoadingMfa = false
                    )
                }
                onQrCodeReady(totpData.qrCode)
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMfa = false,
                        errorMessage = response?.message ?: "Failed to setup TOTP"
                    )
                }
            }
        }
    }

    fun verifyTotp(code: String, onVerified: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMfa = true) }
            val response = settingsService.verifyTotp(code)
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        isLoadingMfa = false,
                        successMessage = "TOTP verified successfully",
                        totpSetupData = null
                    )
                }
                loadMfaStatus()
                loadRecoveryCodes()
                onVerified()
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMfa = false,
                        errorMessage = response?.message ?: "Invalid verification code"
                    )
                }
            }
        }
    }

    fun loadRecoveryCodes() {
        viewModelScope.launch {
            val response = settingsService.getRecoveryCodes()
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        recoveryCodes = response.codes,
                        showRecoveryCodes = true
                    )
                }
            }
        }
    }

    fun dismissRecoveryCodes() {
        _uiState.update { it.copy(showRecoveryCodes = false) }
    }

    fun dismissTotpSetup() {
        _uiState.update { it.copy(totpSetupData = null) }
    }

    // ==================== Password Change ====================

    fun setCurrentPassword(password: String) {
        _uiState.update { it.copy(currentPassword = password) }
    }

    fun setNewPassword(password: String) {
        _uiState.update { it.copy(newPassword = password) }
    }

    fun setConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password) }
    }

    fun changePassword(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.newPassword != state.confirmPassword) {
                _uiState.update { it.copy(errorMessage = "Passwords do not match") }
                return@launch
            }
            if (state.newPassword.length < 8) {
                _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters") }
                return@launch
            }

            _uiState.update { it.copy(isChangingPassword = true) }
            val response = settingsService.changePassword(
                state.currentPassword,
                state.newPassword,
                state.confirmPassword
            )
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        isChangingPassword = false,
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = "",
                        successMessage = "Password changed successfully"
                    )
                }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isChangingPassword = false,
                        errorMessage = response?.message ?: "Failed to change password"
                    )
                }
            }
        }
    }

    // ==================== AniList ====================

    fun loadAniListStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAniList = true) }
            val response = settingsService.getAniListStatus()
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        anilistConnected = response.connected,
                        isLoadingAniList = false
                    )
                }
                if (response.connected) {
                    loadAniListProfile()
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingAniList = false,
                        errorMessage = response?.message ?: "Failed to load AniList status"
                    )
                }
            }
        }
    }

    fun loadAniListProfile() {
        viewModelScope.launch {
            val response = settingsService.getAniListProfile()
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        anilistConnected = response.connected,
                        anilistProfile = response.profile
                    )
                }
            }
        }
    }

    fun disconnectAniList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAniList = true) }
            val response = settingsService.disconnectAniList()
            if (response?.success == true) {
                _uiState.update {
                    it.copy(
                        anilistConnected = false,
                        anilistProfile = null,
                        isLoadingAniList = false,
                        successMessage = "AniList disconnected successfully"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingAniList = false,
                        errorMessage = response?.message ?: "Failed to disconnect AniList"
                    )
                }
            }
        }
    }

    fun onConnectAniList(openUri: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAniList = true, errorMessage = null) }
            
            // 1. Ensure we have a valid session to get a JWT
            val sessionResult = authService.checkSession()
            if (sessionResult is SessionCheckResult.Valid) {
                // 2. Clear any old errors and get a new token
                val (token, error) = authService.getAppwriteJwtWithError()
                if (token != null) {
                    val url = settingsService.getAniListAuthUrl(token)
                    println("[SettingsViewModel] Redirecting to AniList with token: ${token.take(10)}...")
                    openUri(url)
                    _uiState.update { it.copy(isLoadingAniList = false, errorMessage = null) }
                } else {
                    println("[SettingsViewModel] Failed to get Appwrite JWT: $error")
                    _uiState.update { 
                        it.copy(
                            isLoadingAniList = false, 
                            errorMessage = error ?: "Secure connection failed. Please log in again or try later." 
                        ) 
                    }
                }
            } else {
                println("[SettingsViewModel] Session check failed: $sessionResult")
                _uiState.update { 
                    it.copy(
                        isLoadingAniList = false, 
                        errorMessage = "Session invalid. Please log in again to sync." 
                    ) 
                }
            }
        }
    }

    fun clearImportExportState() {
        _uiState.update {
            it.copy(
                isImportingFromAniList = false,
                isExportingToAniList = false,
                importProgress = 0,
                exportProgress = 0,
                importStatus = "",
                exportStatus = "",
                importResult = null,
                exportResult = null,
                syncLog = emptyList()
            )
        }
    }

    fun cancelSyncOperation() {
        val wasImporting = _uiState.value.isImportingFromAniList
        val wasExporting = _uiState.value.isExportingToAniList

        if (!wasImporting && !wasExporting) return

        if (wasImporting) {
            appendLog("[Import] Cancel requested by user")
        }
        if (wasExporting) {
            appendLog("[Export] Cancel requested by user")
        }

        importJob?.cancel()
        exportJob?.cancel()
        importJob = null
        exportJob = null

        _uiState.update {
            it.copy(
                isImportingFromAniList = false,
                isExportingToAniList = false,
                importStatus = if (wasImporting) "Import cancelled" else it.importStatus,
                exportStatus = if (wasExporting) "Export cancelled" else it.exportStatus,
                importResult = if (wasImporting) "Cancelled" else it.importResult,
                exportResult = if (wasExporting) "Cancelled" else it.exportResult
            )
        }
    }

    // ==================== AniList Import/Export ====================

    private fun appendLog(message: String) {
        _uiState.update { it.copy(syncLog = it.syncLog + message) }
    }

    fun importFromAniList() {
        if (importJob?.isActive == true || exportJob?.isActive == true) return

        importJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isImportingFromAniList = true,
                        importProgress = 0,
                        importStatus = "Getting AniList access token...",
                        importResult = null,
                        syncLog = emptyList()
                    )
                }
                appendLog("[Import] Getting AniList access token...")

                // Get AniList token from backend
                val tokenResponse = settingsService.getAniListToken()
                appendLog("[Import] Token response: success=${tokenResponse?.success}, hasToken=${tokenResponse?.access_token != null}")
                if (tokenResponse?.success != true || tokenResponse.access_token == null) {
                    appendLog("[Import] ERROR: ${tokenResponse?.message ?: "No token returned"}")
                    _uiState.update {
                        it.copy(
                            isImportingFromAniList = false,
                            errorMessage = tokenResponse?.message ?: "Failed to get AniList access token. Please reconnect."
                        )
                    }
                    return@launch
                }

                val accessToken = tokenResponse.access_token
                val userId = _uiState.value.anilistProfile?.id

                if (userId == null) {
                    appendLog("[Import] ERROR: No AniList user ID available")
                    _uiState.update {
                        it.copy(
                            isImportingFromAniList = false,
                            errorMessage = "Could not fetch AniList user ID. Cannot proceed with import."
                        )
                    }
                    return@launch
                }

                appendLog("[Import] Fetching AniList watchlist for userId=$userId...")
                _uiState.update { it.copy(importProgress = 25, importStatus = "Fetching your AniList watchlist...") }

                // Fetch AniList watchlist via GraphQL
                val aniListData = settingsService.fetchAniListWatchlist(accessToken, userId)
                if (aniListData == null) {
                    appendLog("[Import] ERROR: AniList returned null data")
                    _uiState.update {
                        it.copy(
                            isImportingFromAniList = false,
                            errorMessage = "Failed to fetch AniList watchlist"
                        )
                    }
                    return@launch
                }

                val totalEntries = aniListData.lists?.sumOf { it.entries?.size ?: 0 } ?: 0
                val listNames = aniListData.lists?.map { "${it.name}(${it.entries?.size ?: 0})" }?.joinToString(", ") ?: "none"
                appendLog("[Import] AniList returned $totalEntries entries in lists: $listNames")

                _uiState.update { it.copy(importProgress = 50, importStatus = "Converting AniList data...") }

                // Convert to Kuudere format
                val kuudereData = convertAniListToKuudereFormat(aniListData)
                val convertedTotal = kuudereData.values.sumOf { it.size }
                kuudereData.forEach { (folder, items) ->
                    appendLog("[Import] Converted: $folder → ${items.size} entries")
                }
                appendLog("[Import] Total converted: $convertedTotal entries")

                _uiState.update { it.copy(importProgress = 75, importStatus = "Importing to Kuudere...") }
                appendLog("[Import] Sending to Kuudere /api/import/json...")

                // Import to Kuudere using the JSON import endpoint
                val result = settingsService.importWatchlistToKuudere(kuudereData) { event ->
                    // Real-time SSE progress updates
                    when (event.type) {
                        "progress" -> {
                            val statusText = event.status ?: "Processing..."
                            val currentItem = event.currentItem
                            val logLine = if (currentItem != null) {
                                "[Import] ${event.progress}% — $currentItem"
                            } else {
                                "[Import] ${event.progress}% — $statusText"
                            }
                            appendLog(logLine)
                            _uiState.update {
                                it.copy(
                                    importProgress = event.progress,
                                    importStatus = statusText
                                )
                            }
                        }
                        "item_error" -> {
                            appendLog("[Import] ERROR: ${event.currentItem} — ${event.error}")
                        }
                    }
                }
                appendLog("[Import] API response: success=${result?.success}, message=${result?.message}")
                appendLog("[Import] Stats: imported=${result?.stats?.imported}, skipped=${result?.stats?.skipped}, errors=${result?.stats?.errors}")

                if (result?.success == true) {
                    _uiState.update {
                        it.copy(
                            importProgress = 100,
                            importStatus = "Import completed!",
                            importResult = "${result.stats?.imported ?: 0} imported, ${result.stats?.skipped ?: 0} skipped, ${result.stats?.errors ?: 0} errors"
                        )
                    }
                    appendLog("[Import] ✓ Import completed successfully!")
                } else {
                    appendLog("[Import] ✗ Import failed: ${result?.message ?: "unknown error"}")
                    _uiState.update {
                        it.copy(
                            isImportingFromAniList = false,
                            errorMessage = result?.message ?: "Import failed"
                        )
                    }
                }

                // Auto-clear after 5 seconds
                kotlinx.coroutines.delay(5000)
                clearImportExportState()

            } catch (_: CancellationException) {
                if (_uiState.value.isImportingFromAniList) {
                    appendLog("[Import] Cancelled")
                    _uiState.update {
                        it.copy(
                            isImportingFromAniList = false,
                            importStatus = "Import cancelled",
                            importResult = "Cancelled"
                        )
                    }
                }
            } catch (e: Exception) {
                appendLog("[Import] ✗ Exception: ${e.message}")
                _uiState.update {
                    it.copy(
                        isImportingFromAniList = false,
                        errorMessage = "Import failed: ${e.message}"
                    )
                }
            } finally {
                importJob = null
            }
        }
    }

    fun exportToAniList() {
        if (exportJob?.isActive == true || importJob?.isActive == true) return

        exportJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isExportingToAniList = true,
                        exportProgress = 0,
                        exportStatus = "Getting AniList access token...",
                        exportResult = null,
                        syncLog = emptyList()
                    )
                }
                appendLog("[Export] Getting AniList access token...")

                // Get AniList token from backend
                val tokenResponse = settingsService.getAniListToken()
                appendLog("[Export] Token response: success=${tokenResponse?.success}")
                if (tokenResponse?.success != true || tokenResponse.access_token == null) {
                    appendLog("[Export] ERROR: ${tokenResponse?.message ?: "No token returned"}")
                    _uiState.update {
                        it.copy(
                            isExportingToAniList = false,
                            errorMessage = tokenResponse?.message ?: "Failed to get AniList access token. Please reconnect."
                        )
                    }
                    return@launch
                }

                val accessToken = tokenResponse.access_token

                _uiState.update { it.copy(exportProgress = 25, exportStatus = "Fetching your Kuudere watchlist...") }
                appendLog("[Export] Fetching Kuudere watchlist...")

                // Get Kuudere watchlist
                val watchlist = settingsService.getKuudereWatchlistForExport()
                if (watchlist == null) {
                    appendLog("[Export] ERROR: Failed to fetch Kuudere watchlist")
                    _uiState.update {
                        it.copy(
                            isExportingToAniList = false,
                            errorMessage = "Failed to fetch Kuudere watchlist"
                        )
                    }
                    return@launch
                }

                appendLog("[Export] Got ${watchlist.size} entries from Kuudere")
                _uiState.update { it.copy(exportProgress = 50, exportStatus = "Exporting to AniList...") }

                // Export to AniList via GraphQL
                var exported = 0
                var skipped = 0
                var errors = 0

                for ((index, entry) in watchlist.withIndex()) {
                    val progress = Math.round((index.toFloat() / watchlist.size) * 40) + 50
                    val title = entry.english ?: entry.romaji ?: "Unknown"
                    _uiState.update { it.copy(exportProgress = progress, exportStatus = "Exporting: $title (${index + 1}/${watchlist.size})") }

                    // Convert Kuudere status to AniList status
                    val anilistStatus = when (entry.folder) {
                        "Watching" -> "CURRENT"
                        "Completed" -> "COMPLETED"
                        "On Hold" -> "PAUSED"
                        "Dropped" -> "DROPPED"
                        "Plan To Watch" -> "PLANNING"
                        else -> null
                    }

                    if (anilistStatus == null || entry.anilistId == null) {
                        appendLog("[Export] SKIP: $title (folder=${entry.folder}, anilistId=${entry.anilistId})")
                        skipped++
                        continue
                    }

                    val error = settingsService.updateAniListEntry(
                        accessToken,
                        entry.anilistId,
                        anilistStatus,
                        entry.score,
                        entry.episodesWatched ?: 0
                    )

                    if (error == null) {
                        appendLog("[Export] ✓ $title → status=$anilistStatus, progress=${entry.episodesWatched ?: 0}, score=${entry.score}, mediaId=${entry.anilistId}")
                        exported++
                    } else {
                        appendLog("[Export] ✗ FAILED: $title — status=$anilistStatus, progress=${entry.episodesWatched ?: 0}, score=${entry.score}, mediaId=${entry.anilistId} — $error")
                        errors++
                    }

                    // Rate limiting - randomized 2.5-3.5s delay to avoid AniList blocking
                    kotlinx.coroutines.delay(2500L + (Math.random() * 1000).toLong())
                }

                appendLog("[Export] Done: $exported exported, $skipped skipped, $errors errors")
                _uiState.update {
                    it.copy(
                        exportProgress = 100,
                        exportStatus = "Export completed!",
                        exportResult = "$exported exported, $skipped skipped, $errors errors"
                    )
                }

                // Auto-clear after 5 seconds
                kotlinx.coroutines.delay(5000)
                clearImportExportState()

            } catch (_: CancellationException) {
                if (_uiState.value.isExportingToAniList) {
                    appendLog("[Export] Cancelled")
                    _uiState.update {
                        it.copy(
                            isExportingToAniList = false,
                            exportStatus = "Export cancelled",
                            exportResult = "Cancelled"
                        )
                    }
                }
            } catch (e: Exception) {
                appendLog("[Export] ✗ Exception: ${e.message}")
                _uiState.update {
                    it.copy(
                        isExportingToAniList = false,
                        errorMessage = "Export failed: ${e.message}"
                    )
                }
            } finally {
                exportJob = null
            }
        }
    }

    private fun convertAniListToKuudereFormat(aniListData: AniListMediaListCollection?): Map<String, List<Map<String, Any?>>> {
        val folderMap = mutableMapOf<String, MutableList<Map<String, Any?>>>(
            "Watching" to mutableListOf(),
            "Completed" to mutableListOf(),
            "On Hold" to mutableListOf(),
            "Dropped" to mutableListOf(),
            "Plan To Watch" to mutableListOf()
        )

        aniListData?.lists?.forEach { list ->
            list.entries?.forEach { entry ->
                if (entry.media?.type != "ANIME") return@forEach

                // Derive folder from list.name first (how AniList groups them),
                // fall back to entry.status enum
                val folder = when (list.name) {
                    "Watching" -> "Watching"
                    "Completed" -> "Completed"
                    "Paused" -> "On Hold"
                    "Dropped" -> "Dropped"
                    "Planning" -> "Plan To Watch"
                    else -> when (entry.status) {
                        "CURRENT" -> "Watching"
                        "COMPLETED" -> "Completed"
                        "PAUSED" -> "On Hold"
                        "DROPPED" -> "Dropped"
                        "PLANNING" -> "Plan To Watch"
                        else -> "Plan To Watch"
                    }
                }

                folderMap[folder]?.add(mapOf(
                    "name" to (entry.media.title?.english ?: entry.media.title?.romaji ?: entry.media.title?.native ?: ""),
                    "mal_id" to entry.media.idMal,
                    "anilist_id" to entry.media.id,
                    "link" to "https://anilist.co/anime/${entry.media.id}",
                    "watchListType" to getFolderNumber(folder)
                ))
            }
        }

        return folderMap
    }

    private fun getFolderNumber(folder: String): Int {
        return when (folder) {
            "Watching" -> 1
            "Completed" -> 2
            "On Hold" -> 3
            "Dropped" -> 4
            "Plan To Watch" -> 5
            else -> 5
        }
    }

    // ==================== Storage Management ====================

    fun loadStorageInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStorage = true) }
            val info = storageService.getStorageInfo()
            val downloadInfo = storageService.getDownloadStorageInfo()
            _uiState.update {
                it.copy(
                    storageInfo = info,
                    downloadStorageInfo = downloadInfo,
                    isLoadingStorage = false
                )
            }
        }
    }

    fun clearFontCache(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = storageService.clearFontCache()
            if (success) {
                loadStorageInfo()
                _uiState.update { it.copy(successMessage = "Font cache cleared") }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to clear font cache") }
            }
            onComplete(success)
        }
    }

    fun deleteAnimeDownloads(animeId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = storageService.deleteAnimeDownloads(animeId)
            if (success) {
                loadStorageInfo()
                _uiState.update { it.copy(successMessage = "Anime downloads deleted") }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to delete anime downloads") }
            }
            onComplete(success)
        }
    }

    fun deleteEpisodeDownload(animeId: String, episodeNumber: Int, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = storageService.deleteEpisodeDownload(animeId, episodeNumber)
            if (success) {
                loadStorageInfo()
                _uiState.update { it.copy(successMessage = "Episode deleted") }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to delete episode") }
            }
            onComplete(success)
        }
    }

    fun formatBytes(bytes: Long): String = storageService.formatBytes(bytes)
    fun formatBytesCompact(bytes: Long): String = storageService.formatBytesCompact(bytes)

    // ==================== Notifications ====================

    fun loadNotificationPreferences() {
        viewModelScope.launch {
            val enabled = settingsStore.notificationsEnabledFlow.first()
            _uiState.update {
                it.copy(
                    notificationsEnabled = enabled,
                    hasNotificationPrefsChanges = false
                )
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                notificationsEnabled = enabled,
                hasNotificationPrefsChanges = true
            )
        }
    }

    fun saveNotificationPreferences() {
        viewModelScope.launch {
            val enabled = _uiState.value.notificationsEnabled
            settingsStore.setNotificationsEnabled(enabled)

            // Sync FCM topic subscriptions on Android
            if (enabled) {
                to.kuudere.anisuge.platform.startNotificationListenerService()
            } else {
                to.kuudere.anisuge.platform.stopNotificationListenerService()
            }

            _uiState.update {
                it.copy(
                    hasNotificationPrefsChanges = false,
                    successMessage = "Notification preferences saved"
                )
            }
        }
    }
}
