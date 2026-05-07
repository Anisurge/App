package to.kuudere.anisuge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import to.kuudere.anisuge.screens.auth.AuthScreen
import to.kuudere.anisuge.screens.auth.AuthViewModel
import to.kuudere.anisuge.screens.splash.SplashScreen
import to.kuudere.anisuge.screens.splash.SplashViewModel
import to.kuudere.anisuge.screens.splash.SplashDestination
import to.kuudere.anisuge.screens.home.HomeScreen
import to.kuudere.anisuge.screens.home.HomeViewModel
import to.kuudere.anisuge.screens.home.ContinueWatchingScreen
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
import to.kuudere.anisuge.screens.settings.SettingsViewModel
import to.kuudere.anisuge.screens.latest.LatestEpisodesScreen
import to.kuudere.anisuge.screens.latest.LatestViewModel
import to.kuudere.anisuge.theme.AnisugTheme
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
import to.kuudere.anisuge.i18n.appStringsFor
/** Compat helper: reads a String from the new KMP SavedState arguments type. */
private fun SavedState?.str(key: String): String? =
    try { this?.read { if (contains(key)) getString(key) else null } } catch (_: Exception) { null }

@Composable
fun App(
    notificationLaunch: NotificationLaunch? = null,
    onNotificationLaunchConsumed: () -> Unit = {},
    onAppExit: () -> Unit = {},
) {
    AnisugTheme {
        val navController = rememberNavController()
        val splashVm = remember {
            SplashViewModel(
                AppComponent.authService,
                AppComponent.updateService,
                AppComponent.homeService,
                AppComponent.analyticsPingService,
            )
        }
        val authVm   = remember { AuthViewModel(AppComponent.authService) }
        val homeVm   = remember { HomeViewModel(AppComponent.homeService, AppComponent.authService, AppComponent.watchlistService) }
        val searchVm = remember { SearchViewModel(AppComponent.searchService) }
        val infoVm   = remember { AnimeInfoViewModel(AppComponent.infoService, AppComponent.watchlistService) }
        val watchVm  = remember { WatchViewModel(AppComponent.infoService, AppComponent.watchlistService, AppComponent.settingsStore, AppComponent.settingsService, AppComponent.serverRepository) }
        val watchlistVm = remember { WatchlistViewModel() }
        val scheduleVm = remember { ScheduleViewModel(AppComponent.scheduleService) }
        val settingsVm = remember { SettingsViewModel(AppComponent.settingsService, AppComponent.settingsStore, AppComponent.serverRepository, AppComponent.authService) }
        val latestVm = remember { LatestViewModel(AppComponent.latestService) }
        val updateVm = remember { UpdateViewModel(AppComponent.updateService) }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val isWatchScreen = navBackStackEntry?.destination?.route?.startsWith("watch/") == true
        val updateState by updateVm.state.collectAsState()
        val appLocaleCode by AppComponent.settingsStore.appLocaleFlow.collectAsState(initial = AppLocale.default.code)
        val appStrings = appStringsFor(AppLocale.fromCode(appLocaleCode))
        val uriHandler = LocalUriHandler.current
        
        var showExitConfirm by remember { mutableStateOf(false) }
        var pendingNotificationLaunch by remember { mutableStateOf<NotificationLaunch?>(null) }
        var notificationDialog by remember { mutableStateOf<NotificationLaunch?>(null) }
        
        // Handle back button - show exit confirmation only when at the root (home)
        val currentRoute = navBackStackEntry?.destination?.route
        val isAtRoot = currentRoute != null && 
            (currentRoute.startsWith("home") || currentRoute == Screen.Auth.route)
        
        PlatformBackHandler(enabled = isAtRoot && !showExitConfirm) {
            showExitConfirm = true
        }

        LaunchedEffect(notificationLaunch?.id) {
            if (notificationLaunch != null) {
                pendingNotificationLaunch = notificationLaunch
            }
        }

        // Discord Rich Presence removed - not supported by new API

        LaunchedEffect(currentRoute, pendingNotificationLaunch?.id) {
            val launch = pendingNotificationLaunch ?: return@LaunchedEffect
            val route = currentRoute ?: return@LaunchedEffect
            if (route == Screen.Splash.route || route == Screen.Auth.route || route.startsWith("update")) return@LaunchedEffect

            pendingNotificationLaunch = null
            onNotificationLaunchConsumed()

            when {
                launch.animeId != null && launch.episodeNumber != null -> {
                    navController.navigate(Screen.Watch(launch.animeId, launch.episodeNumber).route)
                }
                launch.animeId != null -> {
                    navController.navigate(Screen.Info(launch.animeId).route)
                }
                else -> {
                    notificationDialog = launch
                }
            }
        }


        CompositionLocalProvider(LocalAppStrings provides appStrings) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
            
            NavHost(
                navController    = navController,
                startDestination = Screen.Splash.route,
                // Splash exit: keep it visible while auth fades in on top
                enterTransition  = { fadeIn(animationSpec = tween(400)) },
                exitTransition   = { fadeOut(animationSpec = tween(400)) },
            ) {
                composable(Screen.Splash.route) {
                    SplashScreen(
                        viewModel        = splashVm,
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
                        viewModel      = authVm,
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
                        navArgument("downloads") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("tab") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val downloadsArg = backStackEntry.arguments.str("downloads") == "true"
                    val requestedTab = backStackEntry.arguments.str("tab")
                    HomeScreen(
                        homeViewModel = homeVm,
                        searchViewModel = searchVm,
                        watchlistViewModel = watchlistVm,
                        scheduleViewModel = scheduleVm,
                        settingsViewModel = settingsVm,
                        onAnimeClick = { animeId -> navController.navigate(Screen.Info(animeId).route) },
                        onWatchClick = { id, lang, ep, server -> navController.navigate(Screen.Watch(id, ep, server, lang).route) },
                        onWatchOffline = { id, ep, path, title ->
                            navController.navigate(Screen.Watch(id, ep, offlinePath = path, offlineTitle = title).route)
                        },
                        onLogout = {
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(Screen.Home().route) { inclusive = true }
                            }
                        },
                        onExit = onAppExit,
                        onViewContinueWatchingMore = { navController.navigate(Screen.ContinueWatching.route) },
                        startOnDownloads = downloadsArg || (splashVm.destination.value == SplashDestination.GoHomeOffline),
                        startTab = requestedTab
                    )
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
                        onWatchEpisode = { id, lang, ep -> navController.navigate(Screen.Watch(id, ep, null, lang).route) },
                        onDownloadsClick = {
                            navController.navigate(Screen.Home(startOnDownloads = true).route)
                        },
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
                        navArgument("server") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("lang") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("offlinePath") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("offlineTitle") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val animeId = backStackEntry.arguments.str("animeId") ?: ""
                    val episodeNumStr = backStackEntry.arguments.str("episodeNumber") ?: "1"
                    val episodeNum = episodeNumStr.toIntOrNull() ?: 1
                    val server = backStackEntry.arguments.str("server")
                    val lang = backStackEntry.arguments.str("lang")
                    val offlinePath = backStackEntry.arguments.str("offlinePath")
                    val offlineTitle = backStackEntry.arguments.str("offlineTitle")

                    WatchScreen(
                        animeId = animeId,
                        episodeNumber = episodeNum,
                        server = server,
                        lang = lang,
                        offlinePath = offlinePath,
                        offlineTitle = offlineTitle,
                        viewModel = watchVm,
                        onBack = { navController.popBackStack() },
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

                composable(Screen.ContinueWatching.route) {
                    ContinueWatchingScreen(
                        viewModel = homeVm,
                        onWatchClick = { id, lang, ep, server -> navController.navigate(Screen.Watch(id, ep, server, lang).route) },
                        onBack = { navController.popBackStack() }
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
