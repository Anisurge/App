package to.kuudere.anisuge.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.DownloadStorageInfo
import to.kuudere.anisuge.data.models.StorageInfo
import to.kuudere.anisuge.data.models.UserSettings
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.SettingsService
import to.kuudere.anisuge.data.services.SettingsStore
import to.kuudere.anisuge.data.services.WatchlistService
import to.kuudere.anisuge.data.services.StorageService
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.services.TrackingService
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.i18n.AppLocale
import to.kuudere.anisuge.utils.isNetworkError

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    val settings: UserSettings = UserSettings(),
    val hasSettingsChanges: Boolean = false,

    val userProfile: to.kuudere.anisuge.data.models.UserProfile? = null,
    val isLoadingProfile: Boolean = false,

    val currentPassword: String = "",
    val newPassword: String = "",
    val isChangingPassword: Boolean = false,

    val storageInfo: StorageInfo? = null,
    val downloadStorageInfo: DownloadStorageInfo? = null,
    val isLoadingStorage: Boolean = false,

    val serverPriority: List<String> = emptyList(),
    val hasServerPriorityChanges: Boolean = false,
    val isLoadingServers: Boolean = false,
    val availableServers: List<to.kuudere.anisuge.data.models.ServerInfo> = emptyList(),

    val deleteAnimeId: String? = null,
    val deleteAnimeTitle: String? = null,
    val showClearCacheConfirm: Boolean = false,
    val isOffline: Boolean = false,
    val downloadPath: String = "",
    val subtitleSize: Int = 100,
    val floatingBottomNav: Boolean = true,
    val liquidGlassBottomNav: Boolean = false,
    val appLocale: AppLocale = AppLocale.default,
    val preferRomajiAnimeTitles: Boolean = false,

    val notificationsEnabled: Boolean = true,
    val hasNotificationPrefsChanges: Boolean = false,

    // Tracking
    val malConnected: Boolean = false,
    val malUsername: String? = null,
    val anilistConnected: Boolean = false,
    val anilistUsername: String? = null,
    val isConnectingMal: Boolean = false,
    val isConnectingAnilist: Boolean = false,
    val isSyncingMal: Boolean = false,
    val isSyncingAnilist: Boolean = false,
)

sealed class SettingsTab {
    data object Profile : SettingsTab()
    data object Preferences : SettingsTab()
    data object Storage : SettingsTab()
    data object Appearance : SettingsTab()
    data object Sync : SettingsTab()
    data object Servers : SettingsTab()
    data object Notifications : SettingsTab()
}

class SettingsViewModel(
    private val settingsService: SettingsService,
    private val settingsStore: SettingsStore,
    private val serverRepository: ServerRepository,
    private val authService: AuthService,
    private val trackingService: TrackingService,
    private val watchlistService: WatchlistService,
    private val storageService: StorageService = StorageService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var originalSettings: UserSettings = UserSettings()

    init {
        viewModelScope.launch {
            authService.authState.collect { result ->
                if (result is SessionCheckResult.Valid) {
                    _uiState.update { it.copy(userProfile = result.user, isLoadingProfile = false) }
                    loadSettings()
                } else if (result is SessionCheckResult.NoSession || result is SessionCheckResult.Expired) {
                    _uiState.update { it.copy(userProfile = null) }
                }
            }
        }

        refresh()

        viewModelScope.launch {
            settingsStore.autoPlayFlow.collect { v ->
                if (!_uiState.value.hasSettingsChanges) {
                    _uiState.update { it.copy(settings = it.settings.copy(autoPlay = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.autoNextFlow.collect { v ->
                if (!_uiState.value.hasSettingsChanges) {
                    _uiState.update { it.copy(settings = it.settings.copy(autoNext = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.autoSkipIntroFlow.collect { v ->
                if (!_uiState.value.hasSettingsChanges) {
                    _uiState.update { it.copy(settings = it.settings.copy(skipIntro = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.autoSkipOutroFlow.collect { v ->
                if (!_uiState.value.hasSettingsChanges) {
                    _uiState.update { it.copy(settings = it.settings.copy(skipOutro = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.defaultLangFlow.collect { v ->
                if (!_uiState.value.hasSettingsChanges) {
                    _uiState.update { it.copy(settings = it.settings.copy(defaultLang = v)) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.syncPercentageFlow.collect { v ->
                if (!_uiState.value.hasSettingsChanges) {
                    _uiState.update { it.copy(settings = it.settings.copy(syncPercentage = v.toDouble())) }
                }
            }
        }

        viewModelScope.launch {
            serverRepository.userPriority.collect { priority ->
                _uiState.update { it.copy(serverPriority = priority, hasServerPriorityChanges = false) }
            }
        }
        viewModelScope.launch {
            serverRepository.servers.collect { servers ->
                _uiState.update { it.copy(availableServers = servers) }
            }
        }

        viewModelScope.launch { settingsStore.downloadPathFlow.collect { v -> _uiState.update { it.copy(downloadPath = v) } } }
        viewModelScope.launch { settingsStore.subtitleSizeFlow.collect { v -> _uiState.update { it.copy(subtitleSize = v) } } }
        viewModelScope.launch { settingsStore.floatingBottomNavFlow.collect { v -> _uiState.update { it.copy(floatingBottomNav = v) } } }
        viewModelScope.launch { settingsStore.liquidGlassBottomNavFlow.collect { v -> _uiState.update { it.copy(liquidGlassBottomNav = v) } } }
        viewModelScope.launch { settingsStore.appLocaleFlow.collect { code -> _uiState.update { it.copy(appLocale = AppLocale.fromCode(code)) } } }
        viewModelScope.launch {
            settingsStore.preferRomajiAnimeTitlesFlow.collect { v ->
                _uiState.update { it.copy(preferRomajiAnimeTitles = v) }
            }
        }

        // Load tracking state
        loadTrackingState()
    }

    private fun loadTrackingState() {
        viewModelScope.launch {
            val malToken = settingsStore.getMalAccessToken()
            val anilistToken = settingsStore.getAnilistAccessToken()
            val malUsername = settingsStore.getMalUsername()
            val anilistUsername = settingsStore.getAnilistUsername()
            _uiState.update {
                it.copy(
                    malConnected = malToken != null,
                    malUsername = malUsername,
                    anilistConnected = anilistToken != null,
                    anilistUsername = anilistUsername,
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true, isLoading = true) }
            val result = authService.checkSession()
            if (result is SessionCheckResult.Valid) {
                loadUserProfile()
            }
            _uiState.update { it.copy(isLoadingProfile = false, isLoading = false) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
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
            is SettingsTab.Servers -> loadServerPriority()
            is SettingsTab.Notifications -> loadNotificationPreferences()
            else -> {}
        }
    }

    fun setFloatingBottomNav(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setFloatingBottomNav(enabled) }
    }

    fun setLiquidGlassBottomNav(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLiquidGlassBottomNav(enabled) }
    }

    fun setAppLocale(locale: AppLocale) {
        viewModelScope.launch { settingsStore.setAppLocale(locale.code) }
    }

    fun setPreferRomajiAnimeTitles(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setPreferRomajiAnimeTitles(enabled) }
    }

    // Profile
    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true, isOffline = false) }
            try {
                val response = settingsService.getUserProfile()
                if (response?.user != null) {
                    _uiState.update { it.copy(userProfile = response.user, isLoadingProfile = false, isOffline = false) }
                } else {
                    _uiState.update { it.copy(isLoadingProfile = false, errorMessage = "Failed to load user profile") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingProfile = false, isOffline = e.isNetworkError()) }
            }
        }
    }

    // Server Priority
    private var originalServerPriority: List<String> = emptyList()

    fun loadServerPriority() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true) }
            val priority = serverRepository.userPriority.value
            originalServerPriority = priority
            _uiState.update { it.copy(serverPriority = priority, hasServerPriorityChanges = false, isLoadingServers = false) }
        }
    }

    fun updateServerPriority(newPriority: List<String>) {
        _uiState.update { it.copy(serverPriority = newPriority, hasServerPriorityChanges = newPriority != originalServerPriority) }
    }

    fun saveServerPriority() {
        viewModelScope.launch {
            val priority = _uiState.value.serverPriority
            serverRepository.setUserPriority(priority)
            originalServerPriority = priority
            _uiState.update { it.copy(hasServerPriorityChanges = false, successMessage = "Server priority saved") }
        }
    }

    fun resetServerPriority() {
        viewModelScope.launch {
            serverRepository.resetUserPriority()
            originalServerPriority = emptyList()
            _uiState.update { it.copy(serverPriority = emptyList(), hasServerPriorityChanges = false, successMessage = "Reset to default priority") }
        }
    }

    // Settings
    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isOffline = false) }
            try {
                val loaded = settingsService.getSettings()
                if (loaded != null) {
                    originalSettings = loaded
                    _uiState.update { it.copy(settings = loaded, isLoading = false, hasSettingsChanges = false, isOffline = false) }
                    loaded.let { s ->
                        settingsStore.setAutoPlay(s.autoPlay)
                        settingsStore.setAutoNext(s.autoNext)
                        settingsStore.setAutoSkipIntro(s.skipIntro)
                        settingsStore.setAutoSkipOutro(s.skipOutro)
                        settingsStore.setDefaultLang(s.defaultLang)
                        settingsStore.setSyncPercentage(s.syncPercentage.toInt())
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load settings") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isOffline = e.isNetworkError()) }
            }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val saved = settingsService.updateSettings(_uiState.value.settings)
            if (saved != null) {
                originalSettings = _uiState.value.settings
                _uiState.update { it.copy(isSaving = false, hasSettingsChanges = false, successMessage = "Settings saved successfully") }
                _uiState.value.settings.let { s ->
                    settingsStore.setAutoPlay(s.autoPlay)
                    settingsStore.setAutoNext(s.autoNext)
                    settingsStore.setAutoSkipIntro(s.skipIntro)
                    settingsStore.setAutoSkipOutro(s.skipOutro)
                    settingsStore.setDefaultLang(s.defaultLang)
                    settingsStore.setSyncPercentage(s.syncPercentage.toInt())
                }
            } else {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Failed to save settings") }
            }
        }
    }

    fun updateSetting(updater: (UserSettings) -> UserSettings) {
        _uiState.update { state ->
            val newSettings = updater(state.settings)
            state.copy(settings = newSettings, hasSettingsChanges = newSettings != originalSettings)
        }
    }

    fun setDefaultLang(enabled: Boolean) = updateSetting { it.copy(defaultLang = enabled) }
    fun setAutoNext(enabled: Boolean) = updateSetting { it.copy(autoNext = enabled) }
    fun setAutoPlay(enabled: Boolean) = updateSetting { it.copy(autoPlay = enabled) }
    fun setSkipIntro(enabled: Boolean) = updateSetting { it.copy(skipIntro = enabled) }
    fun setSkipOutro(enabled: Boolean) = updateSetting { it.copy(skipOutro = enabled) }
    fun setSyncPercentage(percentage: Int) = updateSetting { it.copy(syncPercentage = percentage.coerceIn(50, 100).toDouble()) }

    fun setDownloadPath(path: String) { viewModelScope.launch { settingsStore.setDownloadPath(path) } }
    fun setSubtitleSize(sizePercent: Int) { viewModelScope.launch { settingsStore.setSubtitleSize(sizePercent) } }

    // Password Change
    fun setCurrentPassword(password: String) { _uiState.update { it.copy(currentPassword = password) } }
    fun setNewPassword(password: String) { _uiState.update { it.copy(newPassword = password) } }

    fun changePassword(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.newPassword.length < 8) {
                _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters") }
                return@launch
            }
            _uiState.update { it.copy(isChangingPassword = true) }
            val response = settingsService.changePassword(state.currentPassword, state.newPassword)
            if (response?.success != false) {
                _uiState.update { it.copy(isChangingPassword = false, currentPassword = "", newPassword = "", successMessage = "Password changed successfully") }
                onSuccess()
            } else {
                _uiState.update { it.copy(isChangingPassword = false, errorMessage = response?.message ?: "Failed to change password") }
            }
        }
    }

    // Storage Management
    fun loadStorageInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStorage = true) }
            val info = storageService.getStorageInfo()
            val downloadInfo = storageService.getDownloadStorageInfo()
            _uiState.update { it.copy(storageInfo = info, downloadStorageInfo = downloadInfo, isLoadingStorage = false) }
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

    // Notifications
    fun loadNotificationPreferences() {
        viewModelScope.launch {
            val enabled = settingsStore.notificationsEnabledFlow.first()
            _uiState.update { it.copy(notificationsEnabled = enabled, hasNotificationPrefsChanges = false) }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled, hasNotificationPrefsChanges = true) }
    }

    fun saveNotificationPreferences() {
        viewModelScope.launch {
            val enabled = _uiState.value.notificationsEnabled
            settingsStore.setNotificationsEnabled(enabled)
            if (enabled) {
                to.kuudere.anisuge.platform.startNotificationListenerService()
            } else {
                to.kuudere.anisuge.platform.stopNotificationListenerService()
            }
            _uiState.update { it.copy(hasNotificationPrefsChanges = false, successMessage = "Notification preferences saved") }
        }
    }

    // ── Tracking ────────────────────────────────────────────────────────────

    fun connectMal(onOpenUrl: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingMal = true) }
            val url = trackingService.getMalLoginUrl()
            _uiState.update { it.copy(isConnectingMal = false) }
            if (url != null) {
                onOpenUrl(url)
            }
        }
    }

    fun disconnectMal() {
        viewModelScope.launch {
            trackingService.disconnectMal()
            _uiState.update { it.copy(malConnected = false, malUsername = null) }
        }
    }

    fun refreshMalConnection() {
        viewModelScope.launch {
            val token = settingsStore.getMalAccessToken()
            if (token != null) {
                val username = try { trackingService.fetchMalUsername() } catch (_: Exception) { null }
                if (username != null) {
                    settingsStore.saveMalUsername(username)
                }
                _uiState.update { it.copy(malConnected = true, malUsername = username) }
            } else {
                _uiState.update { it.copy(malConnected = false, malUsername = null) }
            }
        }
    }

    fun connectAnilist(onOpenUrl: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingAnilist = true) }
            val url = trackingService.getAnilistLoginUrl()
            _uiState.update { it.copy(isConnectingAnilist = false) }
            if (url != null) {
                onOpenUrl(url)
            }
        }
    }

    fun disconnectAnilist() {
        viewModelScope.launch {
            trackingService.disconnectAnilist()
            _uiState.update { it.copy(anilistConnected = false, anilistUsername = null) }
        }
    }

    fun refreshAnilistConnection() {
        viewModelScope.launch {
            val token = settingsStore.getAnilistAccessToken()
            if (token != null) {
                val username = try { trackingService.fetchAnilistUsername() } catch (_: Exception) { null }
                if (username != null) {
                    settingsStore.saveAnilistUsername(username)
                }
                _uiState.update { it.copy(anilistConnected = true, anilistUsername = username) }
            } else {
                _uiState.update { it.copy(anilistConnected = false, anilistUsername = null) }
            }
        }
    }

    fun refreshTrackingState() {
        loadTrackingState()
    }

    fun syncAllToMAL() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingMal = true, errorMessage = null, successMessage = null) }
            try {
                var attempted = 0
                var success = 0
                val response = watchlistService.getWatchlist(limit = 100)
                response?.results?.forEach { item ->
                    val malId = item.anime?.malId ?: return@forEach
                    val folder = item.effectiveFolder ?: return@forEach
                    if (folder == "WATCHING" || folder == "PAUSED" || folder == "COMPLETED") {
                        val totalEpisodes = item.anime.epCount
                        val progressEpisode = item.anime.episode?.episodeNumber?.takeIf { it > 0 }
                            ?: if (folder == "COMPLETED") (totalEpisodes ?: 1) else 1
                        attempted += 1
                        if (trackingService.syncMalProgress(malId, progressEpisode, totalEpisodes)) {
                            success += 1
                        }
                    }
                }
                val failed = attempted - success
                _uiState.update {
                    it.copy(
                        isSyncingMal = false,
                        errorMessage = when {
                            attempted == 0 -> "No eligible MAL items to sync."
                            failed > 0 -> "MAL sync finished: $success/$attempted succeeded."
                            else -> null
                        },
                        successMessage = if (attempted > 0 && failed == 0) {
                            "MAL sync complete: $success/$attempted succeeded."
                        } else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncingMal = false,
                        errorMessage = "MAL sync failed: ${e.message ?: "Unknown error"}",
                    )
                }
            }
        }
    }

    fun syncAllToAniList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingAnilist = true, errorMessage = null, successMessage = null) }
            try {
                var attempted = 0
                var success = 0
                val response = watchlistService.getWatchlist(limit = 100)
                response?.results?.forEach { item ->
                    val anilistId = item.anime?.anilistId ?: return@forEach
                    val folder = item.effectiveFolder ?: return@forEach
                    if (folder == "WATCHING" || folder == "PAUSED" || folder == "COMPLETED") {
                        val totalEpisodes = item.anime.epCount
                        val progressEpisode = item.anime.episode?.episodeNumber?.takeIf { it > 0 }
                            ?: if (folder == "COMPLETED") (totalEpisodes ?: 1) else 1
                        attempted += 1
                        if (trackingService.syncAnilistProgress(anilistId, progressEpisode, totalEpisodes)) {
                            success += 1
                        }
                    }
                }
                val failed = attempted - success
                _uiState.update {
                    it.copy(
                        isSyncingAnilist = false,
                        errorMessage = when {
                            attempted == 0 -> "No eligible AniList items to sync."
                            failed > 0 -> "AniList sync finished: $success/$attempted succeeded."
                            else -> null
                        },
                        successMessage = if (attempted > 0 && failed == 0) {
                            "AniList sync complete: $success/$attempted succeeded."
                        } else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncingAnilist = false,
                        errorMessage = "AniList sync failed: ${e.message ?: "Unknown error"}",
                    )
                }
            }
        }
    }
}
