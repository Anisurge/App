package to.kuudere.anisuge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import to.kuudere.anisuge.navigation.Screen
import to.kuudere.anisuge.navigation.NotificationLaunch
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.screens.auth.AuthScreen
import to.kuudere.anisuge.screens.auth.AuthViewModel
import to.kuudere.anisuge.screens.splash.SplashScreen
import to.kuudere.anisuge.screens.splash.SplashViewModel
import to.kuudere.anisuge.screens.splash.SplashDestination
import to.kuudere.anisuge.screens.home.HomeScreen
import to.kuudere.anisuge.screens.home.HomeViewModel
import to.kuudere.anisuge.screens.home.ContinueWatchingScreen
import to.kuudere.anisuge.screens.chat.LiveChatScreen
import to.kuudere.anisuge.screens.chat.LiveChatViewModel
import to.kuudere.anisuge.screens.w2g.W2gRoomListScreen
import to.kuudere.anisuge.screens.w2g.W2gPlayerScreen
import to.kuudere.anisuge.screens.w2g.W2gViewModel
import to.kuudere.anisuge.screens.search.SearchScreen
import to.kuudere.anisuge.screens.search.SearchViewModel
import to.kuudere.anisuge.screens.search.KUUDERE_GENRES
import to.kuudere.anisuge.screens.info.AnimeInfoScreen
import to.kuudere.anisuge.screens.info.AnimeInfoViewModel
import to.kuudere.anisuge.screens.watch.WatchScreen
import to.kuudere.anisuge.screens.watch.WatchViewModel
import to.kuudere.anisuge.screens.watchlist.WatchlistViewModel
import to.kuudere.anisuge.screens.schedule.ScheduleViewModel
import to.kuudere.anisuge.screens.settings.SettingsScreen
import to.kuudere.anisuge.screens.settings.SettingsTab
import to.kuudere.anisuge.screens.settings.SettingsViewModel
import to.kuudere.anisuge.screens.settings.layout.LayoutEditorScreen
import to.kuudere.anisuge.screens.latest.LatestEpisodesScreen
import to.kuudere.anisuge.screens.latest.LatestViewModel
import to.kuudere.anisuge.screens.newonapp.NewOnAppScreen
import to.kuudere.anisuge.screens.tv.TvAppShell
import to.kuudere.anisuge.theme.AnisugTheme
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.theme.AppThemeId
import androidx.navigation.NamedNavArgument
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import to.kuudere.anisuge.screens.update.UpdateScreen
import to.kuudere.anisuge.screens.update.UpdateViewModel
import to.kuudere.anisuge.platform.LockScreenOrientation
import to.kuudere.anisuge.platform.PlatformBackHandler
import to.kuudere.anisuge.platform.isAndroidTvPlatform
import to.kuudere.anisuge.ui.ConfirmDialog
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.compose.ui.platform.LocalUriHandler
import to.kuudere.anisuge.i18n.AppLocale
import to.kuudere.anisuge.i18n.LocalAppStrings
import to.kuudere.anisuge.i18n.LocalPreferRomajiAnimeTitles
import to.kuudere.anisuge.i18n.appStringsFor
import to.kuudere.anisuge.screens.crash.CrashReportData
import to.kuudere.anisuge.screens.crash.CrashScreen
import to.kuudere.anisuge.screens.crash.parseCrashReportJson

/** Compat helper: reads a String from the new KMP SavedState arguments type. */
private fun SavedState?.str(key: String): String? =
    try {
        this?.read { if (contains(key)) getString(key) else null }
    } catch (_: Exception) {
        null
    }

private fun deepLinkLog(message: String) {
    println("[AnisurgeDeepLink] $message")
}

private fun settingsTabFromKey(key: String?): SettingsTab? = when (key?.lowercase()) {
    "profile" -> SettingsTab.Profile
    "preferences" -> SettingsTab.Preferences
    "storage" -> SettingsTab.Storage
    "appearance" -> SettingsTab.Appearance
    "sync" -> SettingsTab.Sync
    "connect" -> SettingsTab.Connect
    "servers" -> SettingsTab.Servers
    "notifications" -> SettingsTab.Notifications
    "shop", "store" -> SettingsTab.Shop
    "berries" -> SettingsTab.Berries
    else -> null
}

@Composable
fun App(
    /** When true (warm resume / deep link), start on home and do not show splash video. */
    skipSplash: Boolean = false,
    notificationLaunch: NotificationLaunch? = null,
    crashReportJson: String? = null,
    onNotificationLaunchConsumed: () -> Unit = {},
    onAppExit: () -> Unit = {},
    onAppRestart: () -> Unit = {},
) {
    val themeId by AppComponent.settingsStore.themeIdFlow.collectAsState(initial = "default")
    AnisugTheme(themeId = AppThemeId.fromId(themeId)) {
        val navController = rememberNavController()
        val splashVm = remember {
            SplashViewModel(
                AppComponent.authService,
                AppComponent.updateService,
                AppComponent.homeService,
                AppComponent.analyticsPingService,
            )
        }
        val authVm = remember { AuthViewModel(AppComponent.authService) }
        val homeVm = remember {
            HomeViewModel(
                AppComponent.homeService,
                AppComponent.authService,
                AppComponent.watchlistService,
                AppComponent.sessionStore,
                AppComponent.librarySyncService,
                AppComponent.settingsStore,
                AppComponent.searchService,
            )
        }
        val searchVm = remember { SearchViewModel(AppComponent.searchService) }
        val infoVm = remember { AnimeInfoViewModel(AppComponent.infoService, AppComponent.watchlistService, AppComponent.homeService) }
        val watchVm = remember {
            WatchViewModel(
                AppComponent.infoService,
                AppComponent.homeService,
                AppComponent.watchlistService,
                AppComponent.settingsStore,
                AppComponent.settingsService,
                AppComponent.serverRepository,
                AppComponent.aniskipService,
                AppComponent.syncManager,
                AppComponent.trackingService,
            )
        }
        val watchlistVm = remember { WatchlistViewModel() }
        val scheduleVm = remember { ScheduleViewModel(AppComponent.scheduleService) }
        val settingsVm = remember {
            SettingsViewModel(
                AppComponent.settingsService,
                AppComponent.settingsStore,
                AppComponent.serverRepository,
                AppComponent.authService,
                AppComponent.trackingService,
                AppComponent.watchlistService,
                AppComponent.communityService,
                AppComponent.watchHistorySyncService,
                AppComponent.malAnilistIdCache,
                AppComponent.integrationsSyncService,
                AppComponent.bffMeService,
                AppComponent.bffShopService,
                AppComponent.stickerService,
                AppComponent.bffRewardsService,
            )
        }
        val latestVm = remember { LatestViewModel(AppComponent.latestService) }
        val updateVm = remember { UpdateViewModel(AppComponent.updateService) }
        val liveChatVm = remember {
            LiveChatViewModel(
                AppComponent.chatService,
                AppComponent.authService,
                AppComponent.searchService,
                AppComponent.stickerService,
            )
        }
        val w2gVm = remember {
            W2gViewModel(
                AppComponent.sessionStore,
                AppComponent.w2gRoomService,
                AppComponent.searchService,
                AppComponent.serverRepository,
                AppComponent.infoService,
                AppComponent.stickerService,
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val isWatchScreen = navBackStackEntry?.destination?.route?.startsWith("watch/") == true
        val updateState by updateVm.state.collectAsState()
        val authState by AppComponent.authService.authState.collectAsState()
        val currentUserProfile = (authState as? SessionCheckResult.Valid)?.user

        LaunchedEffect(authState) {
            if (authState is SessionCheckResult.NoSession || authState is SessionCheckResult.Expired) {
                val current = navController.currentDestination?.route
                if (current != null &&
                    current != Screen.Splash.route &&
                    current != Screen.Auth.route &&
                    !current.startsWith("update")
                ) {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
        val appLocaleCode by AppComponent.settingsStore.appLocaleFlow.collectAsState(initial = AppLocale.default.code)
        val appStrings = appStringsFor(AppLocale.fromCode(appLocaleCode))
        val preferRomajiAnimeTitles by AppComponent.settingsStore.preferRomajiAnimeTitlesFlow.collectAsState(initial = false)
        val uriHandler = LocalUriHandler.current
        val handleChatAction: (String) -> Unit = { raw ->
            val deeplink = raw.trim()
            when {
                deeplink.startsWith("anisurge://anime/", ignoreCase = true) -> {
                    val animeId = deeplink.removePrefix("anisurge://anime/").substringBefore("/")
                    if (animeId.isNotBlank()) {
                        navController.navigate(Screen.Info(animeId).route) { launchSingleTop = true }
                    }
                }
                deeplink.equals("anisurge://home", ignoreCase = true) -> {
                    navController.navigate(Screen.Home().route) { launchSingleTop = true }
                }
                deeplink.equals("anisurge://search", ignoreCase = true) -> {
                    navController.navigate(Screen.Home(startTab = "Search").route) { launchSingleTop = true }
                }
                deeplink.equals("anisurge://watchlist", ignoreCase = true) -> {
                    navController.navigate(Screen.Home(startTab = "Bookmarks").route) { launchSingleTop = true }
                }
                deeplink.equals("anisurge://downloads", ignoreCase = true) -> {
                    navController.navigate(Screen.Home(startTab = "Downloads").route) { launchSingleTop = true }
                }
                deeplink.equals("anisurge://chat", ignoreCase = true) -> {
                    navController.navigate(Screen.LiveChat.route) { launchSingleTop = true }
                }
                deeplink.startsWith("anisurge://settings", ignoreCase = true) -> {
                    val key = deeplink.removePrefix("anisurge://settings").trim('/').substringBefore("/")
                    val tabKey = key.ifBlank { "profile" }
                    navController.navigate(
                        Screen.Home(startTab = "Settings", startSettingsTab = tabKey).route
                    ) { launchSingleTop = true }
                }
            }
        }

        var showExitConfirm by remember { mutableStateOf(false) }
        var homeBackAction by remember { mutableStateOf<(() -> Boolean)?>(null) }
        var notificationDialog by remember { mutableStateOf<NotificationLaunch?>(null) }

        /** Frozen at first composition — do not change when [skipSplash] flips on warm [onNewIntent]. */
        val initialSkipSplash = remember { skipSplash }

        // Handle back button - show exit confirmation only when at the root (home)
        val currentRoute = navBackStackEntry?.destination?.route
        val isAtRoot = currentRoute != null &&
                (currentRoute.startsWith("home") || currentRoute == Screen.Auth.route)

        val onHomeRoute = currentRoute?.startsWith("home") == true
        PlatformBackHandler(enabled = onHomeRoute && !showExitConfirm) {
            if (homeBackAction?.invoke() == true) return@PlatformBackHandler
            if (isAtRoot) showExitConfirm = true
        }

        LaunchedEffect(skipSplash) {
            if (skipSplash) {
                homeVm.refresh(force = false)
            }
        }

        LaunchedEffect(notificationLaunch?.id) {
            val launch = notificationLaunch ?: return@LaunchedEffect
            deepLinkLog("launch id=${launch.id} anime=${launch.animeId} ep=${launch.episodeNumber}")

            snapshotFlow { navController.currentDestination?.route }
                .filter { route ->
                    route != null &&
                            route != Screen.Splash.route &&
                            route != Screen.Auth.route &&
                            !route.startsWith("update")
                }
                .first()

            val readyRoute = navController.currentDestination?.route
            deepLinkLog("nav ready route=$readyRoute")
            onNotificationLaunchConsumed()

            when {
                launch.animeId != null && launch.episodeNumber != null -> {
                    val dest = Screen.Watch(launch.animeId, launch.episodeNumber).route
                    deepLinkLog("navigate watch $dest")
                    navController.navigate(dest) { launchSingleTop = true }
                }

                launch.animeId != null -> {
                    val dest = Screen.Info(launch.animeId).route
                    deepLinkLog("navigate info $dest")
                    navController.navigate(dest) { launchSingleTop = true }
                }

                else -> {
                    deepLinkLog("show notification dialog")
                    notificationDialog = launch
                }
            }
        }


        CompositionLocalProvider(
            LocalAppStrings provides appStrings,
            LocalPreferRomajiAnimeTitles provides preferRomajiAnimeTitles,
        ) {
            Box(modifier = Modifier.fillMaxSize().background(AppColors.background)) {
                val crashData = remember { mutableStateOf(crashReportJson?.let { parseCrashReportJson(it) }) }

                if (crashData.value != null) {
                    CrashScreen(
                        crashData = crashData.value!!,
                        onRestart = onAppRestart,
                    )
                    return@Box
                }

                if (!isWatchScreen) {
                    LockScreenOrientation(landscape = isAndroidTvPlatform)
                }

                if (showExitConfirm) {
                    ConfirmDialog(
                        title = appStrings.exitApp,
                        message = appStrings.exitAppMessage,
                        confirmLabel = appStrings.exit,
                        onConfirm = {
                            showExitConfirm = false
                            onAppExit()
                        },
                        onDismiss = { showExitConfirm = false }
                    )
                }

                notificationDialog?.let { launch ->
                    val openUrl = launch.actionUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                        ?: launch.mediaUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                    val mediaLabel = when (launch.mediaType?.lowercase()) {
                        "video" -> "Open video"
                        "audio" -> "Open audio"
                        "image" -> "Open image"
                        "link" -> "Open link"
                        else -> "Open"
                    }
                    ConfirmDialog(
                        title = launch.title,
                        message = launch.body.ifBlank { "Open this notification from Anisurge." },
                        confirmLabel = if (openUrl != null) launch.actionLabel ?: mediaLabel else "OK",
                        dismissLabel = appStrings.close,
                        isDanger = false,
                        onConfirm = {
                            openUrl?.let { uriHandler.openUri(it) }
                            notificationDialog = null
                        },
                        onDismiss = { notificationDialog = null }
                    )
                }

                val navStartDestination =
                    if (initialSkipSplash) Screen.Home().route else Screen.Splash.route

                NavHost(
                    navController = navController,
                    startDestination = navStartDestination,
                    // Splash exit: keep it visible while auth fades in on top
                    enterTransition = { fadeIn(animationSpec = tween(400)) },
                    exitTransition = { fadeOut(animationSpec = tween(400)) },
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            viewModel = splashVm,
                            onNavigateToAuth = {
                                val targetRoute = if (updateState.isUpdateAvailable == true) {
                                    Screen.Update(Screen.Auth.route).route
                                } else if (updateState.isUpdateAvailable == null) {
                                    // Still checking, go to Update screen which shows spinner
                                    Screen.Update(Screen.Auth.route).route
                                } else {
                                    // Definitely NO update, skip to Auth
                                    Screen.Auth.route
                                }

                                navController.navigate(targetRoute) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            },
                            onNavigateToHome = {
                                val nextRoute = Screen.Home().route
                                val targetRoute = if (updateState.isUpdateAvailable == true) {
                                    Screen.Update(nextRoute).route
                                } else if (updateState.isUpdateAvailable == null) {
                                    // Still checking, go to Update screen which shows spinner
                                    Screen.Update(nextRoute).route
                                } else {
                                    // Definitely NO update, skip to Home
                                    nextRoute
                                }

                                navController.navigate(targetRoute) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Screen.Auth.route) {
                        AuthScreen(
                            viewModel = authVm,
                            onLoginSuccess = {
                                homeVm.refresh(force = true)
                                watchlistVm.refresh()
                                settingsVm.refresh()
                                navController.navigate(Screen.Home().route) {
                                    popUpTo(Screen.Auth.route) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(
                        route = Screen.Home.route,
                        arguments = listOf(
                            navArgument("downloads") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("tab") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("settingsTab") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val downloadsArg = backStackEntry.arguments.str("downloads") == "true"
                        val requestedTab = backStackEntry.arguments.str("tab")
                        val requestedSettingsTab = settingsTabFromKey(backStackEntry.arguments.str("settingsTab"))
                        if (isAndroidTvPlatform) {
                            TvAppShell(
                                homeViewModel = homeVm,
                                searchViewModel = searchVm,
                                watchlistViewModel = watchlistVm,
                                onAnimeClick = { animeId -> navController.navigate(Screen.Info(animeId).route) },
                                onWatchClick = { id, lang, ep, server, resumeAt ->
                                    navController.navigate(
                                        Screen.Watch(
                                            id,
                                            ep,
                                            server,
                                            lang,
                                            resumeAtSeconds = resumeAt
                                        ).route
                                    )
                                },
                                onLogout = {
                                    navController.navigate(Screen.Auth.route) {
                                        popUpTo(Screen.Home().route) { inclusive = true }
                                    }
                                },
                            )
                        } else {
                            HomeScreen(
                                homeViewModel = homeVm,
                                searchViewModel = searchVm,
                                watchlistViewModel = watchlistVm,
                                scheduleViewModel = scheduleVm,
                                settingsViewModel = settingsVm,
                                onAnimeClick = { animeId -> navController.navigate(Screen.Info(animeId).route) },
                                onWatchClick = { id, lang, ep, server, resumeAt ->
                                    navController.navigate(
                                        Screen.Watch(
                                            id,
                                            ep,
                                            server,
                                            lang,
                                            resumeAtSeconds = resumeAt
                                        ).route
                                    )
                                },
                                onWatchOffline = { id, ep, path, title ->
                                    navController.navigate(
                                        Screen.Watch(
                                            id,
                                            ep,
                                            offlinePath = path,
                                            offlineTitle = title
                                        ).route
                                    )
                                },
                                onLogout = {
                                    navController.navigate(Screen.Auth.route) {
                                        popUpTo(Screen.Home().route) { inclusive = true }
                                    }
                                },
                                onExit = onAppExit,
                                onViewContinueWatchingMore = { navController.navigate(Screen.ContinueWatching.route) },
                                onViewLatestEpisodesMore = { navController.navigate(Screen.Latest.route) },
                                onViewNewOnAppMore = { navController.navigate(Screen.NewOnApp.route) },
                                onOpenLayoutEditor = { navController.navigate(Screen.HomeLayout.route) },
                                liveChatViewModel = liveChatVm,
                                onLiveChatClick = { navController.navigate(Screen.LiveChat.route) },
                                onW2gClick = { navController.navigate(Screen.W2gRoomList.route) },
                                onLiveChatSignIn = {
                                    navController.navigate(Screen.Auth.route) {
                                        popUpTo(Screen.Home().route) { inclusive = false }
                                    }
                                },
                                onChatAction = handleChatAction,
                                startOnDownloads = downloadsArg || (splashVm.destination.value == SplashDestination.GoHomeOffline),
                                startTab = requestedTab,
                                startSettingsTab = requestedSettingsTab,
                                onHomeBackActionChange = { homeBackAction = it },
                            )
                        }
                    }

                    composable(Screen.Search.route) {
                        SearchScreen(
                            viewModel = searchVm,
                            onAnimeClick = { animeId -> navController.navigate(Screen.Info(animeId).route) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Info.route) { backStackEntry ->
                        val animeId = backStackEntry.arguments.str("animeId") ?: ""
                        AnimeInfoScreen(
                            animeId = animeId,
                            viewModel = infoVm,
                            onBack = { navController.popBackStack() },
                            onWatchEpisode = { id, lang, ep ->
                                navController.navigate(
                                    Screen.Watch(
                                        id,
                                        ep,
                                        null,
                                        lang
                                    ).route
                                )
                            },
                            onDownloadsClick = {
                                navController.navigate(Screen.Home(startOnDownloads = true).route)
                            },
                            isPremiumUser = currentUserProfile?.isPremium == true,
                            onGenreClick = { genre ->
                                searchVm.clearFilters()
                                searchVm.onGenreToggle(genre)
                                searchVm.search()
                                navController.navigate(Screen.Home(startTab = "Search").route)
                            },
                            onExit = onAppExit
                        )
                    }

                    composable(
                        route = Screen.Watch.route,
                        enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)) },
                        exitTransition = { fadeOut(animationSpec = tween(400)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(400)) },
                        popExitTransition = { fadeOut(animationSpec = tween(200)) },
                        arguments = listOf(
                            navArgument("animeId") { type = androidx.navigation.NavType.StringType },
                            navArgument("episodeNumber") { type = androidx.navigation.NavType.StringType },
                            navArgument("server") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("lang") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("offlinePath") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("offlineTitle") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("resumeAt") {
                                type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                            },
                        )
                    ) { backStackEntry ->
                        val animeId = backStackEntry.arguments.str("animeId") ?: ""
                        val episodeNumStr = backStackEntry.arguments.str("episodeNumber") ?: "1"
                        val episodeNum = episodeNumStr.toIntOrNull() ?: 1
                        val server = backStackEntry.arguments.str("server")
                        val lang = backStackEntry.arguments.str("lang")
                        val offlinePath = backStackEntry.arguments.str("offlinePath")
                        val offlineTitle = backStackEntry.arguments.str("offlineTitle")
                        val resumeAtStr = backStackEntry.arguments.str("resumeAt")
                        val resumeAtSeconds = resumeAtStr?.toDoubleOrNull()

                        WatchScreen(
                            animeId = animeId,
                            episodeNumber = episodeNum,
                            server = server,
                            lang = lang,
                            offlinePath = offlinePath,
                            offlineTitle = offlineTitle,
                            resumeAtSeconds = resumeAtSeconds,
                            viewModel = watchVm,
                            isPremiumUser = currentUserProfile?.isPremium == true,
                            onBack = {
                                homeVm.refreshContinueWatching()
                                infoVm.refreshWatchProgress()
                                navController.popBackStack()
                            },
                            onExit = onAppExit
                        )
                    }

                    composable(Screen.Latest.route) {
                        LatestEpisodesScreen(
                            viewModel = latestVm,
                            onAnimeClick = { animeId -> navController.navigate(Screen.Info(animeId).route) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.NewOnApp.route) {
                        NewOnAppScreen(
                            viewModel = searchVm,
                            onAnimeClick = { animeId -> navController.navigate(Screen.Info(animeId).route) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.ContinueWatching.route) {
                        ContinueWatchingScreen(
                            viewModel = homeVm,
                            onWatchClick = { id, lang, ep, server, resumeAt ->
                                navController.navigate(
                                    Screen.Watch(
                                        id,
                                        ep,
                                        server,
                                        lang,
                                        resumeAtSeconds = resumeAt
                                    ).route
                                )
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.HomeLayout.route) {
                        LayoutEditorScreen(
                            settingsViewModel = settingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(Screen.LiveChat.route) {
                        LiveChatScreen(
                            viewModel = liveChatVm,
                            onBack = { navController.popBackStack() },
                            onSignIn = {
                                navController.navigate(Screen.Auth.route) {
                                    popUpTo(Screen.Home().route) { inclusive = false }
                                }
                            },
                            onAction = handleChatAction,
                        )
                    }

                    composable(Screen.W2gRoomList.route) {
                        W2gRoomListScreen(
                            viewModel = w2gVm,
                            onRoomClick = { code, hasPassword ->
                                navController.navigate(Screen.W2gRoom.route(code))
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = Screen.W2gRoom.ROUTE,
                        arguments = listOf(
                            navArgument("inviteCode") { type = androidx.navigation.NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val inviteCode = backStackEntry.arguments.str("inviteCode") ?: return@composable
                        W2gPlayerScreen(
                            inviteCode = inviteCode,
                            viewModel = w2gVm,
                            userId = currentUserProfile?.effectiveId,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = Screen.Update.route,
                        arguments = listOf(
                            navArgument("next") { type = androidx.navigation.NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val next = backStackEntry.arguments.str("next")?.replace("_", "/") ?: Screen.Home().route
                        val state by updateVm.state.collectAsState()
                        UpdateScreen(
                            state = state,
                            onUpdateLater = {
                                navController.navigate(next) {
                                    popUpTo(Screen.Update.route) { inclusive = true }
                                }
                            },
                            onUpdateNow = {
                                // Link is opened in UpdateScreen.kt,
                                // we stay here so user can finish download/install
                            }
                        )
                    }
                }
            }
        }
    }
}
