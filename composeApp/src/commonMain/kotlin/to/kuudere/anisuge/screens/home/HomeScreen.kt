package to.kuudere.anisuge.screens.home

import to.kuudere.anisuge.ui.OfflineState

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import anisurge.composeapp.generated.resources.Res
import anisurge.composeapp.generated.resources.logo_txt
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.Bookmark
import to.kuudere.anisuge.ui.WatchlistBottomSheet
import to.kuudere.anisuge.AppComponent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import to.kuudere.anisuge.screens.settings.SettingsTab
import to.kuudere.anisuge.screens.settings.SettingsScreen
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import to.kuudere.anisuge.utils.Uri
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.max
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import kotlin.math.absoluteValue
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.ContinueWatchingItem
import to.kuudere.anisuge.utils.DownloadManager
import to.kuudere.anisuge.utils.DownloadTask
import to.kuudere.anisuge.platform.DraggableWindowArea
import to.kuudere.anisuge.platform.WindowManagementButtons
import to.kuudere.anisuge.screens.search.SearchScreen
import to.kuudere.anisuge.screens.search.SearchViewModel
import to.kuudere.anisuge.screens.watchlist.WatchlistScreen
import to.kuudere.anisuge.screens.watchlist.WatchlistViewModel
import to.kuudere.anisuge.screens.schedule.ScheduleScreen
import to.kuudere.anisuge.screens.schedule.ScheduleViewModel
import to.kuudere.anisuge.screens.settings.SettingsScreen
import to.kuudere.anisuge.screens.settings.SettingsViewModel
import to.kuudere.anisuge.ui.ConfirmDialog
import to.kuudere.anisuge.platform.isDesktopPlatform
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput

enum class AnisugTab { Home, Search, Calendar, Bookmarks, Downloads, Settings }

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    watchlistViewModel: WatchlistViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    onAnimeClick: (String) -> Unit,
    onWatchClick: (String, String, Int, String?) -> Unit,
    onWatchOffline: (String, Int, String, String) -> Unit = { _, _, _, _ -> },
    onLogout: () -> Unit = {},
    onExit: () -> Unit = {},
    onViewLatestMore: () -> Unit = {},
    onViewContinueWatchingMore: () -> Unit = {},
    startOnDownloads: Boolean = false,
    startTab: String? = null,
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    var initialSettingsTab by remember { mutableStateOf<SettingsTab?>(null) }
    var currentTab by remember(startOnDownloads, startTab) { 
        val initial = if (startOnDownloads) AnisugTab.Downloads else {
            AnisugTab.entries.firstOrNull { it.name.equals(startTab, ignoreCase = true) } ?: AnisugTab.Home
        }
        mutableStateOf(initial) 
    }
    var prevTabIndex by remember { mutableStateOf(0) }
    var showWatchlistFor by remember { mutableStateOf<AnimeItem?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }
    
    // Function to switch tabs with history tracking
    val switchTab: (AnisugTab, SettingsTab?) -> Unit = { newTab, initialNested ->
        prevTabIndex = AnisugTab.entries.indexOf(currentTab)
        initialSettingsTab = initialNested
        currentTab = newTab
    }

    LaunchedEffect(Unit) {
        // Loging states are now reactive — ViewModels handle their own initial check
        // but we can still trigger a global sync here if needed.
    }

    // Join room based on current tab
    LaunchedEffect(currentTab) {
        val room = when (currentTab) {
            AnisugTab.Home -> "home"
            AnisugTab.Search -> "search"
            AnisugTab.Calendar -> "countdowns"
            AnisugTab.Bookmarks -> "watchlist"
            AnisugTab.Downloads -> "downloads"
            AnisugTab.Settings -> "settings"
        }
        AppComponent.realtimeService.joinRoom(room)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF000000))) {
        val isDesktop = maxWidth >= 800.dp

        Row(Modifier.fillMaxSize()) {
            if (isDesktop) {
                AnisugSidebar(
                    avatarUrl = homeState.userProfile?.avatar,
                    selectedTab = currentTab,
                    isLoggingOut = homeState.isLoggingOut,
                    onTabSelect = switchTab,
                    onLogout = {
                        showLogoutConfirm = true
                    },
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.05f)))
            }

            if (isDesktop) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                val toIndex = AnisugTab.entries.indexOf(targetState)
                                val fromIndex = AnisugTab.entries.indexOf(initialState)
                                val goingDown = toIndex > fromIndex
                                val slideOffset = { size: Int -> if (goingDown) size / 6 else -(size / 6) }
                                val slideOutOffset = { size: Int -> if (goingDown) -(size / 6) else size / 6 }
                                (slideInVertically(tween(300)) { slideOffset(it) } + fadeIn(tween(300)))
                                    .togetherWith(slideOutVertically(tween(300)) { slideOutOffset(it) } + fadeOut(tween(200)))
                            }
                        ) { tab ->
                            TabContent(
                                tab = tab,
                                homeState = homeState,
                                homeViewModel = homeViewModel,
                                searchViewModel = searchViewModel,
                                watchlistViewModel = watchlistViewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                onAnimeClick = onAnimeClick,
                                onWatchClick = onWatchClick,
                                onWatchOffline = onWatchOffline,
                                onLogout = { showLogoutConfirm = true },
                                onViewLatestMore = onViewLatestMore,
                                onViewContinueWatchingMore = onViewContinueWatchingMore,
                                onWatchlistClick = { showWatchlistFor = it },
                                onSearchLatest = {
                                    searchViewModel.onSortChange("Latest")
                                    searchViewModel.search()
                                    prevTabIndex = AnisugTab.entries.indexOf(currentTab)
                                    currentTab = AnisugTab.Search
                                },
                                onExit = onExit,
                                initialSettingsTab = initialSettingsTab
                            )
                        }
                    }
                }
            } else {
                // Mobile: Glass layout with overlapping bars
                val density = LocalDensity.current
                var topBarHeightPx by remember { mutableStateOf(with(density) { 76.dp.roundToPx() }) }
                var bottomBarHeightPx by remember { mutableStateOf(with(density) { 80.dp.roundToPx() }) }
                val topBarHeight = with(density) { topBarHeightPx.toDp() }
                val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
                val useFloatingBottomBar = settingsState.floatingBottomNav

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    // Content — padded so both bars never overlap it
                    Box(
                        Modifier
                            .fillMaxSize()
                            .haze(state = hazeState)
                            .padding(
                                top = topBarHeight,
                                bottom = if (useFloatingBottomBar) 0.dp else bottomBarHeight
                            )
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                val toIndex = AnisugTab.entries.indexOf(targetState)
                                val fromIndex = AnisugTab.entries.indexOf(initialState)
                                val goingRight = toIndex > fromIndex
                                val enter = slideInHorizontally(tween(300)) { if (goingRight) it else -it } + fadeIn(tween(300))
                                val exit  = slideOutHorizontally(tween(300)) { if (goingRight) -it else it } + fadeOut(tween(200))
                                enter.togetherWith(exit)
                            }
                        ) { tab ->
                            TabContent(
                                tab = tab,
                                homeState = homeState,
                                homeViewModel = homeViewModel,
                                searchViewModel = searchViewModel,
                                watchlistViewModel = watchlistViewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                onAnimeClick = onAnimeClick,
                                onWatchClick = onWatchClick,
                                onWatchOffline = onWatchOffline,
                                onLogout = { showLogoutConfirm = true },
                                onViewLatestMore = onViewLatestMore,
                                onViewContinueWatchingMore = onViewContinueWatchingMore,
                                onWatchlistClick = { showWatchlistFor = it },
                                onSearchLatest = {
                                    searchViewModel.onSortChange("Latest")
                                    searchViewModel.search()
                                    prevTabIndex = AnisugTab.entries.indexOf(currentTab)
                                    currentTab = AnisugTab.Search
                                },
                                onExit = onExit,
                                initialSettingsTab = initialSettingsTab
                            )
                        }
                    }

                    // Top Bar — measured so content knows how tall it is
                    MobileTopBar(
                        avatarUrl = homeState.userProfile?.avatar,
                        onDownloadClick = {
                            prevTabIndex = AnisugTab.entries.indexOf(currentTab)
                            currentTab = AnisugTab.Downloads
                        },
                        hazeState = hazeState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .onGloballyPositioned { topBarHeightPx = it.size.height }
                    )

                    // Bottom Bar — measured so content knows how tall it is
                    if (useFloatingBottomBar) {
                        AnisugBottomBar(
                            selectedTab = currentTab,
                            onTabSelect = { newTab -> switchTab(newTab, null) },
                            hazeState = hazeState,
                            liquidGlass = settingsState.liquidGlassBottomNav,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .onGloballyPositioned { bottomBarHeightPx = it.size.height }
                        )
                    } else {
                        NormalAnisugBottomBar(
                            selectedTab = currentTab,
                            onTabSelect = { newTab -> switchTab(newTab, null) },
                            hazeState = hazeState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .onGloballyPositioned { bottomBarHeightPx = it.size.height }
                        )
                    }
                }
            }
        }

        // ── Watchlist Bottom Sheet ──────────────────────────────────────────
        if (showWatchlistFor != null) {
            WatchlistBottomSheet(
                currentFolder = null, // We don't have this info here easily
                onSelect = { folder ->
                    homeViewModel.updateWatchlist(showWatchlistFor!!.id, folder)
                    showWatchlistFor = null
                },
                onDismiss = { showWatchlistFor = null }
            )
        }

        if (showLogoutConfirm) {
            ConfirmDialog(
                title = "Logout",
                message = "Are you sure you want to logout of your account? This will end your current session.",
                confirmLabel = "Logout",
                onConfirm = {
                    showLogoutConfirm = false
                    homeViewModel.logout(onComplete = onLogout)
                },
                onDismiss = { showLogoutConfirm = false }
            )
        }
    }
}

@Composable
private fun TabContent(
    tab: AnisugTab,
    homeState: HomeUiState,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    watchlistViewModel: WatchlistViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    onAnimeClick: (String) -> Unit,
    onWatchClick: (String, String, Int, String?) -> Unit,
    onWatchOffline: (String, Int, String, String) -> Unit,
    onLogout: () -> Unit,
    onViewLatestMore: () -> Unit,
    onViewContinueWatchingMore: () -> Unit,
    onWatchlistClick: (AnimeItem) -> Unit,
    onSearchLatest: () -> Unit,
    onExit: () -> Unit,
    initialSettingsTab: SettingsTab?,
) {
    Box(Modifier.fillMaxSize()) {
        to.kuudere.anisuge.platform.DraggableWindowArea(
            modifier = Modifier.fillMaxWidth().height(84.dp).align(Alignment.TopStart)
        ) { }

        when (tab) {
            AnisugTab.Home -> {
                AnimatedContent(
                    targetState = homeState.isLoading && homeState.topAiring.isEmpty(),
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                    label = "HomeLoadingTransition"
                ) { isLoading ->
                    when {
                        isLoading ->
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            }
                        homeState.isOffline && homeState.topAiring.isEmpty() ->
                            to.kuudere.anisuge.ui.OfflineState(onRetry = { homeViewModel.refresh(force = true) }, isLoading = homeState.isLoading)
                        homeState.error != null && homeState.topAiring.isEmpty() ->
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(homeState.error!!, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                    Button(
                                        onClick = { homeViewModel.refresh(force = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBF80FF))
                                    ) { Text("Retry") }
                                }
                            }
                        else -> {
                            HomeContent(
                                homeState,
                                onAnimeClick,
                                onWatchClick,
                                onWatchlistClick = onWatchlistClick,
                                onRefresh = { homeViewModel.refresh(force = true) },
                                onViewLatestMore = onViewLatestMore,
                                onViewContinueWatchingMore = onViewContinueWatchingMore,
                                onViewNewOnAppMore = onSearchLatest,
                                onExit = onExit
                            )
                        }
                    }
                }
            }
            AnisugTab.Search -> SearchScreen(searchViewModel, onAnimeClick, onExit = onExit)
            AnisugTab.Bookmarks -> WatchlistScreen(watchlistViewModel, onAnimeClick)
            AnisugTab.Calendar -> ScheduleScreen(scheduleViewModel, onAnimeClick, onExit = onExit)
            AnisugTab.Downloads -> DownloadsTab(onWatchOffline, onExit = onExit)
            AnisugTab.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                onLogout = onLogout,
                 onRefresh = { homeViewModel.refresh(force = true) },
                isLoggingOut = homeState.isLoggingOut,
                initialTab = initialSettingsTab,
                onExit = onExit
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onAnimeClick: (String) -> Unit,
    onWatchClick: (String, String, Int, String?) -> Unit,
    onWatchlistClick: (AnimeItem) -> Unit,
    onRefresh: () -> Unit = {},
    onViewLatestMore: () -> Unit = {},
    onViewContinueWatchingMore: () -> Unit = {},
    onViewNewOnAppMore: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    
    val innerContent: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
        Box(Modifier.fillMaxWidth()) {
            // ── Hero Carousel ──────────────────────────────────────────────
            if (state.topAiring.isNotEmpty()) {
                HeroCarousel(
                    items = state.topAiring,
                    onAnimeClick = { onAnimeClick(it.id) },
                    onWatchClick = { item, lang, ep -> onWatchClick(item.id, lang, ep, null) },
                    onWatchlistClick = onWatchlistClick
                )
            }

            to.kuudere.anisuge.platform.WindowManagementButtons(
                onClose = onExit,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Continue Watching ──────────────────────────────────────────
        if (state.continueWatching.isNotEmpty()) {
            SectionHeader(title = "Continue Watching", onViewMore = onViewContinueWatchingMore)
            ContinueWatchingRow(
                items = state.continueWatching,
                onWatchClick = { id, lang, ep, server -> onWatchClick(id, lang, ep, server) },
            )
            Spacer(Modifier.height(24.dp))
        }

        // ── Latest Episodes ────────────────────────────────────────────
        if (state.latestEpisodes.isNotEmpty()) {
            AnimeSection(
                title = "Latest Episodes",
                items = state.latestEpisodes,
                onItemClick = { item -> onAnimeClick(item.id) },
                onViewMoreClick = onViewLatestMore,
            )
        }

        // ── New On Site ────────────────────────────────────────────────
        if (state.newOnSite.isNotEmpty()) {
            AnimeSection(
                title = "New On App",
                items = state.newOnSite,
                onItemClick = { item -> onAnimeClick(item.id) },
                onViewMoreClick = onViewNewOnAppMore,
            )
        }

        // ── Top Upcoming ───────────────────────────────────────────────
        if (state.topUpcoming.isNotEmpty()) {
            AnimeSection(
                title = "Top Upcoming",
                items = state.topUpcoming,
                onItemClick = { item -> onAnimeClick(item.id) },
                showViewMore = false,
            )
        }

        Spacer(Modifier.height(if (isDesktopPlatform) 48.dp else 156.dp))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isDesktop = maxWidth >= 1024.dp
        if (isDesktop) {
            Box(Modifier.fillMaxSize()) {
                innerContent()
            }
        } else {
            val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = state.isLoading,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    )
                }
            ) {
                innerContent()
            }
        }
    }
}

// ── Hero Carousel ──────────────────────────────────────────────────────────

@Composable
private fun HeroCarousel(
    items: List<AnimeItem>,
    onAnimeClick: (AnimeItem) -> Unit,
    onWatchClick: (AnimeItem, String, Int) -> Unit,
    onWatchlistClick: (AnimeItem) -> Unit,
) {
    var currentIndex by remember { mutableStateOf(0) }
    val item = items[currentIndex]

    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val screenHeightDp = with(density) { windowSize.height.toDp() }
    val carouselHeight = screenHeightDp * 0.62f

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val isDesktop = maxWidth >= 1024.dp
        val isLargeDevice = maxWidth >= 1300.dp
        
        val posterWidth = if (isLargeDevice) 240.dp else 160.dp
        val bannerWidth = if (isLargeDevice) 360.dp else 240.dp
        val titleSize = if (isLargeDevice) 44.sp else 32.sp
        val titleLineHeight = if (isLargeDevice) 52.sp else 40.sp
        val paddingAmount = if (isLargeDevice) 64.dp else 40.dp
        val rowSpacing = if (isLargeDevice) 40.dp else 24.dp
        val descLines = if (isLargeDevice) 4 else 3

        if (!isDesktop) {
            // ── Small screen: Fan stack carousel ────────────────────────
            FanCarousel(
                items = items,
                currentIndex = currentIndex,
                onIndexChange = { currentIndex = it },
                onWatchClick = onWatchClick,
                onAnimeClick = onAnimeClick,
                onWatchlistClick = onWatchlistClick,
            )
        } else {
            // ── Desktop: full-bleed hero ─────────────────────────────────
            val bgColor = Color(0xFF000000)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(carouselHeight)
                    .background(bgColor)
            ) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400), initialOffsetX = { 200 })) togetherWith
                        fadeOut(animationSpec = tween(400))
                    },
                    modifier = Modifier.fillMaxSize()
                ) { targetIndex ->
                    val animItem = items[targetIndex]
                    val imageUrl = animItem.bannerUrl ?: animItem.imageUrl
                    Box(Modifier.fillMaxSize()) {
                        
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = animItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        
                        // Slide continues...



                Box(
                    Modifier.fillMaxSize().drawWithCache {
                        val verticalGradient = Brush.verticalGradient(
                            0.3f to Color.Transparent,
                            0.7f to bgColor.copy(alpha = 0.5f),
                            0.95f to bgColor.copy(alpha = 0.98f),
                            1.0f to bgColor
                        )
                        val horizontalGradient = Brush.horizontalGradient(
                            0.0f to bgColor.copy(alpha = 0.9f),
                            0.5f to bgColor.copy(alpha = 0.4f),
                            1.0f to bgColor.copy(alpha = 0.1f)
                        )
                        onDrawBehind {
                            drawRect(brush = horizontalGradient, size = size)
                            drawRect(brush = verticalGradient, size = size)
                        }
                    }
                )

                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = paddingAmount, bottom = paddingAmount, end = paddingAmount),
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Vertical Poster
                    AsyncImage(
                        model = if (animItem.imageUrl.startsWith("http")) animItem.imageUrl else "https://anime.anisurge.qzz.io/img/poster/${animItem.imageUrl}",
                        contentDescription = animItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(posterWidth)
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    // Middle: Text Info
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = animItem.title,
                            color = Color.White,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = titleLineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(Modifier.height(16.dp))

                        // Meta Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val genresText = animItem.genres?.take(4)?.joinToString(" • ")
                            if (!genresText.isNullOrEmpty()) {
                                Text(genresText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            
                            if (animItem.malScore != null && animItem.malScore > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Score", tint = Color.White.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(animItem.malScore.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = stripHtmlTags(animItem.description ?: "").replace("\n", " ").replace("  ", " ").trim(),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            maxLines = descLines,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(32.dp))

                        // Preview / Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onAnimeClick(animItem) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Info, contentDescription = "Info", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { onWatchlistClick(animItem) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Watchlist", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    // Right: Episode Landscape Thumbnail
                    Box(
                        modifier = Modifier
                            .width(bannerWidth)
                            .aspectRatio(16f/9f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { onWatchClick(animItem, "sub", 1) }
                    ) {
                        AsyncImage(
                            model = animItem.bannerUrl ?: (if (animItem.imageUrl.startsWith("http")) animItem.imageUrl else "https://anime.anisurge.qzz.io/img/poster/${animItem.imageUrl}"),
                            contentDescription = "Episode",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Gradient Overlay for Text Visibility and Darker overall tint
                        Box(Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.95f)),
                                startY = 0f
                            )
                        ))
                        
                        // Centered Play Button
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow, 
                                contentDescription = "Play", 
                                tint = Color.White, 
                                modifier = Modifier.size(32.dp).offset(x = 2.dp) // Offset slightly so triangle appears centered
                            )
                        }
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(20.dp)
                        ) {

                            Text(
                                "Episode 1" + if (animItem.epCount != null && animItem.epCount > 0) " / ${animItem.epCount}" else "",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                        
                        // Time badge bottom right
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                        ) {
                        Text("24m", color = Color.White.copy(alpha=0.7f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = paddingAmount, bottom = paddingAmount),
                ) {
                    CarouselNavBtn(Icons.AutoMirrored.Filled.ArrowBack) {
                        currentIndex = (currentIndex - 1 + items.size) % items.size
                    }
                    Spacer(Modifier.width(12.dp))
                    CarouselNavBtn(Icons.AutoMirrored.Filled.ArrowForward) {
                        currentIndex = (currentIndex + 1) % items.size
                    }
                }

                // Dot Indicators
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = paddingAmount + 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, _ ->
                        val isSelected = index == currentIndex
                        val width = animateDpAsState(if (isSelected) 24.dp else 8.dp).value
                        val color = animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.3f)).value
                        Box(
                            Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { currentIndex = index }
                        )
                    }
                }
            }
        }
    }
}

// ── Small screens carousel (OnStream style) ───────────────────────────────

private const val FAKE_PAGE_COUNT = 10_000  // large enough for "infinite" circular scroll

@Composable
private fun FanCarousel(
    items: List<AnimeItem>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onWatchClick: (AnimeItem, String, Int) -> Unit,
    onAnimeClick: (AnimeItem) -> Unit,
    onWatchlistClick: (AnimeItem) -> Unit,
) {
    if (items.isEmpty()) return

    // Fake-infinite pager: start in the middle so user can scroll both directions
    val startPage = (FAKE_PAGE_COUNT / 2) - ((FAKE_PAGE_COUNT / 2) % items.size) + currentIndex
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { FAKE_PAGE_COUNT }
    )

    // Map fake page index → real item index
    fun realIndex(fakePage: Int): Int = ((fakePage % items.size) + items.size) % items.size

    // Sync pager changes back to parent
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        val real = realIndex(pagerState.currentPage)
        if (real != currentIndex) {
            onIndexChange(real)
        }
    }

    // Auto-scroll every 4 seconds, pauses during user interaction
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            if (!pagerState.isScrollInProgress && items.isNotEmpty()) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val windowSize = LocalWindowInfo.current.containerSize
        val density = LocalDensity.current
        val screenWidthDp = with(density) { windowSize.width.toDp() }

        // Card sizing — poster at ~45% of screen width so sides are visible
        val cardWidth = screenWidthDp * 0.45f
        val cardHeight = cardWidth * 1.5f
        val hPadding = max(0.dp, (screenWidthDp - cardWidth) / 2)

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = hPadding),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(cardHeight)
        ) { fakePage ->
            val pageOffset = (
                (pagerState.currentPage - fakePage) + pagerState.currentPageOffsetFraction
            ).absoluteValue

            val scale = 1f - (pageOffset.coerceIn(0f, 1f) * 0.15f)
            val alpha = 1f - (pageOffset.coerceIn(0f, 1f) * 0.4f)

            val item = items[realIndex(fakePage)]

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (fakePage == pagerState.currentPage) onAnimeClick(item)
                    }
            ) {
                AsyncImage(
                    model = if (item.imageUrl.startsWith("http")) item.imageUrl
                            else "https://anime.anisurge.qzz.io/img/poster/${item.imageUrl}",
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        val activeItem = items[realIndex(pagerState.currentPage)]

        // Title
        Text(
            text = activeItem.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(6.dp))

        // Meta (e.g., TV • Action, Drama, Supernatural)
        val type = activeItem.type ?: "TV"
        val genresStr = activeItem.genres?.joinToString(", ") ?: ""
        val metaText = if (genresStr.isNotEmpty()) "$type  •  $genresStr" else type

        Text(
            text = metaText,
            color = Color.Gray,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(20.dp))

        // Actions Row — fixed-width side columns for alignment
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Detail"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clickable { onAnimeClick(activeItem) }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Detail",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("Detail", color = Color.White, fontSize = 11.sp)
            }

            // "WATCH NOW" button — fills the center
            Button(
                onClick = { onWatchClick(activeItem, "sub", 1) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(46.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "WATCH NOW",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // "Add List"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clickable { onWatchlistClick(activeItem) }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    contentDescription = "Add List",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("Add List", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Continue Watching Row ──────────────────────────────────────────────────

@Composable
private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onWatchClick: (String, String, Int, String?) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LazyRow(
        state = listState,
        contentPadding    = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                coroutineScope.launch {
                    listState.scrollBy(-delta)
                }
            }
        )
    ) {
        itemsIndexed(items) { _, item ->
            val inter = remember { MutableInteractionSource() }
            val hovered by inter.collectIsHoveredAsState()
            // Parse anime id from link e.g. "/watch/{id}?ep=1&lang=sub"
            val animeId = item.link.removePrefix("/").split("/").getOrNull(1) ?: ""
            val lang    = Uri.parseQueryParam(item.link, "lang") ?: "sub"
            val server  = Uri.parseQueryParam(item.link, "server")

            Column(
                Modifier
                    .width(260.dp)
                    .hoverable(inter)
                    .clickable { onWatchClick(animeId, lang, item.episode, server) }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model             = item.thumbnail,
                        contentDescription = item.title,
                        contentScale      = ContentScale.Crop,
                        modifier          = Modifier.fillMaxSize(),
                    )
                    // Progress bar
                    val progress = parseProgressFraction(item.progress, item.duration)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxSize()
                                .background(Color(0xFFE50914))
                        )
                    }
                    // Hover play and delete overlay
                    if (hovered) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Box(
                                Modifier.align(Alignment.Center)
                                    .background(Color.White, CircleShape)
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow, null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // Delete button placeholder
                            Box(
                                Modifier.align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                    .clickable { /* Handle delete later */ }
                                    .padding(6.dp)
                            ) {
                                Text("X", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    // Bottom Left Episode badge
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 8.dp, start = 8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("EP ${item.episode}", color = Color.White, fontSize = 12.sp)
                    }

                    // Bottom Right Time badge
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 8.dp, end = 8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("${item.progress}/${item.duration}", color = Color.White, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    item.title, 
                    color = Color.White, 
                    fontSize = 14.sp, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Generic Anime Section ──────────────────────────────────────────────────

@Composable
private fun AnimeSection(
    title: String,
    items: List<AnimeItem>,
    onItemClick: (AnimeItem) -> Unit,
    showViewMore: Boolean = true,
    onViewMoreClick: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val isXlScreen = maxWidth >= 1280.dp
        Column {
            SectionHeader(title = title, onViewMore = if (showViewMore) onViewMoreClick else null)
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            LazyRow(
                state = listState,
                contentPadding        = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            listState.scrollBy(-delta)
                        }
                    }
                )
            ) {
                itemsIndexed(items) { _, item ->
                    to.kuudere.anisuge.ui.AnimeCard(
                        item    = item,
                        modifier = Modifier.width(if (isXlScreen) 200.dp else 155.dp),
                        onClick = { onItemClick(item) }
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}


// ── Small helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, onViewMore: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (onViewMore != null) {
            TextButton(onClick = onViewMore) {
                Text("View more >", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SpotlightTag(index: Int) {
    Row(
        Modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(30.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(Color(0xFFBF80FF), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text("#${index + 1} Spotlight", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetaTag(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MalScoreBadge(score: Double) {
    Box(
        Modifier
            .background(Color(0xFF00C853), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("MAL $score", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CarouselNavBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// ── HTML Stripper ──────────────────────────────────────────────────────────

private fun stripHtmlTags(htmlContent: String): String {
    val br    = htmlContent.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    val p     = br.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    val div   = p.replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
    return div.replace(Regex("<[^>]*>"), "").trim()
}

@Composable
private fun SmallBadge(text: String, color: Color = Color.White) {
    Box(
        Modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Sidebar ────────────────────────────────────────────────────────────────

@Composable
private fun AnisugSidebar(
    avatarUrl: String?,
    selectedTab: AnisugTab,
    isLoggingOut: Boolean,
    onTabSelect: (AnisugTab, SettingsTab?) -> Unit,
    onLogout: () -> Unit,
) {
    val fullAvatarUrl = when {
        avatarUrl == null -> null
        avatarUrl.startsWith("http") -> avatarUrl
        else -> "https://anime.anisurge.qzz.io$avatarUrl"
    }

    DraggableWindowArea(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF000000)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(32.dp))
                // User Avatar / Logo
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onTabSelect(AnisugTab.Settings, SettingsTab.Profile) },
                    contentAlignment = Alignment.Center
                ) {
                    if (fullAvatarUrl != null) {
                        AsyncImage(
                            model = fullAvatarUrl,
                            contentDescription = "User Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(48.dp))
                
                // Icons
                SidebarIcon(
                    Icons.Outlined.CalendarToday, 
                    isSelected = selectedTab == AnisugTab.Calendar,
                    onClick = { onTabSelect(AnisugTab.Calendar, null) }
                )
                SidebarIcon(
                    Icons.Outlined.Home, 
                    isSelected = selectedTab == AnisugTab.Home, 
                    selectedTint = Color.White,
                    onClick = { onTabSelect(AnisugTab.Home, null) }
                )
                SidebarIcon(
                    Icons.Default.Search, 
                    isSelected = selectedTab == AnisugTab.Search, 
                    onClick = { onTabSelect(AnisugTab.Search, null) }
                )
                SidebarIcon(
                    Icons.Outlined.Bookmarks, 
                    isSelected = selectedTab == AnisugTab.Bookmarks,
                    onClick = { onTabSelect(AnisugTab.Bookmarks, null) }
                )
                SidebarIcon(
                    Icons.Outlined.Download, 
                    isSelected = selectedTab == AnisugTab.Downloads,
                    onClick = { onTabSelect(AnisugTab.Downloads, null) }
                )
                SidebarIcon(
                    Icons.Outlined.Settings, 
                    isSelected = selectedTab == AnisugTab.Settings,
                    onClick = { onTabSelect(AnisugTab.Settings, null) }
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val uriHandler = LocalUriHandler.current
                DonateButton(onClick = { uriHandler.openUri("https://anisurge.lol/donate") })
                LogoutButton(isLoggingOut = isLoggingOut, onLogout = onLogout)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SidebarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    isSelected: Boolean,
    selectedTint: Color = Color.White,
    defaultTint: Color = Color.Gray.copy(alpha = 0.4f),
    onClick: () -> Unit = {}
) {
    val animatedTint by animateColorAsState(
        targetValue = if (isSelected) selectedTint else defaultTint,
        animationSpec = tween(durationMillis = 200)
    )
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.07f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200)
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(animatedBg)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = animatedTint, modifier = Modifier.size(22.dp))
        }
        // Active dot indicator
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White.copy(alpha = 0.6f) else Color.Transparent)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LogoutButton(isLoggingOut: Boolean, onLogout: () -> Unit) {
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        if (hovered && !isLoggingOut) Color.White.copy(alpha = 0.07f) else Color.Transparent,
        tween(200)
    )
    val iconAlpha by animateFloatAsState(if (isLoggingOut) 0f else 1f, tween(200))
    val spinnerAlpha by animateFloatAsState(if (isLoggingOut) 1f else 0f, tween(200))

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .hoverable(inter)
                .clickable(enabled = !isLoggingOut, onClick = onLogout),
            contentAlignment = Alignment.Center,
        ) {
            // Icon fades out when logging out
            Icon(
                Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "Logout",
                tint = Color(0xFFE50914).copy(alpha = iconAlpha),
                modifier = Modifier.size(22.dp),
            )
            // Spinner fades in when logging out
            if (spinnerAlpha > 0f) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = spinnerAlpha),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(Modifier.size(4.dp).clip(CircleShape).background(Color.Transparent))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DonateButton(onClick: () -> Unit) {
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        if (hovered) Color.White.copy(alpha = 0.07f) else Color.Transparent,
        tween(200)
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .hoverable(inter)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Donate",
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(22.dp),
            )
        }
        Box(Modifier.size(4.dp).clip(CircleShape).background(Color.Transparent))
        Spacer(Modifier.height(8.dp))
    }
}


@Composable
private fun MobileTopBar(
    avatarUrl: String?,
    onDownloadClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    tints = listOf(HazeTint(Color.Black.copy(alpha = 0.85f))),
                    blurRadius = 40.dp,
                    noiseFactor = 0.15f
                )
            )
            .pointerInput(Unit) { /* Consume clicks to prevent pass-through */ }
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo left
            androidx.compose.foundation.Image(
                painter = painterResource(Res.drawable.logo_txt),
                contentDescription = "Anisuge",
                modifier = Modifier.height(44.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Download icon button
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Download,
                        contentDescription = "Downloads",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // User avatar right
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1F1F)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Profile",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        // Subtle separator
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
    }
}

@Composable
private fun AnisugBottomBar(
    selectedTab: AnisugTab,
    onTabSelect: (AnisugTab) -> Unit,
    hazeState: HazeState,
    liquidGlass: Boolean,
    modifier: Modifier = Modifier
) {
    if (liquidGlass && !isDesktopPlatform) {
        LiquidGlassBottomBar(
            selectedTab = selectedTab,
            onTabSelect = onTabSelect,
            modifier = modifier
        )
        return
    }

    val navShape = RoundedCornerShape(if (liquidGlass) 30.dp else 28.dp)
    val navMaterial = if (liquidGlass) {
        Modifier
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    tints = listOf(
                        HazeTint(Color.Transparent)
                    ),
                    blurRadius = 5.dp,
                    noiseFactor = 0f
                )
            )
    } else {
        Modifier
            .background(Color(0xFF0D0D0D).copy(alpha = 0.86f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), navShape)
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    tints = listOf(HazeTint(Color.Black.copy(alpha = 0.55f))),
                    blurRadius = 40.dp,
                    noiseFactor = 0.08f
                )
            )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) { /* Consume clicks to prevent pass-through */ }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .height(if (liquidGlass) 64.dp else 62.dp)
                .clip(navShape)
                .then(navMaterial)
                .padding(horizontal = if (liquidGlass) 12.dp else 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calendar | Home | Search | Bookmarks | Settings
            BottomBarIcon(
                Icons.Outlined.CalendarToday,
                isSelected = selectedTab == AnisugTab.Calendar,
                liquidGlass = liquidGlass,
                onClick = { onTabSelect(AnisugTab.Calendar) }
            )
            BottomBarIcon(
                Icons.Outlined.Home,
                isSelected = selectedTab == AnisugTab.Home,
                selectedTint = Color.White,
                liquidGlass = liquidGlass,
                onClick = { onTabSelect(AnisugTab.Home) }
            )
            BottomBarIcon(
                Icons.Default.Search,
                isSelected = selectedTab == AnisugTab.Search,
                liquidGlass = liquidGlass,
                onClick = { onTabSelect(AnisugTab.Search) }
            )
            BottomBarIcon(
                Icons.Outlined.Bookmarks,
                isSelected = selectedTab == AnisugTab.Bookmarks,
                liquidGlass = liquidGlass,
                onClick = { onTabSelect(AnisugTab.Bookmarks) }
            )
            BottomBarIcon(
                Icons.Outlined.Settings,
                isSelected = selectedTab == AnisugTab.Settings,
                liquidGlass = liquidGlass,
                onClick = { onTabSelect(AnisugTab.Settings) }
            )
        }
    }
}

@Composable
private fun NormalAnisugBottomBar(
    selectedTab: AnisugTab,
    onTabSelect: (AnisugTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D0D))
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    tints = listOf(HazeTint(Color.Black.copy(alpha = 0.6f))),
                    blurRadius = 40.dp,
                    noiseFactor = 0.1f
                )
            )
            .pointerInput(Unit) { /* Consume clicks to prevent pass-through */ }
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarIcon(
                Icons.Outlined.CalendarToday,
                isSelected = selectedTab == AnisugTab.Calendar,
                onClick = { onTabSelect(AnisugTab.Calendar) }
            )
            BottomBarIcon(
                Icons.Outlined.Home,
                isSelected = selectedTab == AnisugTab.Home,
                selectedTint = Color.White,
                onClick = { onTabSelect(AnisugTab.Home) }
            )
            BottomBarIcon(
                Icons.Default.Search,
                isSelected = selectedTab == AnisugTab.Search,
                onClick = { onTabSelect(AnisugTab.Search) }
            )
            BottomBarIcon(
                Icons.Outlined.Bookmarks,
                isSelected = selectedTab == AnisugTab.Bookmarks,
                onClick = { onTabSelect(AnisugTab.Bookmarks) }
            )
            BottomBarIcon(
                Icons.Outlined.Settings,
                isSelected = selectedTab == AnisugTab.Settings,
                onClick = { onTabSelect(AnisugTab.Settings) }
            )
        }
    }
}

@Composable
private fun BottomBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    isSelected: Boolean,
    selectedTint: Color = Color.White,
    defaultTint: Color = Color.Gray.copy(alpha = 0.4f),
    liquidGlass: Boolean = false,
    onClick: () -> Unit = {}
) {
    val animatedTint by animateColorAsState(
        targetValue = when {
            isSelected -> selectedTint
            liquidGlass -> Color.White.copy(alpha = 0.72f)
            else -> defaultTint
        },
        animationSpec = tween(durationMillis = 200)
    )
    val animatedBg by animateColorAsState(
        targetValue = when {
            isSelected && liquidGlass -> Color.Black.copy(alpha = 0.08f)
            isSelected -> Color.White.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200)
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(if (liquidGlass) 18.dp else 12.dp))
            .background(animatedBg)
            .then(
                if (isSelected && liquidGlass) {
                    Modifier
                        .drawWithCache {
                            val corner = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                            onDrawWithContent {
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.10f),
                                    cornerRadius = corner
                                )
                                drawContent()
                            }
                        }
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = animatedTint, modifier = Modifier.size(24.dp))
            if (isSelected) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f))
                )
            }
        }
    }
}


// ── Utility ────────────────────────────────────────────────────────────────

private fun parseProgressFraction(progress: String, duration: String): Float {
    fun toSeconds(t: String): Int {
        val parts = t.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }
    val cur = toSeconds(progress)
    val tot = toSeconds(duration)
    return if (tot > 0) (cur.toFloat() / tot).coerceIn(0f, 1f) else 0f
}

/** Minimal URL query param parser (no android dependency). */
private object Uri {
    fun parseQueryParam(url: String, key: String): String? {
        val query = url.substringAfter('?', "")
        return query.split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
    }
}

// ── Offline State ─────────────────────────────────────────────────────────

// Removed HomeOfflineState, using shared OfflineState instead


// ── Downloads Tab ─────────────────────────────────────────────────────────

@Composable
fun DownloadsTab(
    onWatchOffline: (String, Int, String, String) -> Unit = { _, _, _, _ -> },
    onExit: () -> Unit = {}
) {
    val tasks by DownloadManager.tasks.collectAsState()
    val sortedTasks = remember(tasks) {
        tasks.sortedWith(
            compareBy<DownloadTask> { downloadTaskPriority(it) }
                .thenByDescending { if (it.status == "Finished") 1 else 0 }
                .thenByDescending { it.progress }
                .thenBy { it.title.lowercase() }
        )
    }
    val listState = rememberLazyListState()

    val finishedCount = tasks.count { it.status == "Finished" }
    val activeCount = tasks.count { it.status != "Finished" && !it.status.startsWith("Failed") }
    val failedCount = tasks.count { it.status.startsWith("Failed") }

    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            to.kuudere.anisuge.platform.WindowManagementButtons(
                onClose = onExit,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Download,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    text = "No downloads yet",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Episodes you download will appear here\nand play without an internet connection.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    } else {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            val cols = when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 580.dp -> 2
                else               -> 1
            }

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = if (maxWidth < 800.dp) 140.dp else 32.dp, top = 20.dp)
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        to.kuudere.anisuge.platform.WindowManagementButtons(
                            onClose = onExit,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
                item(key = "downloads-header") {
                    DownloadsAnimatedEntry(delayMs = 0) {
                        Column {
                            Text(
                                "Downloads",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Offline-ready episodes, live progress, and quick actions in one place.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DownloadsStatChip(
                                    label = "Active",
                                    value = activeCount.toString(),
                                    accent = Color.White
                                )
                                DownloadsStatChip(
                                    label = "Finished",
                                    value = finishedCount.toString(),
                                    accent = Color(0xFF48E27A)
                                )
                                if (failedCount > 0) {
                                    DownloadsStatChip(
                                        label = "Failed",
                                        value = failedCount.toString(),
                                        accent = Color(0xFFFF6B6B)
                                    )
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                        }
                    }
                }

                val rows = sortedTasks.chunked(cols)
                itemsIndexed(rows, key = { rowIdx, _ -> "row-$rowIdx" }) { rowIdx, rowTasks ->
                    DownloadsAnimatedEntry(delayMs = (rowIdx * 40).coerceAtMost(400)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            rowTasks.forEach { task ->
                                DownloadTaskCard(
                                    task = task,
                                    onWatchOffline = onWatchOffline,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // fill remaining columns with invisible spacers
                            repeat(cols - rowTasks.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                item(key = "downloads-bottom-spacer") {
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }
}

private fun downloadTaskPriority(task: DownloadTask): Int = when {
    task.status.startsWith("Downloading") -> 0
    task.status == "Fetching stream..." -> 0
    task.isPaused -> 1
    task.status.startsWith("Failed") -> 2
    task.status == "Finished" -> 3
    else -> 1
}

@Composable
private fun DownloadsAnimatedEntry(
    delayMs: Int = 0,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320)
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(320)
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetY.dp.toPx()
            }
    ) {
        content()
    }
}

@Composable
private fun DownloadsStatChip(
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onWatchOffline: (String, Int, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = DownloadManager
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()
    var showConfirmRemove by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (hovered) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.03f),
        animationSpec = tween(260, easing = FastOutSlowInEasing)
    )
    val borderColor by animateColorAsState(
        targetValue = if (hovered) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(260, easing = FastOutSlowInEasing)
    )
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.012f else 1f,
        animationSpec = tween(260, easing = FastOutSlowInEasing)
    )
    val progressValue by animateFloatAsState(
        targetValue = task.progress.coerceIn(0f, 1f),
        animationSpec = tween(280, easing = FastOutSlowInEasing)
    )

    val accentColor = when {
        task.status == "Finished" -> Color(0xFF48E27A)
        task.status.startsWith("Failed") -> Color(0xFFFF6B6B)
        task.isPaused -> Color.White.copy(alpha = 0.65f)
        else -> Color.White
    }
    val statusLabel = when {
        task.status == "Finished" -> "Ready offline"
        task.status.startsWith("Failed") -> "Needs attention"
        task.isPaused -> "Paused"
        task.status.startsWith("Downloading") -> "Downloading"
        else -> task.status
    }
    val detailText = when {
        task.status.startsWith("Downloading") && task.downloadSpeed.isNotEmpty() -> buildString {
            append(task.downloadSpeed)
            if (task.eta.isNotEmpty()) append(" • ${task.eta}")
        }
        task.status == "Finished" -> "Stored locally and ready to watch offline."
        task.status.startsWith("Failed") -> task.status
        task.isPaused -> "Resume anytime without losing progress."
        else -> task.status
    }

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .hoverable(inter)
    ) {
        // ── Top section: poster + metadata ───────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                if (task.coverImage != null) {
                    AsyncImage(
                        model = task.coverImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

            }

            // Metadata
            val isActive = task.status != "Finished"
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = if (isActive) Arrangement.SpaceBetween else Arrangement.spacedBy(5.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                    )
                    Text(
                        text = "Episode ${task.episodeNumber}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                    )
                    if (!isActive) {
                        Text(
                            text = detailText,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                    if (isActive) {
                        Text(
                            text = detailText,
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = task.status,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                text = "${(progressValue * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                            color = accentColor,
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )
                    }
                }
            }
        }

        // ── Divider ───────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

        // ── Bottom action bar: 3 equal columns ────────────────────────
        Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (task.status == "Finished") {
                // Folder button
                CardActionCell(
                    icon = Icons.Default.Folder,
                    label = "Folder",
                    modifier = Modifier.weight(1f),
                    onClick = { task.localPath?.let { to.kuudere.anisuge.utils.openDirectory(it) } },
                )
                Box(Modifier.width(1.dp).fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
                // Play button (primary)
                CardActionCell(
                    icon = Icons.Default.PlayArrow,
                    label = "Play",
                    isPrimary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { task.localPath?.let { onWatchOffline(task.animeId, task.episodeNumber, it, task.title) } },
                )
            } else {
                // Pause / Resume button
                CardActionCell(
                    icon = if (task.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (task.isPaused) "Resume" else "Pause",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (task.isPaused) manager.resumeDownload(task.id) else manager.pauseDownload(task.id)
                    },
                )
                Box(Modifier.width(1.dp).fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
                // Spacer column (placeholder to keep 3-column structure)
                Box(Modifier.weight(1f))
            }
            Box(Modifier.width(1.dp).fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
            // Remove button — always last
            CardActionCell(
                icon = Icons.Default.Close,
                label = "Remove",
                isDanger = true,
                modifier = Modifier.weight(1f),
                onClick = { showConfirmRemove = true },
            )
        }

        if (showConfirmRemove) {
            ConfirmDialog(
                title = "Remove download?",
                message = "\"${task.title}\" Episode ${task.episodeNumber} will be removed from your downloads list.",
                confirmLabel = "Remove",
                onConfirm = { manager.removeTask(task.id); showConfirmRemove = false },
                onDismiss = { showConfirmRemove = false },
            )
        }
    }
}

@Composable
private fun CardActionCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()

    val bg by animateColorAsState(
        targetValue = when {
            isPrimary && hovered -> Color.White.copy(alpha = 0.14f)
            isPrimary -> Color.Transparent
            isDanger && hovered -> Color(0xFFFF6B6B).copy(alpha = 0.12f)
            hovered -> Color.White.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    )
    val tint by animateColorAsState(
        targetValue = when {
            isPrimary -> Color(0xFF48E27A)
            isDanger -> Color(0xFFFF6B6B).copy(alpha = 0.85f)
            else -> Color.White.copy(alpha = 0.75f)
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .hoverable(inter)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
