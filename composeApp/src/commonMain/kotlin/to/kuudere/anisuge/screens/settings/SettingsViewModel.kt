package to.kuudere.anisuge.screens.settings

import to.kuudere.anisuge.AppComponent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.kuudere.anisuge.data.models.Comment
import to.kuudere.anisuge.data.models.CommunityCategory
import to.kuudere.anisuge.data.models.CommunityCommentCreateRequest
import to.kuudere.anisuge.data.models.CommunityCreatePostRequest
import to.kuudere.anisuge.data.models.CommunityLeaderboardUser
import to.kuudere.anisuge.data.models.CommunityPost
import to.kuudere.anisuge.data.models.CommunityStatsResponse
import to.kuudere.anisuge.data.models.DownloadStorageInfo
import to.kuudere.anisuge.data.models.LayoutConfig
import to.kuudere.anisuge.data.models.StorageInfo
import to.kuudere.anisuge.data.models.UserSettings
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.CommunityService
import to.kuudere.anisuge.data.services.SettingsService
import to.kuudere.anisuge.data.services.SettingsStore
import to.kuudere.anisuge.data.services.WatchlistService
import to.kuudere.anisuge.data.services.StorageService
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.services.LibrarySyncDirection
import to.kuudere.anisuge.data.services.TrackingService
import to.kuudere.anisuge.data.services.WatchHistorySyncProgress
import to.kuudere.anisuge.data.services.WatchHistorySyncService
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.models.WatchlistEntry
import to.kuudere.anisuge.platform.clearSyncProgressNotification
import to.kuudere.anisuge.platform.updateSyncProgressNotification
import to.kuudere.anisuge.utils.isNetworkError
import to.kuudere.anisuge.data.models.BffShopItem
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.models.toUserProfile
import to.kuudere.anisuge.data.services.AnimatedFrameBytesCache
import to.kuudere.anisuge.ui.isAnimatedFrameAssetUrl
import to.kuudere.anisuge.ui.resolveProfileMediaUrl
import to.kuudere.anisuge.i18n.AppLocale
import to.kuudere.anisuge.theme.AppThemeId

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    val settings: UserSettings = UserSettings(),
    val hasSettingsChanges: Boolean = false,

    val userProfile: to.kuudere.anisuge.data.models.UserProfile? = null,
    /** True when Anisurge BFF session is valid (independent of profile fetch). */
    val isSignedIn: Boolean = false,
    val isLoadingProfile: Boolean = false,

    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
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
    val expandedHeroCarousel: Boolean = false,
    val appLocale: AppLocale = AppLocale.default,
    val preferRomajiAnimeTitles: Boolean = false,
    val themeId: AppThemeId = AppThemeId.Default,
    val legacyScheduleUi: Boolean = false,
    val homeLayout: LayoutConfig = LayoutConfig.DEFAULT,
    val layoutSaveError: String? = null,

    val notificationsEnabled: Boolean = true,
    val hasNotificationPrefsChanges: Boolean = false,

    // Tracking
    val malConnected: Boolean = false,
    val malUsername: String? = null,
    val anilistConnected: Boolean = false,
    val anilistUsername: String? = null,
    val isConnectingMal: Boolean = false,
    val isConnectingAnilist: Boolean = false,
    val isUploadingPfp: Boolean = false,
    val showProfileAccount: Boolean = false,
    /** Mobile settings drill-in (Sync, Preferences, etc.); cleared on back. */
    val mobileSettingsDetailTab: SettingsTab? = null,
    val usernameDraft: String = "",
    val isSavingUsername: Boolean = false,
    val isSyncingMal: Boolean = false,
    val isSyncingAnilist: Boolean = false,
    val lunarConnected: Boolean = false,
    val lunarUsername: String? = null,
    val isConnectingLunar: Boolean = false,
    val isImportingLunar: Boolean = false,
    val isExportingLunar: Boolean = false,
    val lunarSyncCurrent: Int = 0,
    val lunarSyncTotal: Int = 0,
    val lunarSyncDetail: String? = null,

    /** Bulk sync from account export to linked MAL / AniList. */
    val isWatchHistorySyncing: Boolean = false,
    val watchHistoryCurrent: Int = 0,
    val watchHistoryTotal: Int = 0,
    /** SSE message or current anime title during import. */
    val watchHistoryDetail: String? = null,
    val watchHistoryMalDone: Boolean = false,
    val watchHistoryAnilistDone: Boolean = false,

    // Community
    val isLoadingCommunity: Boolean = false,
    val isLoadingCommunityMore: Boolean = false,
    val communityStats: CommunityStatsResponse? = null,
    val communityCategories: List<CommunityCategory> = emptyList(),
    val communityTrendingPosts: List<CommunityPost> = emptyList(),
    val communityPosts: List<CommunityPost> = emptyList(),
    val communityLeaderboard: List<CommunityLeaderboardUser> = emptyList(),
    val communityUnreadCount: Int = 0,
    val communitySort: String = "hot",
    val communityCategory: String = "all",
    val communityLeaderboardPeriod: String = "all",
    val communityHasMore: Boolean = false,
    val communityOffset: Int = 0,
    val isCreatingCommunityPost: Boolean = false,
    val isVotingCommunityPostIds: Set<String> = emptySet(),
    val communityDraftTitle: String = "",
    val communityDraftContent: String = "",
    val communityDraftCategory: String = "general",
    val communityDraftFlair: String = "Discussion",
    val communityDraftSpoiler: Boolean = false,
    /** Open full-screen community post detail (post id or null when closed). */
    val communityDetailPostId: String? = null,
    val communityDetailPost: CommunityPost? = null,
    val communityDetailComments: List<Comment> = emptyList(),
    val isLoadingCommunityDetail: Boolean = false,
    val communityDetailCommentDraft: String = "",
    val communityDetailCommentSpoiler: Boolean = false,
    val isPostingCommunityComment: Boolean = false,
    /** Full-screen image/GIF viewer URL overlay. */
    val communityFullscreenImageUrl: String? = null,

    val shopCoins: Int = 0,
    val shopCatalog: List<to.kuudere.anisuge.data.models.BffShopItem> = emptyList(),
    val shopOwned: List<to.kuudere.anisuge.data.models.BffShopItem> = emptyList(),
    val isLoadingOwnedFrames: Boolean = false,
    val isLoadingShop: Boolean = false,
    val isLoadingMoreShop: Boolean = false,
    val shopCatalogHasMore: Boolean = false,
    val shopCatalogTotal: Int = 0,
    val shopPurchasingId: String? = null,
    val redeemCodeDraft: String = "",
    val isRedeemingCode: Boolean = false,
    val isDownloadingShopFrames: Boolean = false,
    val isSavingEquippedFrame: Boolean = false,
    val rewardsLoginStreak: Int = 0,
    val rewardsCanClaimDaily: Boolean = false,
    val rewardsNextDaily: Int = 0,
    val rewardsTodayWatch: Int = 0,
    val rewardsTodayWatchCap: Int = 12,
    val isClaimingDailyReward: Boolean = false,
    val isConnectingReanime: Boolean = false,
    val isDisconnectingReanime: Boolean = false,
    val isImportingReanime: Boolean = false,
    val isExportingReanime: Boolean = false,
)

sealed class SettingsTab {
    data object Profile : SettingsTab()
    data object Preferences : SettingsTab()
    data object Storage : SettingsTab()
    data object Appearance : SettingsTab()
    data object Sync : SettingsTab()
    data object Connect : SettingsTab()
    data object Community : SettingsTab()
    data object Servers : SettingsTab()
    data object Notifications : SettingsTab()
    data object Shop : SettingsTab()
    data object Berries : SettingsTab()
}

class SettingsViewModel(
    private val settingsService: SettingsService,
    private val settingsStore: SettingsStore,
    private val serverRepository: ServerRepository,
    private val authService: AuthService,
    private val trackingService: TrackingService,
    private val watchlistService: WatchlistService,
    private val communityService: CommunityService,
    private val watchHistorySyncService: WatchHistorySyncService,
    private val integrationsSyncService: to.kuudere.anisuge.data.services.IntegrationsSyncService,
    private val bffMeService: to.kuudere.anisuge.data.services.BffMeService,
    private val bffShopService: to.kuudere.anisuge.data.services.BffShopService,
    private val bffRewardsService: to.kuudere.anisuge.data.services.BffRewardsService,
    private val storageService: StorageService = StorageService(),
) : ViewModel() {

    private companion object {
        const val SHOP_PAGE_SIZE = 15
        const val SHOP_PREFETCH_CONCURRENCY = 6
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var originalSettings: UserSettings = UserSettings()
    private var playbackSettingsSyncJob: Job? = null
    private var playbackSettingsSyncGeneration = 0

    init {
        viewModelScope.launch {
            authService.authState.collect { result ->
                if (result is SessionCheckResult.Valid) {
                    _uiState.update {
                        it.copy(
                            isSignedIn = true,
                            userProfile = result.user ?: it.userProfile,
                            isLoadingProfile = false,
                        )
                    }
                    loadSettings()
                    loadTrackingState()
                    loadShopInventory()
                    prefetchEquippedFrame(result.user)
                } else if (result is SessionCheckResult.NoSession || result is SessionCheckResult.Expired) {
                    _uiState.update { it.copy(userProfile = null, isSignedIn = false) }
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
        viewModelScope.launch {
            settingsStore.floatingBottomNavFlow.collect { v ->
                _uiState.update {
                    it.copy(
                        floatingBottomNav = v
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.liquidGlassBottomNavFlow.collect { v ->
                _uiState.update {
                    it.copy(
                        liquidGlassBottomNav = v
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.expandedHeroCarouselFlow.collect { v ->
                _uiState.update {
                    it.copy(
                        expandedHeroCarousel = v
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.appLocaleFlow.collect { code ->
                _uiState.update {
                    it.copy(
                        appLocale = AppLocale.fromCode(
                            code
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.themeIdFlow.collect { id ->
                _uiState.update {
                    it.copy(
                        themeId = AppThemeId.fromId(
                            id
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.preferRomajiAnimeTitlesFlow.collect { v ->
                _uiState.update { it.copy(preferRomajiAnimeTitles = v) }
            }
        }
        viewModelScope.launch {
            settingsStore.legacyScheduleUiFlow.collect { v ->
                _uiState.update { it.copy(legacyScheduleUi = v) }
            }
        }
        viewModelScope.launch {
            settingsStore.homeLayoutFlow.collect { layout ->
                _uiState.update { it.copy(homeLayout = layout) }
            }
        }

        // Load tracking state
        loadTrackingState()
    }

    private fun loadTrackingState() {
        viewModelScope.launch {
            if (authService.authState.value is SessionCheckResult.Valid) {
                integrationsSyncService.restoreFromServer()
            }
            val malToken = settingsStore.getMalAccessToken()
            val anilistToken = settingsStore.getAnilistAccessToken()
            val lunarToken = settingsStore.getLunarAccessToken()
            val malUsername = settingsStore.getMalUsername()
            val anilistUsername = settingsStore.getAnilistUsername()
            val lunarUsername = settingsStore.getLunarUsername()
            _uiState.update {
                it.copy(
                    malConnected = malToken != null,
                    malUsername = malUsername,
                    anilistConnected = anilistToken != null,
                    anilistUsername = anilistUsername,
                    lunarConnected = lunarToken != null,
                    lunarUsername = lunarUsername,
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true, isLoading = true) }
            val result = authService.checkSession()
            if (result is SessionCheckResult.Valid) {
                _uiState.update { it.copy(isSignedIn = true) }
                loadUserProfile()
                loadShopInventory()
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
            is SettingsTab.Profile -> {
                loadUserProfile()
                loadShopInventory()
            }

            is SettingsTab.Shop -> {
                loadShop()
                if (_uiState.value.userProfile == null) {
                    loadUserProfile()
                }
            }

            is SettingsTab.Berries -> {
                loadUserProfile()
                loadShopInventory()
                loadRewardsStatus()
            }

            is SettingsTab.Sync -> {
                loadTrackingState()
                if (_uiState.value.userProfile == null && authService.authState.value is SessionCheckResult.Valid) {
                    loadUserProfile()
                }
            }

            is SettingsTab.Connect -> {
                if (_uiState.value.userProfile == null && authService.authState.value is SessionCheckResult.Valid) {
                    loadUserProfile()
                }
            }

            is SettingsTab.Servers -> loadServerPriority()
            is SettingsTab.Notifications -> loadNotificationPreferences()
            // is SettingsTab.Community -> loadCommunityInitial()
            else -> {}
        }
    }

    fun setFloatingBottomNav(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setFloatingBottomNav(enabled) }
    }

    fun setLiquidGlassBottomNav(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLiquidGlassBottomNav(enabled) }
    }

    fun setExpandedHeroCarousel(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setExpandedHeroCarousel(enabled) }
    }

    fun setAppLocale(locale: AppLocale) {
        viewModelScope.launch { settingsStore.setAppLocale(locale.code) }
    }

    fun setPreferRomajiAnimeTitles(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setPreferRomajiAnimeTitles(enabled) }
    }

    fun setThemeId(themeId: AppThemeId) {
        viewModelScope.launch { settingsStore.setThemeId(themeId.id) }
    }

    fun setLegacyScheduleUi(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLegacyScheduleUi(enabled) }
    }

    fun setHomeLayout(next: LayoutConfig) {
        val normalized = next.sanitize().mergeWithDefaults()
        _uiState.update { it.copy(homeLayout = normalized) }
        viewModelScope.launch {
            try {
                settingsStore.setHomeLayout(normalized)
                _uiState.update { it.copy(layoutSaveError = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(layoutSaveError = e.message ?: "save failed") }
            }
        }
    }

    fun resetHomeLayout() {
        setHomeLayout(LayoutConfig.DEFAULT)
    }

    fun retrySaveHomeLayout() {
        setHomeLayout(_uiState.value.homeLayout)
    }

    fun dismissLayoutSaveError() {
        _uiState.update { it.copy(layoutSaveError = null) }
    }

    // Profile
    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfile = true, isOffline = false) }
            bffMeService.fetchMe().fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            userProfile = profile,
                            shopCoins = profile.coins,
                            isLoadingProfile = false,
                            isOffline = false,
                            usernameDraft = if (it.showProfileAccount) it.usernameDraft else profile.username.orEmpty(),
                        )
                    }
                    prefetchEquippedFrame(profile)
                    if (_uiState.value.shopOwned.isEmpty()) {
                        loadShopInventory()
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            isOffline = e.isNetworkError(),
                            errorMessage = if (e.isNetworkError()) null else (e.message
                                ?: "Failed to load user profile"),
                        )
                    }
                },
            )
        }
    }

    fun openProfileAccount() {
        loadShopInventory()
        val username = _uiState.value.userProfile?.username.orEmpty()
        _uiState.update { it.copy(showProfileAccount = true, usernameDraft = username) }
    }

    fun closeProfileAccount() {
        _uiState.update { it.copy(showProfileAccount = false) }
    }

    fun openMobileSettingsDetail(tab: SettingsTab) {
        _uiState.update { it.copy(mobileSettingsDetailTab = tab) }
    }

    fun closeMobileSettingsDetail() {
        _uiState.update { it.copy(mobileSettingsDetailTab = null) }
    }

    /** Handles profile account overlay or mobile settings detail; returns true if consumed. */
    fun handleSettingsBack(): Boolean {
        val state = _uiState.value
        return when {
            state.showProfileAccount -> {
                closeProfileAccount()
                true
            }

            state.mobileSettingsDetailTab != null -> {
                closeMobileSettingsDetail()
                true
            }

            else -> false
        }
    }

    fun setUsernameDraft(value: String) {
        _uiState.update { it.copy(usernameDraft = value) }
    }

    fun saveUsername() {
        val draft = _uiState.value.usernameDraft.trim()
        if (draft.length < 3) {
            _uiState.update { it.copy(errorMessage = "Username must be at least 3 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingUsername = true, errorMessage = null) }
            bffMeService.updateUsername(draft).fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(
                            isSavingUsername = false,
                            userProfile = profile,
                            usernameDraft = profile.username.orEmpty(),
                            successMessage = "Username updated",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isSavingUsername = false,
                            errorMessage = e.message ?: "Failed to update username",
                        )
                    }
                },
            )
        }
    }

    fun loadRewardsStatus() {
        viewModelScope.launch {
            bffRewardsService.fetchStatus().onSuccess { status ->
                _uiState.update {
                    it.copy(
                        shopCoins = status.coins,
                        rewardsLoginStreak = status.loginStreak,
                        rewardsCanClaimDaily = status.canClaimDaily,
                        rewardsNextDaily = status.nextDailyReward,
                        rewardsTodayWatch = status.todayEarned.watch,
                        rewardsTodayWatchCap = status.todayEarned.watchCap,
                    )
                }
            }
        }
    }

    fun claimDailyReward() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClaimingDailyReward = true) }
            bffRewardsService.claimDaily().fold(
                onSuccess = { body ->
                    _uiState.update {
                        it.copy(
                            isClaimingDailyReward = false,
                            shopCoins = body.coins,
                            successMessage = when {
                                body.granted -> body.message ?: "+${body.amount} Berries — daily check-in"
                                else -> body.message
                            },
                        )
                    }
                    loadRewardsStatus()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isClaimingDailyReward = false,
                            errorMessage = e.message ?: "Failed to claim daily reward",
                        )
                    }
                },
            )
        }
    }

    fun loadShop() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingShop = true) }
            bffShopService.fetchShopMe(catalogLimit = SHOP_PAGE_SIZE, catalogOffset = 0).fold(
                onSuccess = { body ->
                    _uiState.update {
                        it.copy(
                            isLoadingShop = false,
                            shopCoins = body.coins,
                            shopCatalog = body.catalog,
                            shopOwned = body.owned,
                            shopCatalogHasMore = body.catalogHasMore,
                            shopCatalogTotal = body.catalogTotal,
                        )
                    }
                    prefetchShopCatalogFrames(body.catalog)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingShop = false,
                            errorMessage = e.message ?: "Failed to load shop",
                        )
                    }
                },
            )
        }
    }

    fun loadMoreShop() {
        val state = _uiState.value
        if (state.isLoadingShop || state.isLoadingMoreShop || !state.shopCatalogHasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreShop = true) }
            bffShopService.fetchShopMe(
                catalogLimit = SHOP_PAGE_SIZE,
                catalogOffset = state.shopCatalog.size,
            ).fold(
                onSuccess = { body ->
                    val merged = (state.shopCatalog + body.catalog).distinctBy { it.id }
                    _uiState.update { current ->
                        current.copy(
                            isLoadingMoreShop = false,
                            shopCoins = body.coins,
                            shopCatalog = merged,
                            shopOwned = body.owned,
                            shopCatalogHasMore = body.catalogHasMore,
                            shopCatalogTotal = body.catalogTotal,
                        )
                    }
                    prefetchShopCatalogFrames(body.catalog)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingMoreShop = false,
                            errorMessage = e.message ?: "Failed to load more",
                        )
                    }
                },
            )
        }
    }

    /** Store tab only — catalog page bytes; does not block UI (owned frames prefetched via [loadShopInventory]). */
    private fun prefetchShopCatalogFrames(catalog: List<to.kuudere.anisuge.data.models.BffShopItem>) {
        if (catalog.isEmpty()) return
        val entries = catalog.mapNotNull { item ->
            val url = resolveProfileMediaUrl(item.assetUrl) ?: return@mapNotNull null
            url to item.id
        }
        if (entries.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            AnimatedFrameBytesCache.prefetchEntries(entries, concurrency = SHOP_PREFETCH_CONCURRENCY)
        }
    }

    private suspend fun prefetchShopFrameAnimations(
        owned: List<to.kuudere.anisuge.data.models.BffShopItem>,
        catalog: List<to.kuudere.anisuge.data.models.BffShopItem> = emptyList(),
    ) {
        val items = (owned + catalog).distinctBy { it.id }
        if (items.isEmpty()) return
        val entries = items.mapNotNull { item ->
            val url = resolveProfileMediaUrl(item.assetUrl) ?: return@mapNotNull null
            url to item.id
        }
        if (entries.isEmpty()) return
        withContext(Dispatchers.Default) {
            AnimatedFrameBytesCache.prefetchEntries(entries, concurrency = SHOP_PREFETCH_CONCURRENCY)
        }
    }

    private fun prefetchEquippedFrame(profile: UserProfile?) {
        if (profile == null) return
        val url = resolveProfileMediaUrl(profile.equippedFrameUrl) ?: return
        viewModelScope.launch(Dispatchers.Default) {
            AnimatedFrameBytesCache.load(url, itemId = profile.equippedFrameItemId)
        }
    }

    fun loadShopInventory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOwnedFrames = true) }
            bffShopService.fetchOwned().fold(
                onSuccess = { body ->
                    viewModelScope.launch {
                        prefetchShopFrameAnimations(body.owned)
                    }
                    _uiState.update {
                        it.copy(
                            shopOwned = body.owned,
                            isLoadingOwnedFrames = false,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingOwnedFrames = false) }
                },
            )
        }
    }

    fun setRedeemCodeDraft(value: String) {
        _uiState.update { it.copy(redeemCodeDraft = value) }
    }

    fun redeemShopCode() {
        val code = _uiState.value.redeemCodeDraft.trim()
        if (code.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter a redeem code") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRedeemingCode = true, errorMessage = null) }
            bffShopService.redeem(code).fold(
                onSuccess = { body ->
                    val profile = body.user?.toUserProfile()
                    _uiState.update {
                        it.copy(
                            isRedeemingCode = false,
                            redeemCodeDraft = "",
                            shopCoins = body.coins,
                            userProfile = profile ?: it.userProfile?.copy(coins = body.coins),
                            successMessage = body.message
                                ?: "Redeemed ${body.rewardCoins} Berries",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isRedeemingCode = false,
                            errorMessage = e.message ?: "Could not redeem code",
                        )
                    }
                },
            )
        }
    }

    fun purchaseShopItem(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(shopPurchasingId = itemId) }
            bffShopService.purchase(itemId).fold(
                onSuccess = { body ->
                    val profile = body.user?.toUserProfile()
                    _uiState.update {
                        it.copy(
                            shopPurchasingId = null,
                            shopCoins = body.coins,
                            shopCatalog = it.shopCatalog.map { item ->
                                if (item.id == body.item.id) body.item else item
                            },
                            shopOwned = if (it.shopOwned.any { o -> o.id == body.item.id }) {
                                it.shopOwned
                            } else {
                                it.shopOwned + body.item
                            },
                            userProfile = profile ?: it.userProfile?.copy(coins = body.coins),
                            successMessage = "Purchased ${body.item.name}",
                        )
                    }
                    prefetchShopCatalogFrames(listOf(body.item))
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            shopPurchasingId = null,
                            errorMessage = e.message ?: "Purchase failed",
                        )
                    }
                },
            )
        }
    }

    fun downloadOwnedShopFrames() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingShopFrames = true) }
            bffShopService.fetchDownloadManifest().fold(
                onSuccess = { manifest ->
                    var saved = 0
                    for (entry in manifest.items) {
                        bffShopService.downloadFrameBytes(entry.assetUrl).onSuccess { bytes ->
                            val resolved = resolveProfileMediaUrl(entry.assetUrl)
                            if (resolved != null) {
                                AnimatedFrameBytesCache.store(resolved, bytes, itemId = entry.id)
                            } else {
                                to.kuudere.anisuge.data.services.ShopFrameCache.save(entry.id, bytes)
                            }
                            saved++
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isDownloadingShopFrames = false,
                            successMessage = "Saved $saved frame(s) offline",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isDownloadingShopFrames = false,
                            errorMessage = e.message ?: "Download failed",
                        )
                    }
                },
            )
        }
    }

    fun equipShopFrame(item: to.kuudere.anisuge.data.models.BffShopItem?) {
        viewModelScope.launch {
            val profile = _uiState.value.userProfile ?: return@launch
            _uiState.update {
                it.copy(
                    isSavingEquippedFrame = true,
                    userProfile = profile.copy(
                        equippedFrameUrl = item?.assetUrl,
                        equippedFrameItemId = item?.id,
                    ),
                )
            }
            bffMeService.updateEquippedFrame(
                currentEquipped = profile.equipped,
                frameUrl = item?.assetUrl,
                frameItemId = item?.id,
            ).fold(
                onSuccess = { updated ->
                    authService.checkSession()
                    _uiState.update {
                        it.copy(
                            isSavingEquippedFrame = false,
                            userProfile = updated,
                            successMessage = if (item == null) "Frame removed" else "Equipped ${item.name}",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isSavingEquippedFrame = false,
                            errorMessage = e.message ?: "Failed to equip frame",
                        )
                    }
                },
            )
        }
    }

    // Server Priority
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
        _uiState.update {
            it.copy(
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
            _uiState.update { it.copy(hasServerPriorityChanges = false, successMessage = "Server priority saved") }
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

    // Settings
    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isOffline = false) }
            try {
                val loaded = settingsService.getSettings()
                if (loaded != null) {
                    applySettingsFromServer(loaded)
                } else {
                    applyLocalPlaybackSettingsFallback(errorMessage = "Failed to load settings")
                }
            } catch (e: Exception) {
                applyLocalPlaybackSettingsFallback(
                    isOffline = e.isNetworkError(),
                    errorMessage = if (e.isNetworkError()) null else "Failed to load settings",
                )
            }
        }
    }

    private suspend fun applySettingsFromServer(loaded: UserSettings) {
        originalSettings = loaded
        _uiState.update { it.copy(settings = loaded, isLoading = false, hasSettingsChanges = false, isOffline = false) }
        pushPlaybackPrefsToLocalStore(loaded)
    }

    private suspend fun applyLocalPlaybackSettingsFallback(
        isOffline: Boolean = true,
        errorMessage: String? = null,
    ) {
        val local = readLocalPlaybackSettings()
        originalSettings = local
        _uiState.update {
            it.copy(
                settings = local,
                isLoading = false,
                hasSettingsChanges = false,
                isOffline = isOffline,
                errorMessage = errorMessage,
            )
        }
    }

    private suspend fun readLocalPlaybackSettings(): UserSettings {
        return UserSettings(
            autoPlay = settingsStore.autoPlayFlow.first(),
            autoNext = settingsStore.autoNextFlow.first(),
            skipIntro = settingsStore.autoSkipIntroFlow.first(),
            skipOutro = settingsStore.autoSkipOutroFlow.first(),
            defaultLang = settingsStore.defaultLangFlow.first(),
            syncPercentage = settingsStore.syncPercentageFlow.first().toDouble(),
            showComments = _uiState.value.settings.showComments,
            publicWatchlist = _uiState.value.settings.publicWatchlist,
        )
    }

    private suspend fun pushPlaybackPrefsToLocalStore(settings: UserSettings) {
        settingsStore.setAutoPlay(settings.autoPlay)
        settingsStore.setAutoNext(settings.autoNext)
        settingsStore.setAutoSkipIntro(settings.skipIntro)
        settingsStore.setAutoSkipOutro(settings.skipOutro)
        settingsStore.setDefaultLang(settings.defaultLang)
        settingsStore.setSyncPercentage(settings.syncPercentage.toInt())
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val saved = settingsService.updateSettings(_uiState.value.settings)
            if (saved != null) {
                originalSettings = saved
                _uiState.update {
                    it.copy(
                        settings = saved,
                        isSaving = false,
                        hasSettingsChanges = false,
                        successMessage = "Settings saved successfully",
                    )
                }
                pushPlaybackPrefsToLocalStore(saved)
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

    fun setDefaultLang(enabled: Boolean) = persistPlaybackPreference { it.copy(defaultLang = enabled) }
    fun setAutoNext(enabled: Boolean) = persistPlaybackPreference { it.copy(autoNext = enabled) }
    fun setAutoPlay(enabled: Boolean) = persistPlaybackPreference { it.copy(autoPlay = enabled) }
    fun setSkipIntro(enabled: Boolean) = persistPlaybackPreference { it.copy(skipIntro = enabled) }
    fun setSkipOutro(enabled: Boolean) = persistPlaybackPreference { it.copy(skipOutro = enabled) }
    fun setSyncPercentage(percentage: Int) =
        persistPlaybackPreference { it.copy(syncPercentage = percentage.coerceIn(50, 100).toDouble()) }

    /** Apply playback prefs locally and to the account immediately (no separate Save tap). */
    private fun persistPlaybackPreference(updater: (UserSettings) -> UserSettings) {
        updateSetting(updater)
        val prefs = _uiState.value.settings
        val generation = ++playbackSettingsSyncGeneration
        playbackSettingsSyncJob?.cancel()
        viewModelScope.launch {
            pushPlaybackPrefsToLocalStore(prefs)
        }
        playbackSettingsSyncJob = viewModelScope.launch {
            delay(250)
            syncPlaybackSettingsToServer(prefs, generation)
        }
    }

    private suspend fun syncPlaybackSettingsToServer(prefs: UserSettings, generation: Int) {
        try {
            val current = settingsService.getSettings()
            if (generation != playbackSettingsSyncGeneration) return
            val base = current ?: UserSettings()
            val merged = base.copy(
                autoPlay = prefs.autoPlay,
                autoNext = prefs.autoNext,
                skipIntro = prefs.skipIntro,
                skipOutro = prefs.skipOutro,
                defaultLang = prefs.defaultLang,
                syncPercentage = prefs.syncPercentage,
            )
            val saved = settingsService.updateSettings(merged)
            if (generation != playbackSettingsSyncGeneration) return
            if (saved != null) {
                originalSettings = saved
                _uiState.update {
                    it.copy(
                        settings = saved,
                        hasSettingsChanges = it.settings != originalSettings,
                    )
                }
                pushPlaybackPrefsToLocalStore(saved)
            }
        } catch (e: Exception) {
            println("[SettingsViewModel] syncPlaybackSettings error: ${e.message}")
        }
    }

    fun setDownloadPath(path: String) {
        viewModelScope.launch { settingsStore.setDownloadPath(path) }
    }

    fun setSubtitleSize(sizePercent: Int) {
        viewModelScope.launch { settingsStore.setSubtitleSize(sizePercent) }
    }

    // Password Change
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
            if (state.currentPassword.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Enter your current password") }
                return@launch
            }
            if (state.newPassword.length < 8) {
                _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters") }
                return@launch
            }
            if (state.newPassword != state.confirmPassword) {
                _uiState.update { it.copy(errorMessage = "New passwords do not match") }
                return@launch
            }
            _uiState.update { it.copy(isChangingPassword = true) }
            val response = settingsService.changePassword(state.currentPassword, state.newPassword)
            if (response?.success != false) {
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

    // Storage Management
    fun loadStorageInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStorage = true) }
            try {
                val info = storageService.getStorageInfo()
                val downloadInfo = storageService.getDownloadStorageInfo()
                _uiState.update {
                    it.copy(
                        storageInfo = info,
                        downloadStorageInfo = downloadInfo,
                        isLoadingStorage = false,
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingStorage = false,
                        errorMessage = "Could not load storage info",
                    )
                }
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
            _uiState.update {
                it.copy(
                    hasNotificationPrefsChanges = false,
                    successMessage = "Notification preferences saved"
                )
            }
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
                val username = try {
                    trackingService.fetchMalUsername()
                } catch (_: Exception) {
                    null
                }
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
                val username = try {
                    trackingService.fetchAnilistUsername()
                } catch (_: Exception) {
                    null
                }
                if (username != null) {
                    settingsStore.saveAnilistUsername(username)
                }
                _uiState.update { it.copy(anilistConnected = true, anilistUsername = username) }
            } else {
                _uiState.update { it.copy(anilistConnected = false, anilistUsername = null) }
            }
        }
    }

    fun connectLunar(onOpenUrl: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingLunar = true) }
            val url = trackingService.getLunarLoginUrl()
            _uiState.update { it.copy(isConnectingLunar = false) }
            if (url != null) {
                onOpenUrl(url)
            }
        }
    }

    fun disconnectLunar() {
        viewModelScope.launch {
            trackingService.disconnectLunar()
            _uiState.update { it.copy(lunarConnected = false, lunarUsername = null) }
        }
    }

    fun refreshLunarConnection() {
        viewModelScope.launch {
            val token = settingsStore.getLunarAccessToken()
            if (token != null) {
                val username = try {
                    val profile = trackingService.fetchLunarProfile(token)
                    profile?.username
                } catch (_: Exception) {
                    null
                }
                if (username != null) {
                    val userId = settingsStore.getLunarUserId() ?: ""
                    settingsStore.saveLunarUsernameAndId(username, userId)
                    integrationsSyncService.pushFromLocal()
                }
                _uiState.update { it.copy(lunarConnected = true, lunarUsername = username) }
            } else {
                _uiState.update { it.copy(lunarConnected = false, lunarUsername = null) }
            }
        }
    }

    fun importLibraryFromLunar() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    isImportingLunar = true,
                    lunarSyncCurrent = 0,
                    lunarSyncTotal = 0,
                    lunarSyncDetail = "Fetching LunarAnime watchlist...",
                    errorMessage = null,
                    successMessage = null,
                )
            }
            try {
                updateSyncProgressNotification(
                    title = "Importing from LunarAnime",
                    statusText = "Fetching LunarAnime watchlist...",
                    progressCurrent = 0,
                    progressMax = 0,
                )
                val items = trackingService.fetchLunarWatchlist()
                var imported = 0
                var failed = 0
                val total = items.size
                _uiState.update { it.copy(lunarSyncTotal = total) }
                items.forEachIndexed { index, item ->
                    val slug = item.slug.trim()
                    if (slug.isEmpty()) {
                        return@forEachIndexed
                    }
                    val step = index + 1
                    val detail = item.title?.takeIf { it.isNotBlank() } ?: slug
                    _uiState.update {
                        it.copy(
                            lunarSyncCurrent = step,
                            lunarSyncTotal = total,
                            lunarSyncDetail = detail,
                        )
                    }
                    updateSyncProgressNotification(
                        title = "Importing from LunarAnime",
                        statusText = "$detail\n$step / ${maxOf(1, total)}",
                        progressCurrent = step,
                        progressMax = maxOf(1, total),
                    )
                    val ok = watchlistService.updateStatus(slug, "PLANNING") != null
                    if (ok) imported++ else failed++
                    if ((imported + failed) % 10 == 0) delay(50L)
                }
                _uiState.update {
                    it.copy(
                        isImportingLunar = false,
                        lunarSyncCurrent = 0,
                        lunarSyncTotal = 0,
                        lunarSyncDetail = null,
                        successMessage = if (imported > 0) "Imported $imported LunarAnime items." else null,
                        errorMessage = when {
                            items.isEmpty() -> "No LunarAnime anime watchlist items found."
                            failed > 0 -> "LunarAnime import finished: $imported imported, $failed failed."
                            else -> null
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImportingLunar = false,
                        lunarSyncCurrent = 0,
                        lunarSyncTotal = 0,
                        lunarSyncDetail = null,
                        errorMessage = "LunarAnime import failed: ${e.message ?: "Unknown error"}",
                    )
                }
            } finally {
                clearSyncProgressNotification()
            }
        }
    }

    fun exportLibraryToLunar() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    isExportingLunar = true,
                    lunarSyncCurrent = 0,
                    lunarSyncTotal = 0,
                    lunarSyncDetail = "Preparing LunarAnime export...",
                    errorMessage = null,
                    successMessage = null,
                )
            }
            try {
                updateSyncProgressNotification(
                    title = "Exporting to LunarAnime",
                    statusText = "Fetching LunarAnime watchlist...",
                    progressCurrent = 0,
                    progressMax = 0,
                )
                val remoteSlugs = trackingService.fetchLunarWatchlist()
                    .map { it.slug.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val local = loadAllWatchlistEntries()
                val total = local.size
                var exported = 0
                var skipped = 0
                var failed = 0
                _uiState.update { it.copy(lunarSyncTotal = total) }
                local.forEachIndexed { index, entry ->
                    val slug = entry.effectiveAnimeId.trim()
                    val step = index + 1
                    val detail = entry.anime.displayTitle.takeIf { it.isNotBlank() } ?: slug.ifBlank { "Unknown item" }
                    _uiState.update {
                        it.copy(
                            lunarSyncCurrent = step,
                            lunarSyncTotal = total,
                            lunarSyncDetail = detail,
                        )
                    }
                    updateSyncProgressNotification(
                        title = "Exporting to LunarAnime",
                        statusText = "$detail\n$step / ${maxOf(1, total)}",
                        progressCurrent = step,
                        progressMax = maxOf(1, total),
                    )
                    if (slug.isEmpty()) {
                        skipped++
                        return@forEachIndexed
                    }
                    if (slug.lowercase() in remoteSlugs) {
                        skipped++
                        return@forEachIndexed
                    }
                    val ok = trackingService.toggleLunarWatchlistAnime(slug)
                    if (ok) exported++ else failed++
                    if ((exported + failed) % 10 == 0) delay(80L)
                }
                _uiState.update {
                    it.copy(
                        isExportingLunar = false,
                        lunarSyncCurrent = 0,
                        lunarSyncTotal = 0,
                        lunarSyncDetail = null,
                        successMessage = if (exported > 0) "Exported $exported items to LunarAnime." else null,
                        errorMessage = when {
                            local.isEmpty() -> "No Anisurge watchlist items to export."
                            exported == 0 && failed == 0 -> "LunarAnime already has your exported items."
                            failed > 0 -> "LunarAnime export finished: $exported exported, $failed failed, $skipped skipped."
                            else -> null
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExportingLunar = false,
                        lunarSyncCurrent = 0,
                        lunarSyncTotal = 0,
                        lunarSyncDetail = null,
                        errorMessage = "LunarAnime export failed: ${e.message ?: "Unknown error"}",
                    )
                }
            } finally {
                clearSyncProgressNotification()
            }
        }
    }

    fun refreshTrackingState() {
        loadTrackingState()
    }

    fun onCustomPfpPicked(pick: to.kuudere.anisuge.platform.ChatImagePick?) {
        if (pick == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPfp = true, errorMessage = null) }
            bffMeService.uploadCustomPfp(pick).fold(
                onSuccess = { profile ->
                    authService.checkSession()
                    _uiState.update {
                        it.copy(
                            isUploadingPfp = false,
                            userProfile = profile,
                            successMessage = "Profile picture updated",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isUploadingPfp = false,
                            errorMessage = e.message ?: "Failed to upload profile picture",
                        )
                    }
                },
            )
        }
    }

    // ── Community ───────────────────────────────────────────────────────────────

    fun refreshCommunity() {
        loadCommunityInitial(force = true)
    }

    fun setCommunitySort(sort: String) {
        _uiState.update { it.copy(communitySort = sort) }
        loadCommunityInitial(force = true)
    }

    fun setCommunityCategory(category: String) {
        _uiState.update { it.copy(communityCategory = category) }
        loadCommunityInitial(force = true)
    }

    fun setCommunityLeaderboardPeriod(period: String) {
        _uiState.update { it.copy(communityLeaderboardPeriod = period) }
        viewModelScope.launch {
            val leaderboard = communityService.getLeaderboard(period)?.users ?: emptyList()
            _uiState.update { it.copy(communityLeaderboard = leaderboard) }
        }
    }

    fun setCommunityDraftTitle(value: String) {
        _uiState.update { it.copy(communityDraftTitle = value) }
    }

    fun setCommunityDraftContent(value: String) {
        _uiState.update { it.copy(communityDraftContent = value) }
    }

    fun setCommunityDraftCategory(value: String) {
        _uiState.update { it.copy(communityDraftCategory = value) }
    }

    fun setCommunityDraftFlair(value: String) {
        _uiState.update { it.copy(communityDraftFlair = value) }
    }

    fun setCommunityDraftSpoiler(value: Boolean) {
        _uiState.update { it.copy(communityDraftSpoiler = value) }
    }

    fun loadCommunityInitial(force: Boolean = false) {
        if (_uiState.value.isLoadingCommunity && !force) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCommunity = true,
                    errorMessage = null,
                )
            }

            val sort = _uiState.value.communitySort
            val category = _uiState.value.communityCategory
            val period = _uiState.value.communityLeaderboardPeriod
            try {
                val statsDeferred = async { communityService.getStats() }
                val categoriesDeferred = async { communityService.getCategories() }
                val trendingDeferred = async { communityService.getTrending() }
                val postsDeferred =
                    async { communityService.getPosts(sort = sort, category = category, limit = 10, offset = 0) }
                val leaderboardDeferred = async { communityService.getLeaderboard(period) }
                val unreadDeferred = async { communityService.getUnreadCount() }
                awaitAll(
                    statsDeferred,
                    categoriesDeferred,
                    trendingDeferred,
                    postsDeferred,
                    leaderboardDeferred,
                    unreadDeferred
                )

                val stats = statsDeferred.await()
                val categories = categoriesDeferred.await()?.categories ?: emptyList()
                val trending = trendingDeferred.await()?.posts ?: emptyList()
                val postsResponse = postsDeferred.await()
                val posts = postsResponse?.posts ?: emptyList()
                val leaderboard = leaderboardDeferred.await()?.users ?: emptyList()
                val unreadCount = unreadDeferred.await()?.unreadCount ?: 0
                val resolvedDraftCategory = when {
                    categories.none { it.slug == _uiState.value.communityDraftCategory } ->
                        categories.firstOrNull()?.slug ?: _uiState.value.communityDraftCategory

                    else -> _uiState.value.communityDraftCategory
                }

                _uiState.update {
                    it.copy(
                        isLoadingCommunity = false,
                        communityStats = stats,
                        communityCategories = categories,
                        communityTrendingPosts = trending,
                        communityPosts = posts,
                        communityLeaderboard = leaderboard,
                        communityUnreadCount = unreadCount,
                        communityHasMore = postsResponse?.hasMore == true,
                        communityOffset = posts.size,
                        communityDraftCategory = resolvedDraftCategory,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingCommunity = false,
                        errorMessage = "Failed to load community: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun loadMoreCommunityPosts() {
        val state = _uiState.value
        if (state.isLoadingCommunity || state.isLoadingCommunityMore || !state.communityHasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCommunityMore = true) }
            try {
                val response = communityService.getPosts(
                    sort = state.communitySort,
                    category = state.communityCategory,
                    limit = 10,
                    offset = state.communityOffset
                )
                val newPosts = response?.posts ?: emptyList()
                _uiState.update {
                    it.copy(
                        isLoadingCommunityMore = false,
                        communityPosts = it.communityPosts + newPosts,
                        communityOffset = it.communityOffset + newPosts.size,
                        communityHasMore = response?.hasMore == true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingCommunityMore = false,
                        errorMessage = "Failed loading more posts: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun voteCommunityPost(postId: String, vote: Int) {
        if (vote !in listOf(-1, 0, 1)) return
        val current = _uiState.value
        val existing =
            current.communityPosts.firstOrNull { it.id == postId }
                ?: current.communityDetailPost?.takeIf { it.id == postId }
                ?: return
        val oldVote = existing.userVote
        val newVotes = existing.votes - oldVote + vote

        _uiState.update {
            val updatedDetail =
                if (it.communityDetailPost?.id == postId) {
                    it.communityDetailPost.copy(userVote = vote, votes = newVotes)
                } else it.communityDetailPost
            it.copy(
                communityPosts = it.communityPosts.map { post ->
                    if (post.id == postId) post.copy(userVote = vote, votes = newVotes) else post
                },
                communityDetailPost = updatedDetail,
                isVotingCommunityPostIds = it.isVotingCommunityPostIds + postId,
            )
        }

        viewModelScope.launch {
            val result = communityService.votePost(postId, vote)
            if (result.isFailure) {
                _uiState.update {
                    val revertedDetail =
                        if (it.communityDetailPost?.id == postId) {
                            it.communityDetailPost.copy(userVote = oldVote, votes = existing.votes)
                        } else it.communityDetailPost
                    it.copy(
                        communityPosts = it.communityPosts.map { post ->
                            if (post.id == postId) post.copy(userVote = oldVote, votes = existing.votes) else post
                        },
                        communityDetailPost = revertedDetail,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to vote post",
                        isVotingCommunityPostIds = it.isVotingCommunityPostIds - postId,
                    )
                }
                return@launch
            }

            val payload = result.getOrNull()
            _uiState.update {
                val detail = it.communityDetailPost
                val mergedDetailPost = if (detail?.id == postId) {
                    detail.copy(
                        userVote = payload?.userVote ?: detail.userVote,
                        votes = payload?.votes ?: detail.votes,
                    )
                } else {
                    detail
                }
                it.copy(
                    communityPosts = it.communityPosts.map { post ->
                        if (post.id == postId) {
                            post.copy(
                                userVote = payload?.userVote ?: post.userVote,
                                votes = payload?.votes ?: post.votes,
                            )
                        } else {
                            post
                        }
                    },
                    communityDetailPost = mergedDetailPost,
                    isVotingCommunityPostIds = it.isVotingCommunityPostIds - postId,
                )
            }
        }
    }

    fun openCommunityPostDetail(postId: String) {
        val snapshot = _uiState.value.communityPosts.firstOrNull { it.id == postId }
        _uiState.update {
            it.copy(
                communityDetailPostId = postId,
                communityDetailPost = snapshot,
                communityDetailComments = emptyList(),
                communityDetailCommentDraft = "",
                communityDetailCommentSpoiler = false,
                isLoadingCommunityDetail = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val fresh = communityService.getPost(postId)?.post
                val comments = communityService.getPostComments(postId)?.comments ?: emptyList()
                _uiState.update { st ->
                    st.copy(
                        communityDetailPost = fresh ?: st.communityDetailPost,
                        communityDetailComments = comments,
                        isLoadingCommunityDetail = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingCommunityDetail = false,
                        errorMessage = e.message ?: "Failed to load post",
                    )
                }
            }
        }
    }

    fun dismissCommunityPostDetail() {
        _uiState.update {
            it.copy(
                communityDetailPostId = null,
                communityDetailPost = null,
                communityDetailComments = emptyList(),
                communityDetailCommentDraft = "",
                communityDetailCommentSpoiler = false,
                isLoadingCommunityDetail = false,
            )
        }
    }

    fun setCommunityDetailCommentDraft(value: String) {
        _uiState.update { it.copy(communityDetailCommentDraft = value) }
    }

    fun setCommunityDetailCommentSpoiler(value: Boolean) {
        _uiState.update { it.copy(communityDetailCommentSpoiler = value) }
    }

    fun showCommunityFullscreenImage(url: String) {
        if (url.isNotBlank()) _uiState.update { it.copy(communityFullscreenImageUrl = url) }
    }

    fun dismissCommunityFullscreenImage() {
        _uiState.update { it.copy(communityFullscreenImageUrl = null) }
    }

    fun submitCommunityPostComment() {
        val state = _uiState.value
        val postId = state.communityDetailPostId ?: return
        val text = state.communityDetailCommentDraft.trim()
        if (text.isBlank() || state.isPostingCommunityComment) return
        if (state.userProfile == null) {
            _uiState.update { it.copy(errorMessage = "Log in to comment.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPostingCommunityComment = true, errorMessage = null) }
            val result = communityService.createPostComment(
                postId,
                CommunityCommentCreateRequest(
                    content = text,
                    spoiler = state.communityDetailCommentSpoiler,
                ),
            )
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isPostingCommunityComment = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to post comment",
                    )
                }
                return@launch
            }
            val freshPost = communityService.getPost(postId)?.post
            val comments = communityService.getPostComments(postId)?.comments ?: emptyList()
            _uiState.update { st ->
                st.copy(
                    isPostingCommunityComment = false,
                    communityDetailComments = comments,
                    communityDetailPost = freshPost ?: st.communityDetailPost,
                    communityDetailCommentDraft = "",
                    communityDetailCommentSpoiler = false,
                    communityPosts = st.communityPosts.map { post ->
                        if (post.id == postId) {
                            post.copy(comments = freshPost?.comments ?: post.comments + 1)
                        } else {
                            post
                        }
                    },
                    successMessage = "Comment posted.",
                )
            }
        }
    }

    fun createCommunityPost() {
        val state = _uiState.value
        if (state.isCreatingCommunityPost) return
        val title = state.communityDraftTitle.trim()
        val content = state.communityDraftContent.trim()
        val category = state.communityDraftCategory.trim()
        val flair = state.communityDraftFlair.trim().ifBlank { "Discussion" }
        if (title.isBlank() || content.isBlank() || category.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Title, content and category are required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingCommunityPost = true) }
            val result = communityService.createPost(
                CommunityCreatePostRequest(
                    title = title,
                    content = content,
                    category = category,
                    flair = flair,
                    images = emptyList(),
                    spoiler = state.communityDraftSpoiler
                )
            )
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isCreatingCommunityPost = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed creating post"
                    )
                }
                return@launch
            }

            val created = result.getOrNull()?.post
            _uiState.update {
                it.copy(
                    isCreatingCommunityPost = false,
                    successMessage = "Community post created.",
                    communityPosts = if (created != null) listOf(created) + it.communityPosts else it.communityPosts,
                    communityDraftTitle = "",
                    communityDraftContent = "",
                    communityDraftFlair = "Discussion",
                    communityDraftSpoiler = false,
                )
            }
        }
    }

    /** Walks every page of the server watchlist (not just the first 100). */
    private suspend fun loadAllWatchlistEntries(): List<WatchlistEntry> {
        val all = mutableListOf<WatchlistEntry>()
        var offset = 0
        val pageSize = 100
        while (true) {
            val response = watchlistService.getWatchlist(
                limit = pageSize,
                offset = offset,
                sort = "last_updated",
            ) ?: return all
            if (response.results.isEmpty()) break
            all.addAll(response.results)
            if (response.results.size < pageSize) break
            if (response.total > 0 && all.size >= response.total) break
            offset += response.results.size
        }
        return all
    }

    private fun formatLibraryImportNotificationBody(prog: WatchHistorySyncProgress): String =
        buildString {
            prog.detail?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(it)
                append("\n")
            }
            when {
                prog.total > 0 -> append("${prog.current} / ${prog.total}")
                prog.current > 0 -> append("Working…")
                else -> append("Preparing import…")
            }
        }

    fun syncAllToMAL() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isSyncingMal = true, errorMessage = null, successMessage = null) }
            try {
                if (settingsStore.getMalIsExpired()) {
                    if (!trackingService.refreshMalToken()) {
                        _uiState.update {
                            it.copy(
                                isSyncingMal = false,
                                errorMessage = "MAL session expired — reconnect MAL in Tracking.",
                            )
                        }
                        return@launch
                    }
                }
                val results = loadAllWatchlistEntries()
                val eligible = results.filter { (it.anime.malId ?: 0) > 0 }
                val missingIds = results.size - eligible.size
                updateSyncProgressNotification(
                    title = "Syncing to MyAnimeList",
                    statusText = "Pushing episode progress for ${eligible.size} entries.\n0 / ${
                        maxOf(
                            1,
                            eligible.size
                        )
                    }",
                    progressCurrent = 0,
                    progressMax = maxOf(1, eligible.size),
                )
                var success = 0
                eligible.forEachIndexed { index, item ->
                    val malId = item.anime.malId!!
                    val normalizedFolder = item.effectiveFolder?.trim()?.uppercase().orEmpty()
                    val totalEpisodes = item.anime.epCount
                    val progressEpisode = item.anime.episode?.episodeNumber?.takeIf { it > 0 }
                        ?: if (normalizedFolder == "COMPLETED") (totalEpisodes ?: 1) else 1
                    val step = index + 1
                    updateSyncProgressNotification(
                        title = "Syncing to MyAnimeList",
                        statusText = "${item.anime.displayTitle}\n$step / ${eligible.size}",
                        progressCurrent = step,
                        progressMax = eligible.size,
                    )
                    if (trackingService.syncMalProgress(malId, progressEpisode, totalEpisodes)) {
                        success += 1
                    }
                    if (step % 10 == 0) delay(50L)
                }
                val attempted = eligible.size
                val failed = attempted - success
                _uiState.update {
                    it.copy(
                        isSyncingMal = false,
                        errorMessage = when {
                            attempted == 0 -> "No eligible MAL item to sync (missing MAL IDs on watchlist entries: $missingIds)."
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
            } finally {
                clearSyncProgressNotification()
            }
        }
    }

    fun syncAllToAniList() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isSyncingAnilist = true, errorMessage = null, successMessage = null) }
            try {
                val results = loadAllWatchlistEntries()
                val eligible = results.filter { (it.anime.anilistId ?: 0) > 0 }
                val missingIds = results.size - eligible.size
                updateSyncProgressNotification(
                    title = "Syncing to AniList",
                    statusText = "Pushing episode progress for ${eligible.size} entries.\n0 / ${
                        maxOf(
                            1,
                            eligible.size
                        )
                    }",
                    progressCurrent = 0,
                    progressMax = maxOf(1, eligible.size),
                )
                var success = 0
                eligible.forEachIndexed { index, item ->
                    val anilistId = item.anime.anilistId!!
                    val normalizedFolder = item.effectiveFolder?.trim()?.uppercase().orEmpty()
                    val totalEpisodes = item.anime.epCount
                    val progressEpisode = item.anime.episode?.episodeNumber?.takeIf { it > 0 }
                        ?: if (normalizedFolder == "COMPLETED") (totalEpisodes ?: 1) else 1
                    val step = index + 1
                    updateSyncProgressNotification(
                        title = "Syncing to AniList",
                        statusText = "${item.anime.displayTitle}\n$step / ${eligible.size}",
                        progressCurrent = step,
                        progressMax = eligible.size,
                    )
                    if (trackingService.syncAnilistProgress(anilistId, progressEpisode, totalEpisodes)) {
                        success += 1
                    }
                    delay(120L)
                }
                val attempted = eligible.size
                val failed = attempted - success
                _uiState.update {
                    it.copy(
                        isSyncingAnilist = false,
                        errorMessage = when {
                            attempted == 0 -> "No eligible AniList item to sync (missing AniList IDs on watchlist entries: $missingIds)."
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
            } finally {
                clearSyncProgressNotification()
            }
        }
    }

    fun startWatchHistorySync() {
        if (_uiState.value.isWatchHistorySyncing) return
        val snap = _uiState.value
        if (snap.userProfile == null) {
            _uiState.update { it.copy(errorMessage = "Sign in to sync watch history from your account.") }
            return
        }
        if (!snap.malConnected && !snap.anilistConnected) {
            _uiState.update {
                it.copy(errorMessage = "Connect MAL or AniList to sync your library.")
            }
            return
        }
        if (snap.isOffline) {
            _uiState.update { it.copy(errorMessage = "You are offline.") }
            return
        }
        val malOn = snap.malConnected
        val alOn = snap.anilistConnected
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _uiState.update {
                    it.copy(
                        isWatchHistorySyncing = true,
                        watchHistoryCurrent = 0,
                        watchHistoryTotal = 0,
                        watchHistoryDetail = null,
                        watchHistoryMalDone = false,
                        watchHistoryAnilistDone = false,
                        errorMessage = null,
                        successMessage = null,
                    )
                }
                updateSyncProgressNotification(
                    title = "Sync library",
                    statusText = "Preparing…\nLarge libraries can take several minutes.",
                    progressCurrent = 0,
                    progressMax = 0,
                )
                val result = watchHistorySyncService.sync { prog ->
                    _uiState.update { st ->
                        st.copy(
                            watchHistoryCurrent = prog.current,
                            watchHistoryTotal = prog.total,
                            watchHistoryMalDone = prog.malServiceDone,
                            watchHistoryAnilistDone = prog.anilistServiceDone,
                            watchHistoryDetail = prog.detail,
                        )
                    }
                    updateSyncProgressNotification(
                        title = "Sync library",
                        statusText = formatLibraryImportNotificationBody(prog),
                        progressCurrent = prog.current,
                        progressMax = prog.total,
                    )
                }
                result.fold(
                    onSuccess = { outcome ->
                        val parts = buildList {
                            if (malOn && outcome.malSynced > 0) add("${outcome.malSynced} entries synced to MAL")
                            if (alOn && outcome.anilistSynced > 0) add("${outcome.anilistSynced} entries synced to AniList")
                        }
                        val message = when {
                            parts.isNotEmpty() -> "Sync complete — ${parts.joinToString(", ")}"
                            outcome.totalEntries == 0 -> "Sync complete — no entries in export."
                            else -> buildString {
                                append("Sync complete — ")
                                append(
                                    buildList {
                                        if (malOn) add("MAL: ${outcome.malSynced} ok, ${outcome.malFailed} failed")
                                        if (alOn) add("AniList: ${outcome.anilistSynced} ok, ${outcome.anilistFailed} failed")
                                    }.joinToString("; ")
                                )
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isWatchHistorySyncing = false,
                                successMessage = message,
                                watchHistoryMalDone = malOn,
                                watchHistoryAnilistDone = alOn,
                                watchHistoryDetail = null,
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isWatchHistorySyncing = false,
                                errorMessage = e.message ?: "Watch history sync failed",
                                watchHistoryMalDone = false,
                                watchHistoryAnilistDone = false,
                                watchHistoryDetail = null,
                            )
                        }
                    },
                )
            } finally {
                clearSyncProgressNotification()
            }
        }
    }

    fun connectReanime(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingReanime = true, errorMessage = null, successMessage = null) }
            val result = bffMeService.connectReanime(email, password)
            result.fold(
                onSuccess = { updatedProfile ->
                    _uiState.update {
                        it.copy(
                            isConnectingReanime = false,
                            userProfile = updatedProfile,
                            successMessage = "ReAnime account connected successfully."
                        )
                    }
                    viewModelScope.launch {
                        try {
                            _uiState.update { it.copy(isLoading = true) }
                            val syncResult = AppComponent.librarySyncService.syncWithReanime(force = true)
                            if (syncResult) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        successMessage = "ReAnime account connected and library synchronized."
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = "Connected to ReAnime, but library sync failed."
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Connected to ReAnime, but library sync failed: ${e.message}"
                                )
                            }
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isConnectingReanime = false,
                            errorMessage = e.message ?: "Failed to connect ReAnime account"
                        )
                    }
                }
            )
        }
    }

    fun disconnectReanime() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDisconnectingReanime = true, errorMessage = null, successMessage = null) }
            val result = bffMeService.disconnectReanime()
            result.fold(
                onSuccess = { updatedProfile ->
                    _uiState.update {
                        it.copy(
                            isDisconnectingReanime = false,
                            userProfile = updatedProfile,
                            successMessage = "ReAnime account disconnected successfully."
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isDisconnectingReanime = false,
                            errorMessage = e.message ?: "Failed to disconnect ReAnime account"
                        )
                    }
                }
            )
        }
    }

    fun syncLibraryWithReanime() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            try {
                val syncResult = AppComponent.librarySyncService.syncWithReanime(
                    force = true,
                    direction = LibrarySyncDirection.Merge,
                )
                if (syncResult) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "2-way library sync completed successfully."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Library sync failed."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Library sync failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun importLibraryFromReanime() {
        syncLibraryWithReanimeDirection(
            direction = LibrarySyncDirection.Import,
            success = "Imported ReAnime library into Anisurge.",
            failure = "ReAnime import failed.",
        )
    }

    fun exportLibraryToReanime() {
        syncLibraryWithReanimeDirection(
            direction = LibrarySyncDirection.Export,
            success = "Exported Anisurge library to ReAnime.",
            failure = "ReAnime export failed.",
        )
    }

    private fun syncLibraryWithReanimeDirection(
        direction: LibrarySyncDirection,
        success: String,
        failure: String,
    ) {
        viewModelScope.launch {
            _uiState.update {
                when (direction) {
                    LibrarySyncDirection.Import -> it.copy(
                        isImportingReanime = true,
                        errorMessage = null,
                        successMessage = null,
                    )
                    LibrarySyncDirection.Export -> it.copy(
                        isExportingReanime = true,
                        errorMessage = null,
                        successMessage = null,
                    )
                    LibrarySyncDirection.Merge -> it.copy(isLoading = true, errorMessage = null, successMessage = null)
                }
            }
            try {
                val ok = AppComponent.librarySyncService.syncWithReanime(force = true, direction = direction)
                _uiState.update {
                    val cleared = when (direction) {
                        LibrarySyncDirection.Import -> it.copy(isImportingReanime = false)
                        LibrarySyncDirection.Export -> it.copy(isExportingReanime = false)
                        LibrarySyncDirection.Merge -> it.copy(isLoading = false)
                    }
                    if (ok) {
                        cleared.copy(successMessage = success)
                    } else {
                        cleared.copy(errorMessage = failure)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    val cleared = when (direction) {
                        LibrarySyncDirection.Import -> it.copy(isImportingReanime = false)
                        LibrarySyncDirection.Export -> it.copy(isExportingReanime = false)
                        LibrarySyncDirection.Merge -> it.copy(isLoading = false)
                    }
                    cleared.copy(errorMessage = "$failure ${e.message ?: "Unknown error"}")
                }
            }
        }
    }
}
