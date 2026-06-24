package to.kuudere.anisuge.screens.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import kotlinx.datetime.Clock
import to.kuudere.anisuge.platform.DiscordPresenceActivity
import to.kuudere.anisuge.platform.DiscordRichPresenceManager
import to.kuudere.anisuge.platform.LockScreenOrientation
import to.kuudere.anisuge.platform.ScreenOrientationMode
import to.kuudere.anisuge.platform.isAndroidPlatform
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.platform.isDesktopPlatform
import to.kuudere.anisuge.platform.isAndroidTvPlatform
import to.kuudere.anisuge.platform.rememberPipManager
import to.kuudere.anisuge.player.PlayerControls
import to.kuudere.anisuge.player.StreamProxy
import to.kuudere.anisuge.player.VideoPlayerState
import to.kuudere.anisuge.player.VideoPlayerSurface
import to.kuudere.anisuge.player.rememberVideoPlayerState
import to.kuudere.anisuge.utils.DownloadManager
import to.kuudere.anisuge.utils.HlsPngTsStrip
import to.kuudere.anisuge.utils.ProgressiveStreamAccel
import coil3.compose.AsyncImage
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.layout.ContentScale
import to.kuudere.anisuge.ui.WatchlistBottomSheet
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.extensions.resolveBridgeProxyStreamUrl
import to.kuudere.anisuge.i18n.resolveDisplayTitle

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun WatchScreen(
    animeId: String,
    episodeNumber: Int,
    server: String? = null,
    lang: String? = null,
    offlinePath: String? = null,
    offlineTitle: String? = null,
    resumeAtSeconds: Double? = null,
    standaloneExtensionTitle: String? = null,
    viewModel: WatchViewModel,
    isPremiumUser: Boolean = false,
    onBack: () -> Unit,
    onExit: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingExtensionMatch by AppComponent.extensionManager.pendingMatch.collectAsState()

    uiState.pendingExtensionStreams?.let { pending ->
        ExtensionStreamPickerSheet(
            sourceName = pending.sourceName,
            videos = pending.videos,
            isLoading = pending.isLoading,
            onSelect = { viewModel.confirmExtensionStream(it) },
            onDismiss = { viewModel.dismissExtensionStreamPicker() },
        )
    }

    pendingExtensionMatch?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                AppComponent.extensionManager.choosePendingMatch(pending.requestId, null)
            },
            title = { Text("Choose the correct anime") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${pending.sourceName} returned multiple matches for ${pending.animeTitle}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    pending.candidates.forEach { (media, confidence) ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                AppComponent.extensionManager.choosePendingMatch(pending.requestId, media)
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                media.thumbnailUrl?.let {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = null,
                                        modifier = Modifier.size(46.dp, 64.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(media.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${(confidence * 100).toInt()}% match",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    AppComponent.extensionManager.choosePendingMatch(pending.requestId, null)
                }) { Text("Cancel") }
            },
        )
    }

    LaunchedEffect(animeId, episodeNumber, offlinePath, server, lang, offlineTitle, resumeAtSeconds, standaloneExtensionTitle) {
        viewModel.initialize(
            animeId,
            episodeNumber,
            server,
            lang,
            offlinePath,
            offlineTitle,
            resumeFromContinueSeconds = resumeAtSeconds,
            standaloneExtensionTitle = standaloneExtensionTitle,
        )
    }

    // Check if the ViewModel hasn't been initialized for this animeId yet.
    // Only animeId is used — episodeNumber and offlinePath can change via onEpisodeSelected()
    // from within the screen (ep list, auto-next, next button). Comparing them against the
    // fixed nav params would keep isStateStale=true forever after any in-screen navigation.
    val isStateStale = uiState.animeId != animeId

    val isLoading = uiState.isLoading || isStateStale
    val loadingMessage = if (isStateStale) {
        if (offlinePath != null) "Loading offline video..." else "Fetching episode $episodeNumber..."
    } else {
        uiState.loadingMessage
    }
    val useAndroidWatchPage = isAndroidPlatform &&
            !isAndroidTvPlatform &&
            uiState.offlinePath == null &&
            !isStateStale
    var forcePortraitAfterFullscreenExit by remember(animeId, offlinePath) { mutableStateOf(false) }

    val orientationMode = when {
        // Offline (downloaded) playback uses the full-bleed player with no episode list below it,
        // so it should be landscape like online fullscreen — otherwise a 16:9 video is locked in
        // portrait and renders tiny.
        isAndroidPlatform && !isAndroidTvPlatform && (uiState.isFullscreen || uiState.offlinePath != null) ->
            ScreenOrientationMode.Landscape

        isAndroidPlatform && !isAndroidTvPlatform && forcePortraitAfterFullscreenExit -> ScreenOrientationMode.Portrait
        isAndroidPlatform && !isAndroidTvPlatform -> ScreenOrientationMode.User
        else -> ScreenOrientationMode.Landscape
    }
    LockScreenOrientation(orientationMode)
    to.kuudere.anisuge.platform.SyncFullscreen(uiState.isFullscreen)

    val setFullscreenMode: (Boolean) -> Unit = { fullscreen ->
        if (isAndroidPlatform && !isAndroidTvPlatform && uiState.offlinePath == null) {
            forcePortraitAfterFullscreenExit = uiState.isFullscreen && !fullscreen
        }
        if (fullscreen) {
            forcePortraitAfterFullscreenExit = false
        }
        viewModel.setFullscreen(fullscreen)
    }

    val handleBack = {
        if (uiState.isFullscreen) {
            setFullscreenMode(false)
        } else {
            viewModel.flushLatestWatchProgress()
            onBack()
        }
    }

    to.kuudere.anisuge.platform.PlatformBackHandler {
        handleBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.checkSyncTokens()
    }

    LaunchedEffect(uiState.syncSnackbar) {
        uiState.syncSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSyncSnackbar()
        }
    }

    LaunchedEffect(uiState.berriesToast) {
        uiState.berriesToast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissBerriesToast()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && !useAndroidWatchPage) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // High-Performance Dual-Circle Loader (Psychologically feels faster)
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                            val infiniteTransition = rememberInfiniteTransition()

                            val rotateCW by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing)
                                ),
                                label = "OuterRotate"
                            )
                            val rotateCCW by infiniteTransition.animateFloat(
                                initialValue = 360f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing)
                                ),
                                label = "InnerRotate"
                            )

                            // Outer Circle (Clockwise)
                            CircularProgressIndicator(
                                progress = { 0.75f },
                                modifier = Modifier.size(60.dp).graphicsLayer { rotationZ = rotateCW },
                                color = Color.White,
                                strokeWidth = 2.dp,
                                trackColor = Color.White.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round
                            )

                            // Inner Circle (Counter-Clockwise)
                            CircularProgressIndicator(
                                progress = { 0.6f },
                                modifier = Modifier.size(35.dp).graphicsLayer { rotationZ = rotateCCW },
                                color = Color.White.copy(alpha = 0.6f),
                                strokeWidth = 2.dp,
                                trackColor = Color.White.copy(alpha = 0.05f),
                                strokeCap = StrokeCap.Round
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Dynamic Loading Steps (Perceived Progress)
                        loadingMessage?.let { msg ->
                            Text(
                                text = msg.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.alpha(0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Subtle progress detail
                            Text(
                                text = "INITIALIZING SECURE PLAYBACK PIPELINE",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.alpha(0.5f)
                            )
                        }
                    }

                    // Back button for mobile/desktop while loading
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(16.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    to.kuudere.anisuge.platform.DraggableWindowArea(
                        modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.TopStart)
                    ) { }

                    to.kuudere.anisuge.platform.WindowManagementButtons(
                        onClose = onExit,
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    )
                }
            } else if (useAndroidWatchPage) {
                AndroidWatchPageLayout(
                    uiState = uiState,
                    viewModel = viewModel,
                    animeId = animeId,
                    offlinePath = offlinePath,
                    isPremiumUser = isPremiumUser,
                    allowLandscapeTabs = !forcePortraitAfterFullscreenExit,
                    onFullscreenToggle = { setFullscreenMode(!uiState.isFullscreen) },
                    onBack = handleBack,
                    onExit = onExit
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    var commentsSheetOpen by remember { mutableStateOf(false) }
                    val isPanelActive = uiState.activeSidePanel != null
                    val sidePanelWidth = 350.dp

                    Row(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            // Use unique key so player FULLY resets when video changes
                            // This prevents old video from persisting when switching online->offline
                            val playerKey = "$animeId-${uiState.currentEpisodeNumber}-${offlinePath ?: "online"}"
                            key(playerKey) {
                                WatchVideoPlayer(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    isPremiumUser = isPremiumUser,
                                    onFullscreenToggle = { setFullscreenMode(!uiState.isFullscreen) },
                                    onBack = handleBack,
                                    onExit = onExit,
                                    onInfoClick = {
                                        viewModel.toggleSidePanel("info")
                                    },
                                    onEpisodesClick = {
                                        viewModel.toggleSidePanel("episodes")
                                    },
                                    onCommentsClick = {
                                        commentsSheetOpen = true
                                        viewModel.toggleSidePanel(null)
                                    },
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isPanelActive,
                            enter = slideInHorizontally(animationSpec = tween(180)) { it } + expandHorizontally(
                                animationSpec = tween(180),
                                expandFrom = Alignment.Start
                            ) + fadeIn(animationSpec = tween(140)),
                            exit = slideOutHorizontally(animationSpec = tween(140)) { it } + shrinkHorizontally(
                                animationSpec = tween(140),
                                shrinkTowards = Alignment.Start
                            ) + fadeOut(animationSpec = tween(100))
                        ) {
                            Box(Modifier.width(sidePanelWidth).fillMaxHeight()) {
                                SidePanelContent(uiState, viewModel, animeId)
                            }
                        }
                    }

                    WatchCommentsBottomSheet(
                        visible = commentsSheetOpen,
                        animeId = animeId,
                        episodeNumber = uiState.currentEpisodeNumber,
                        onDismiss = { commentsSheetOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.background(Color(0xFF000000).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SidePanelContent(uiState: WatchUiState, viewModel: WatchViewModel, animeId: String = "") {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // Removed bulky gray header - using floating close button instead

        // Content
        Box(Modifier.fillMaxSize()) {
            // Close button overlay
            IconButton(
                onClick = { viewModel.toggleSidePanel(null) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
            }

            AnimatedContent(
                targetState = uiState.activeSidePanel,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "SidePanelAnimation"
            ) { activePanel ->
                when (activePanel) {
                    "info" -> {
                        val episodeData = uiState.episodeData
                        var showWatchlistSheet by remember { mutableStateOf(false) }
                        val title = episodeData?.title?.resolveDisplayTitle()?.takeIf { it.isNotBlank() }
                            ?: uiState.offlineTitle
                            ?: "Unknown"
                        val bannerUrl = episodeData?.bannerImage?.takeIf {
                            it.isNotBlank() && it != "null" && !it.contains("placeholder") && it.startsWith("http")
                        }
                        val backgroundImage = bannerUrl ?: episodeData?.coverImage?.bestUrl
                        val hasBanner = bannerUrl != null
                        val isInWatchlist = episodeData?.folder != null
                        val anime = episodeData?.anime
                        val animeDescription = stripWatchInfoHtml(anime?.description)
                        val scoreLabel = anime?.score?.let { "${it}%" }
                        val episodeCountLabel = anime?.epCount?.let { "$it eps" }
                        val durationLabel = anime?.duration?.trim()?.takeIf { it.isNotBlank() }?.let {
                            if (it.contains("min", ignoreCase = true)) it else "$it min"
                        }
                        val seasonLabel = buildString {
                            anime?.season?.takeIf { it.isNotBlank() }?.let { append(it.prettyInfoLabel()) }
                            anime?.seasonYear?.let {
                                if (isNotEmpty()) append(" ")
                                append(it)
                            }
                        }.takeIf { it.isNotBlank() }
                        val infoChips = listOfNotNull(
                            anime?.format?.takeIf { it.isNotBlank() }?.prettyInfoLabel(),
                            anime?.status?.takeIf { it.isNotBlank() }?.prettyInfoLabel(),
                            episodeCountLabel,
                            durationLabel,
                            seasonLabel,
                            scoreLabel,
                        )
                        val watchlistButtonLabel = if (isInWatchlist) {
                            episodeData.folder.takeIf { it.isNotBlank() && it != "Remove" }
                                ?: "In Watchlist"
                        } else {
                            "Watchlist"
                        }

                        BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            val compact = maxWidth < 340.dp
                            val heroHeight = if (compact) 220.dp else 250.dp
                            val posterWidth = if (compact) 116.dp else 130.dp
                            val posterHeight = if (compact) 170.dp else 190.dp
                            val heroOverlap = if (compact) 122.dp else 140.dp
                            val titleSize = if (compact) 20.sp else 24.sp

                            Column(Modifier.fillMaxWidth()) {
                                Box {
                                    AsyncImage(
                                        model = backgroundImage,
                                        contentDescription = "Background",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(heroHeight)
                                            .blur(if (hasBanner) 16.dp else 48.dp)
                                            .alpha(if (hasBanner) 0.6f else 0.75f)
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(heroHeight)
                                            .background(
                                                Brush.verticalGradient(
                                                    0.0f to Color.Black.copy(alpha = 0.0f),
                                                    0.4f to Color.Black.copy(alpha = 0.4f),
                                                    1.0f to Color.Black
                                                )
                                            )
                                    )
                                }

                                Box(Modifier.offset(y = (-heroOverlap))) {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            AsyncImage(
                                                model = episodeData?.coverImage?.bestUrl,
                                                contentDescription = "Cover",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .width(posterWidth)
                                                    .height(posterHeight)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(AppColors.surfaceVariant)
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = title,
                                                    color = Color.White,
                                                    fontSize = titleSize,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(16.dp))

                                                Box(
                                                    Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(AppColors.surfaceVariant)
                                                        .clickable { showWatchlistSheet = true }
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            if (episodeData?.folder != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                            null,
                                                            tint = AppColors.text,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            text = watchlistButtonLabel,
                                                            color = AppColors.text,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(16.dp))

                                        if (infoChips.isNotEmpty()) {
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                infoChips.forEach { chip ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(AppColors.surface)
                                                            .border(
                                                                width = 1.dp,
                                                                color = AppColors.border,
                                                                shape = RoundedCornerShape(999.dp)
                                                            )
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = chip,
                                                            color = AppColors.text,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(14.dp))
                                        }

                                        if (anime?.genres?.isNotEmpty() == true) {
                                            Text(
                                                text = "Genres",
                                                color = AppColors.text,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                anime.genres.forEach { genre ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(AppColors.surface)
                                                            .border(
                                                                width = 1.dp,
                                                                color = AppColors.border,
                                                                shape = RoundedCornerShape(6.dp)
                                                            )
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = genre,
                                                            color = AppColors.textMuted,
                                                            fontSize = 12.sp,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(14.dp))
                                        }

                                        if (animeDescription.isNotBlank()) {
                                            Text(
                                                text = "Synopsis",
                                                color = AppColors.text,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = animeDescription,
                                                color = AppColors.textMuted,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        } else {
                                            Text(
                                                text = "No synopsis available.",
                                                color = Color.Gray,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        }

                                        Spacer(Modifier.height(20.dp))
                                    }
                                }
                            }
                        }

                        if (showWatchlistSheet) {
                            WatchlistBottomSheet(
                                currentFolder = episodeData?.folder,
                                onSelect = { folder ->
                                    showWatchlistSheet = false
                                    viewModel.updateWatchlistStatus(folder)
                                },
                                onDismiss = { showWatchlistSheet = false }
                            )
                        }
                    }

                    "episodes" -> {
                        val episodes = uiState.episodeData?.episodes ?: emptyList()
                        val totalEpisodes = episodes.size
                        val episodesPerPage = 100
                        val pageGroups = remember(totalEpisodes) {
                            if (totalEpisodes > 0) (1..totalEpisodes step episodesPerPage).toList() else listOf(1)
                        }
                        val currentEpisodePageStart = remember(totalEpisodes, uiState.currentEpisodeNumber) {
                            if (totalEpisodes > 0) (((uiState.currentEpisodeNumber - 1) / episodesPerPage) * episodesPerPage) + 1 else 1
                        }
                        var searchQuery by remember(episodes) { mutableStateOf("") }
                        var isAscending by remember(episodes) { mutableStateOf(true) }
                        var currentPageStart by remember(totalEpisodes, uiState.currentEpisodeNumber) {
                            mutableStateOf(currentEpisodePageStart)
                        }
                        var isPageDropdownExpanded by remember { mutableStateOf(false) }
                        var hasScrolled by remember { mutableStateOf(false) }
                        var pageDropdownAnchorSize by remember { mutableStateOf(IntSize.Zero) }
                        var pageDropdownAnchorOffset by remember { mutableStateOf(IntOffset.Zero) }
                        val listState = rememberLazyListState()
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val headerItemCount = 1

                        val visibleEpisodes = remember(episodes, searchQuery, isAscending, currentPageStart) {
                            val baseEpisodes = if (searchQuery.isBlank()) {
                                episodes.filter { episode ->
                                    episode.number in currentPageStart until (currentPageStart + episodesPerPage)
                                }
                            } else {
                                episodes.filter { episode ->
                                    val episodeTitle = episode.titles?.firstOrNull().orEmpty()
                                    episode.number.toString().contains(searchQuery) ||
                                            episodeTitle.contains(searchQuery, ignoreCase = true)
                                }
                            }
                            if (isAscending) baseEpisodes.sortedBy { it.number } else baseEpisodes.sortedByDescending { it.number }
                        }

                        LaunchedEffect(searchQuery, currentEpisodePageStart) {
                            if (searchQuery.isBlank() && currentPageStart != currentEpisodePageStart) {
                                currentPageStart = currentEpisodePageStart
                            }
                        }

                        LaunchedEffect(searchQuery, isAscending, currentPageStart) {
                            if (visibleEpisodes.isNotEmpty() || totalEpisodes > 0) {
                                listState.scrollToItem(0)
                            }
                        }

                        LaunchedEffect(
                            uiState.activeSidePanel,
                            searchQuery,
                            isAscending,
                            currentPageStart,
                            isPageDropdownExpanded
                        ) {
                            if (uiState.activeSidePanel == "episodes" && !hasScrolled) {
                                val currentEpIndex =
                                    visibleEpisodes.indexOfFirst { it.number == uiState.currentEpisodeNumber }
                                if (currentEpIndex >= 0) {
                                    listState.animateScrollToItem(currentEpIndex + headerItemCount)
                                }
                                hasScrolled = true
                            }
                        }

                        LaunchedEffect(uiState.activeSidePanel) {
                            if (uiState.activeSidePanel != "episodes") {
                                hasScrolled = false
                                isPageDropdownExpanded = false
                            }
                        }

                        var outerBoxOffset by remember { mutableStateOf(IntOffset.Zero) }
                        Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                            val pos = coordinates.positionInRoot()
                            outerBoxOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                        }) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (totalEpisodes == 0) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxSize()
                                                .padding(top = 48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                if (uiState.isLoading || uiState.episodeData == null) "Loading episodes..." else "No episodes found",
                                                color = Color.Gray,
                                            )
                                        }
                                    }
                                } else {
                                    item {
                                        Box(Modifier.fillMaxWidth()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .onGloballyPositioned { coordinates ->
                                                            pageDropdownAnchorSize = coordinates.size
                                                            val posRoot = coordinates.positionInRoot()
                                                            pageDropdownAnchorOffset = IntOffset(
                                                                (posRoot.x - outerBoxOffset.x).toInt(),
                                                                (posRoot.y - outerBoxOffset.y).toInt()
                                                            )
                                                        }
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .border(
                                                            1.dp,
                                                            AppColors.border,
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                        .background(AppColors.surface)
                                                        .clickable(enabled = searchQuery.isBlank() && pageGroups.size > 1) {
                                                            isPageDropdownExpanded = !isPageDropdownExpanded
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                                ) {
                                                    Row(
                                                        Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            val end =
                                                                (currentPageStart + episodesPerPage - 1).coerceAtMost(
                                                                    totalEpisodes
                                                                )
                                                            Text(
                                                                text = if (searchQuery.isBlank()) "Episodes $currentPageStart - $end" else "Search results",
                                                                color = Color.White,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Spacer(Modifier.height(4.dp))
                                                            Text(
                                                                text = if (searchQuery.isBlank()) "$totalEpisodes total episodes" else "${visibleEpisodes.size} matching episodes",
                                                                color = Color.Gray,
                                                                fontSize = 12.sp
                                                            )
                                                        }

                                                        if (searchQuery.isBlank() && pageGroups.size > 1) {
                                                            Icon(
                                                                imageVector = if (isPageDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                                contentDescription = "Select range",
                                                                tint = Color.White
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = searchQuery,
                                                        onValueChange = {
                                                            searchQuery = it
                                                            if (it.isNotBlank()) {
                                                                isPageDropdownExpanded = false
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f).height(50.dp),
                                                        placeholder = {
                                                            Text(
                                                                "Search episode...",
                                                                color = Color.White.copy(alpha = 0.4f),
                                                                fontSize = 14.sp
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Default.Search,
                                                                null,
                                                                tint = Color.White.copy(alpha = 0.4f),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        },
                                                        trailingIcon = {
                                                            if (searchQuery.isNotEmpty()) {
                                                                IconButton(onClick = { searchQuery = "" }) {
                                                                    Icon(
                                                                        Icons.Default.Clear,
                                                                        null,
                                                                        tint = Color.White.copy(alpha = 0.5f),
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                                            focusedContainerColor = Color.Black,
                                                            unfocusedContainerColor = Color.Black,
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White,
                                                            cursorColor = Color.White
                                                        ),
                                                        shape = RoundedCornerShape(10.dp),
                                                        singleLine = true
                                                    )

                                                    IconButton(
                                                        onClick = { isAscending = !isAscending },
                                                        modifier = Modifier
                                                            .size(50.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .border(
                                                                1.dp,
                                                                AppColors.border,
                                                                RoundedCornerShape(10.dp)
                                                            )
                                                            .background(AppColors.surface)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                            contentDescription = if (isAscending) "Ascending" else "Descending",
                                                            tint = Color.White
                                                        )
                                                    }
                                                }
                                            }

                                        }
                                    }
                                    if (visibleEpisodes.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillParentMaxWidth()
                                                    .padding(top = 48.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No matching episodes", color = Color.Gray)
                                            }
                                        }
                                    } else {
                                        items(visibleEpisodes, key = { it.number }) { episode ->
                                            val isSelected = episode.number == uiState.currentEpisodeNumber
                                            val thumbnail = uiState.thumbnails[episode.number.toString()]

                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) AppColors.surfaceVariant
                                                        else AppColors.surface
                                                    )
                                                    .then(
                                                        if (isSelected) Modifier.border(
                                                            1.dp,
                                                            AppColors.border,
                                                            RoundedCornerShape(10.dp)
                                                        ) else Modifier
                                                    )
                                                    .clickable { viewModel.onEpisodeSelected(episode.number) }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (thumbnail != null) {
                                                    AsyncImage(
                                                        model = thumbnail,
                                                        contentDescription = "Episode ${episode.number} Thumbnail",
                                                        modifier = Modifier
                                                            .width(96.dp)
                                                            .aspectRatio(16f / 9f)
                                                            .clip(RoundedCornerShape(6.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    // Stylish gradient placeholder with episode number
                                                    Box(
                                                        modifier = Modifier
                                                            .width(96.dp)
                                                            .aspectRatio(16f / 9f)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                Brush.linearGradient(
                                                                    listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                                                                )
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(
                                                                text = "${episode.number}",
                                                                color = Color.White.copy(alpha = 0.5f),
                                                                fontSize = 20.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.width(12.dp))

                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        "Episode ${episode.number}",
                                                        color = if (isSelected) AppColors.text else AppColors.textMuted,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 14.sp
                                                    )
                                                    val title = episode.titles?.firstOrNull()
                                                    if (!title.isNullOrBlank()) {
                                                        Spacer(Modifier.height(3.dp))
                                                        Text(
                                                            title,
                                                            color = if (isSelected) AppColors.textMuted else AppColors.textDim,
                                                            fontSize = 12.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                if (isSelected) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Box(
                                                        Modifier
                                                            .size(28.dp)
                                                            .background(
                                                                AppColors.accent.copy(alpha = 0.15f),
                                                                CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.PlayArrow,
                                                            null,
                                                            tint = AppColors.accent,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } // end LazyColumn

                            // Floating dropdown — sibling of LazyColumn so it draws on top of episode list
                            if (searchQuery.isBlank() && isPageDropdownExpanded && pageGroups.size > 1) {
                                val pageDropdownVisibleItems = 4
                                val pageDropdownItemHeight = 56.dp

                                LazyColumn(
                                    modifier = Modifier
                                        .zIndex(10f)
                                        .offset {
                                            IntOffset(
                                                x = pageDropdownAnchorOffset.x,
                                                y = pageDropdownAnchorOffset.y + pageDropdownAnchorSize.height + with(
                                                    density
                                                ) { 4.dp.roundToPx() }
                                            )
                                        }
                                        .width(with(density) { pageDropdownAnchorSize.width.toDp() })
                                        .heightIn(max = pageDropdownItemHeight * pageDropdownVisibleItems)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppColors.surface),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    items(pageGroups, key = { it }) { start ->
                                        val end = (start + episodesPerPage - 1).coerceAtMost(totalEpisodes)
                                        val isSelected = start == currentPageStart
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = pageDropdownItemHeight)
                                                .clickable {
                                                    currentPageStart = start
                                                    isPageDropdownExpanded = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Episodes $start - $end",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } // end outer Box
                    }

                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select an option", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidWatchPageLayout(
    uiState: WatchUiState,
    viewModel: WatchViewModel,
    animeId: String,
    offlinePath: String?,
    isPremiumUser: Boolean,
    allowLandscapeTabs: Boolean,
    onFullscreenToggle: () -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit,
) {
    var commentsSheetOpen by remember { mutableStateOf(false) }
    var downloadSheetOpen by remember { mutableStateOf(false) }
    val playerKey = "$animeId-${uiState.currentEpisodeNumber}-${offlinePath ?: "online"}"
    val activeWatchLang = uiState.targetLang ?: if (uiState.defaultLang) "dub" else "sub"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val useLandscapeTabs = !uiState.isFullscreen &&
                uiState.offlinePath == null &&
                allowLandscapeTabs &&
                maxWidth > maxHeight

        // Keep the player at a fixed position in the composition tree so it is never
        // unmounted/remounted when fullscreen or orientation changes (which would destroy
        // and recreate the SurfaceView, causing mpv to lose its surface and crash with
        // "Missing surface pointer"). This mirrors the approach used by the W2G player.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!uiState.isFullscreen && useLandscapeTabs) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    } else Modifier
                )
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .weight(if (useLandscapeTabs) 0.62f else 1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                uiState.isFullscreen -> Modifier.weight(1f)
                                useLandscapeTabs -> Modifier.fillMaxSize()
                                else -> Modifier.aspectRatio(16f / 9f)
                            }
                        )
                ) {
                    key(playerKey) {
                        WatchVideoPlayer(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            isPremiumUser = isPremiumUser,
                            onFullscreenToggle = onFullscreenToggle,
                            onBack = onBack,
                            onExit = onExit,
                            onInfoClick = {},
                            onEpisodesClick = {
                                if (uiState.isFullscreen) viewModel.setFullscreen(false)
                            },
                            onCommentsClick = { commentsSheetOpen = true },
                            showPlayerLibraryActions = uiState.isFullscreen,
                            showFullscreenButton = true,
                            compactControls = !uiState.isFullscreen,
                        )
                    }
                }

                if (!uiState.isFullscreen && !useLandscapeTabs && uiState.offlinePath == null) {
                    if (uiState.servers.isNotEmpty()) {
                        InlineServerSelector(
                            servers = uiState.servers,
                            currentServerId = uiState.currentServer,
                            currentLang = activeWatchLang,
                            onLangChange = { viewModel.switchAudioLang(it) },
                            onServerSelected = { serverId -> viewModel.switchServer(serverId) },
                        )
                    }

                    EpisodeListContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onCommentsClick = { commentsSheetOpen = true },
                        onDownloadClick = { downloadSheetOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            if (useLandscapeTabs) {
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .background(AppColors.background)
                ) {
                    if (uiState.servers.isNotEmpty()) {
                        InlineServerSelector(
                            servers = uiState.servers,
                            currentServerId = uiState.currentServer,
                            currentLang = activeWatchLang,
                            onLangChange = { viewModel.switchAudioLang(it) },
                            onServerSelected = { serverId -> viewModel.switchServer(serverId) },
                        )
                    }

                    EpisodeListContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onCommentsClick = { commentsSheetOpen = true },
                        onDownloadClick = { downloadSheetOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }

        WatchCommentsBottomSheet(
            visible = commentsSheetOpen,
            animeId = animeId,
            episodeNumber = uiState.currentEpisodeNumber,
            onDismiss = { commentsSheetOpen = false },
        )
        WatchEpisodeDownloadSheet(
            visible = downloadSheetOpen,
            uiState = uiState,
            isPremiumUser = isPremiumUser,
            onDismiss = { downloadSheetOpen = false },
        )
    }
}

@Composable
private fun InlineServerSelector(
    servers: List<ServerInfo>,
    currentServerId: String,
    currentLang: String,
    onLangChange: (String) -> Unit,
    onServerSelected: (String) -> Unit,
) {
    val subServers = servers.filter { !to.kuudere.anisuge.extensions.isDubSelectableServerId(it.id) }
    val dubServers = servers.filter { to.kuudere.anisuge.extensions.isDubSelectableServerId(it.id) }
    val activeLang = when (currentLang.lowercase()) {
        "dub" -> "dub"
        else -> "sub"
    }
    val visibleServers = if (activeLang == "dub") dubServers else subServers
    var langMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        if (visibleServers.isEmpty() && subServers.isEmpty() && dubServers.isEmpty()) return@Column

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(start = 12.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF333333))
                        .clickable { langMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = activeLang.uppercase(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Audio language",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = langMenuExpanded,
                    onDismissRequest = { langMenuExpanded = false },
                    modifier = Modifier.background(AppColors.surface),
                ) {
                    listOf("sub" to "Sub", "dub" to "Dub").forEach { (key, label) ->
                        val selected = activeLang == key
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        label,
                                        color = Color.White,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AppColors.accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                langMenuExpanded = false
                                if (!selected) onLangChange(key)
                            },
                        )
                    }
                }
            }

            if (visibleServers.isNotEmpty()) {
                ServerChipRow(
                    servers = visibleServers,
                    currentServerId = currentServerId,
                    onServerSelected = onServerSelected,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = "No ${activeLang.uppercase()} servers",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerChipRow(
    servers: List<ServerInfo>,
    currentServerId: String,
    onServerSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            servers.forEach { server ->
                val isSelected = server.id.equals(currentServerId, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF8B1520) else Color(0xFF333333))
                        .clickable { onServerSelected(server.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = server.label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        if (scrollState.maxValue > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF1A1A1A)),
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchCommentsBottomSheet(
    visible: Boolean,
    animeId: String,
    episodeNumber: Int,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
        ) {
            val sheetHeight = (maxHeight * 0.88f).coerceAtMost(520.dp)
            WatchCommentsPanel(
                animeId = animeId,
                episodeNumber = episodeNumber,
                onClose = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
            )
        }
    }
}

@Composable
private fun WatchCommentsPanel(
    animeId: String,
    episodeNumber: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fastUserId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        fastUserId = AppComponent.sessionStore.get()?.let { "me" }
    }

    val userProfile by produceState<to.kuudere.anisuge.data.models.UserProfile?>(null) {
        val result = AppComponent.authService.checkSession()
        value = if (result is SessionCheckResult.Valid) result.user else null
    }

    Box(modifier) {
        CommentsSection(
            animeId = animeId,
            episodeNumber = episodeNumber,
            userId = userProfile?.effectiveId ?: fastUserId,
            username = userProfile?.username,
            userPfp = userProfile?.avatar,
            userFrameUrl = userProfile?.equippedFrameUrl,
            userOuterFrameUrl = userProfile?.equippedOuterFrameUrl,
            onClose = onClose,
        )
    }
}

@Composable
private fun WatchEpisodeActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val activeProgress = progress?.takeIf { it in 0f..1f && it < 1f }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(AppColors.surface),
        )
        activeProgress?.let { fraction ->
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(AppColors.accent.copy(alpha = 0.28f))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(AppColors.accent.copy(alpha = 0.42f)),
            )
        }
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchEpisodeDownloadSheet(
    visible: Boolean,
    uiState: WatchUiState,
    isPremiumUser: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val stream = remember(uiState.currentServer, uiState.currentQuality, uiState.availableQualities, uiState.streamingData) {
        resolveWatchStreamForDownload(uiState)
    }
    val serverLabel = remember(uiState.currentServer, uiState.servers) {
        uiState.servers.firstOrNull { it.id.equals(uiState.currentServer, ignoreCase = true) }?.label
            ?: uiState.currentServer.ifBlank { "Current server" }
    }
    val audioMode = uiState.targetLang ?: if (uiState.defaultLang) "dub" else "sub"
    val subtitleOptions = remember(uiState.availableSubtitles) {
        uiState.availableSubtitles
            .mapNotNull { sub -> (sub.title ?: sub.resolvedLang)?.takeIf { it.isNotBlank() } }
            .distinct()
    }
    var selectedSubtitleLabels by remember(stream, subtitleOptions, uiState.currentSubtitleUrl) {
        mutableStateOf(
            buildSet {
                uiState.availableSubtitles.forEach { sub ->
                    val label = sub.title ?: sub.resolvedLang
                    if (!label.isNullOrBlank() && sub.url == uiState.currentSubtitleUrl) add(label)
                }
                if (isEmpty()) {
                    when {
                        "English" in subtitleOptions -> add("English")
                        subtitleOptions.isNotEmpty() -> add(subtitleOptions.first())
                    }
                }
            },
        )
    }
    var shouldRequestStoragePermission by remember { mutableStateOf(false) }
    var shouldRequestNotificationPermission by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayTitle = uiState.episodeData?.title?.resolveDisplayTitle()
        ?: uiState.offlineTitle
        ?: "Anime"

    fun startDownloadNow() {
        val resolved = stream ?: return
        val animeId = uiState.animeId.ifBlank { uiState.episodeData?.animeId.orEmpty() }
        val anilistId = uiState.episodeData?.anilistId ?: uiState.episodeData?.anime?.anilistId ?: 0
        to.kuudere.anisuge.utils.DownloadManager.startDownload(
            animeId = animeId,
            anilistId = anilistId,
            episodeNumber = uiState.currentEpisodeNumber,
            title = displayTitle,
            coverImage = uiState.episodeData?.coverImage?.bestUrl
                ?: uiState.episodeData?.anime?.coverImage?.bestUrl,
            server = uiState.currentServer,
            subtitleLabels = selectedSubtitleLabels.toList(),
            audioLang = if (audioMode == "dub") "eng" else "jpn",
            downloadFonts = true,
            headers = resolved.headers,
            m3u8Url = resolved.url,
            preferBatchDub = audioMode == "dub",
            useParallelSegments = isPremiumUser,
        )
        onDismiss()
    }

    if (shouldRequestStoragePermission) {
        to.kuudere.anisuge.utils.RequestStoragePermission { granted ->
            shouldRequestStoragePermission = false
            if (granted) shouldRequestNotificationPermission = true
        }
    }
    if (shouldRequestNotificationPermission) {
        to.kuudere.anisuge.utils.RequestNotificationPermission {
            shouldRequestNotificationPermission = false
            startDownloadNow()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Download episode ${uiState.currentEpisodeNumber}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = displayTitle,
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Server: $serverLabel", color = Color.White, fontSize = 13.sp)
                Text("Audio: ${audioMode.uppercase()}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Text("Quality: ${uiState.currentQuality}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }

            if (subtitleOptions.isNotEmpty()) {
                Text("Subtitles", color = Color.Gray, fontSize = 13.sp)
                subtitleOptions.forEach { label ->
                    val checked = label in selectedSubtitleLabels
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSubtitleLabels = if (checked) {
                                    selectedSubtitleLabels - label
                                } else {
                                    selectedSubtitleLabels + label
                                }
                            },
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected ->
                                selectedSubtitleLabels = if (selected) {
                                    selectedSubtitleLabels + label
                                } else {
                                    selectedSubtitleLabels - label
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppColors.accent,
                                uncheckedColor = AppColors.border,
                                checkmarkColor = AppColors.onAccent,
                            ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            if (stream == null) {
                Text(
                    text = "Stream is still loading. Wait for playback to start, then try again.",
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp,
                )
            }

            Button(
                onClick = {
                    if (stream == null) return@Button
                    when {
                        !to.kuudere.anisuge.utils.hasStoragePermission() -> {
                            shouldRequestStoragePermission = true
                        }
                        !to.kuudere.anisuge.utils.hasNotificationPermission() -> {
                            shouldRequestNotificationPermission = true
                        }
                        else -> startDownloadNow()
                    }
                },
                enabled = stream != null && uiState.currentServer.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Download with current server",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

private data class WatchDownloadStream(
    val url: String,
    val headers: Map<String, String>,
)

private fun resolveWatchStreamForDownload(uiState: WatchUiState): WatchDownloadStream? {
    val rawUrl = uiState.availableQualities
        .find { it.first == uiState.currentQuality }
        ?.second
        ?: uiState.availableQualities.firstOrNull()?.second
        ?: return null
    if (rawUrl.isBlank()) return null

    val currentSource = uiState.streamingData?.sources?.find { it.url == rawUrl }
    val headers = currentSource?.headers ?: uiState.streamingData?.headers ?: emptyMap()
    val resolved = resolveBridgeProxyStreamUrl(rawUrl, headers)
    return WatchDownloadStream(url = resolved.url, headers = resolved.headers)
}

@Composable
private fun EpisodeListContent(
    uiState: WatchUiState,
    viewModel: WatchViewModel,
    onCommentsClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val episodes = uiState.episodeData?.episodes ?: emptyList()
    val totalEpisodes = episodes.size
    val watchedEpisode = uiState.episodeData?.progress?.episodeId
        ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
    val currentProgressSeconds = uiState.episodeData?.progress?.currentTime
    val episodesPerPage = 100
    val pageGroups = remember(totalEpisodes) {
        if (totalEpisodes > 0) (1..totalEpisodes step episodesPerPage).toList() else listOf(1)
    }
    val currentEpisodePageStart = remember(totalEpisodes, uiState.currentEpisodeNumber) {
        if (totalEpisodes > 0) (((uiState.currentEpisodeNumber - 1) / episodesPerPage) * episodesPerPage) + 1 else 1
    }
    var searchQuery by remember(episodes) { mutableStateOf("") }
    var isAscending by remember(episodes) { mutableStateOf(true) }
    var currentPageStart by remember(totalEpisodes, uiState.currentEpisodeNumber) {
        mutableStateOf(currentEpisodePageStart)
    }
    var isPageDropdownExpanded by remember { mutableStateOf(false) }
    val downloadTasks by DownloadManager.tasks.collectAsState()
    val currentDownloadTask = remember(
        downloadTasks,
        uiState.animeId,
        uiState.currentEpisodeNumber,
    ) {
        downloadTasks.find {
            it.animeId == uiState.animeId && it.episodeNumber == uiState.currentEpisodeNumber
        }
    }
    val downloadButtonLabel = remember(currentDownloadTask) {
        when {
            currentDownloadTask == null -> "Download"
            currentDownloadTask.status == "Finished" -> "Downloaded"
            currentDownloadTask.isPaused -> {
                val pct = (currentDownloadTask.progress * 100).toInt().coerceIn(0, 100)
                "Paused $pct%"
            }
            currentDownloadTask.status.startsWith("Failed") -> "Download"
            else -> {
                val pct = (currentDownloadTask.progress * 100).toInt().coerceIn(0, 100)
                "$pct%"
            }
        }
    }
    val downloadButtonProgress = remember(currentDownloadTask) {
        currentDownloadTask?.takeIf {
            !it.isPaused &&
                it.status != "Finished" &&
                !it.status.startsWith("Failed")
        }?.progress
    }
    var hasScrolled by remember { mutableStateOf(false) }
    var pageDropdownAnchorSize by remember { mutableStateOf(IntSize.Zero) }
    var pageDropdownAnchorOffset by remember { mutableStateOf(IntOffset.Zero) }
    var outerBoxOffset by remember { mutableStateOf(IntOffset.Zero) }
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val headerItemCount = 1

    val visibleEpisodes = remember(episodes, searchQuery, isAscending, currentPageStart) {
        val baseEpisodes = if (searchQuery.isBlank()) {
            episodes.filter { episode ->
                episode.number in currentPageStart until (currentPageStart + episodesPerPage)
            }
        } else {
            episodes.filter { episode ->
                val episodeTitle = episode.titles?.firstOrNull().orEmpty()
                episode.number.toString().contains(searchQuery) ||
                        episodeTitle.contains(searchQuery, ignoreCase = true)
            }
        }
        if (isAscending) baseEpisodes.sortedBy { it.number } else baseEpisodes.sortedByDescending { it.number }
    }

    LaunchedEffect(searchQuery, currentEpisodePageStart) {
        if (searchQuery.isBlank() && currentPageStart != currentEpisodePageStart) {
            currentPageStart = currentEpisodePageStart
        }
    }

    LaunchedEffect(searchQuery, isAscending, currentPageStart) {
        if (visibleEpisodes.isNotEmpty() || totalEpisodes > 0) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(visibleEpisodes, uiState.currentEpisodeNumber) {
        if (!hasScrolled) {
            val currentEpIndex = visibleEpisodes.indexOfFirst { it.number == uiState.currentEpisodeNumber }
            if (currentEpIndex >= 0) {
                listState.animateScrollToItem(currentEpIndex + headerItemCount)
            }
            hasScrolled = true
        }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        val pos = coordinates.positionInRoot()
        outerBoxOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
    }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (totalEpisodes == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (uiState.isLoading || uiState.episodeData == null) "Loading episodes..." else "No episodes found",
                            color = Color.Gray,
                        )
                    }
                }
            } else {
                item {
                    EpisodeListHeader(
                        searchQuery = searchQuery,
                        onSearchQueryChange = {
                            searchQuery = it
                            if (it.isNotBlank()) isPageDropdownExpanded = false
                        },
                        isAscending = isAscending,
                        onSortClick = { isAscending = !isAscending },
                        pageGroups = pageGroups,
                        currentPageStart = currentPageStart,
                        episodesPerPage = episodesPerPage,
                        totalEpisodes = totalEpisodes,
                        visibleCount = visibleEpisodes.size,
                        isPageDropdownExpanded = isPageDropdownExpanded,
                        onPageDropdownClick = { isPageDropdownExpanded = !isPageDropdownExpanded },
                        onCommentsClick = onCommentsClick,
                        onDownloadClick = onDownloadClick,
                        downloadProgress = downloadButtonProgress,
                        downloadButtonLabel = downloadButtonLabel,
                        onAnchorPositioned = { size, offset ->
                            pageDropdownAnchorSize = size
                            pageDropdownAnchorOffset = IntOffset(
                                (offset.x - outerBoxOffset.x).toInt(),
                                (offset.y - outerBoxOffset.y).toInt(),
                            )
                        },
                    )
                }

                if (visibleEpisodes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No matching episodes", color = Color.Gray)
                        }
                    }
                } else {
                    items(visibleEpisodes, key = { it.number }) { episode ->
                        EpisodeListRow(
                            episode = episode,
                            isSelected = episode.number == uiState.currentEpisodeNumber,
                            watchedEpisode = watchedEpisode,
                            currentProgressSeconds = currentProgressSeconds,
                            episodeProgress = uiState.episodeProgress[episode.number],
                            onClick = { viewModel.onEpisodeSelected(episode.number) },
                        )
                    }
                }
            }
        }

        if (searchQuery.isBlank() && isPageDropdownExpanded && pageGroups.size > 1) {
            val pageDropdownVisibleItems = 4
            val pageDropdownItemHeight = 56.dp

            LazyColumn(
                modifier = Modifier
                    .zIndex(10f)
                    .offset {
                        IntOffset(
                            x = pageDropdownAnchorOffset.x,
                            y = pageDropdownAnchorOffset.y + pageDropdownAnchorSize.height + with(density) { 4.dp.roundToPx() },
                        )
                    }
                    .width(with(density) { pageDropdownAnchorSize.width.toDp() })
                    .heightIn(max = pageDropdownItemHeight * pageDropdownVisibleItems)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.surface),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(pageGroups, key = { it }) { start ->
                    val end = (start + episodesPerPage - 1).coerceAtMost(totalEpisodes)
                    val isSelected = start == currentPageStart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = pageDropdownItemHeight)
                            .clickable {
                                currentPageStart = start
                                isPageDropdownExpanded = false
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Episodes $start - $end",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isAscending: Boolean,
    onSortClick: () -> Unit,
    pageGroups: List<Int>,
    currentPageStart: Int,
    episodesPerPage: Int,
    totalEpisodes: Int,
    visibleCount: Int,
    isPageDropdownExpanded: Boolean,
    onPageDropdownClick: () -> Unit,
    onCommentsClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    downloadProgress: Float? = null,
    downloadButtonLabel: String = "Download",
    onAnchorPositioned: (IntSize, androidx.compose.ui.geometry.Offset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onCommentsClick != null || onDownloadClick != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onCommentsClick?.let { onClick ->
                    WatchEpisodeActionButton(
                        label = "Comments",
                        icon = Icons.AutoMirrored.Filled.Comment,
                        modifier = Modifier.weight(1f),
                        onClick = onClick,
                    )
                }
                onDownloadClick?.let { onClick ->
                    WatchEpisodeActionButton(
                        label = downloadButtonLabel,
                        icon = Icons.Default.Download,
                        modifier = Modifier.weight(1f),
                        progress = downloadProgress,
                        onClick = onClick,
                    )
                }
            }
        }

        val rangeEnd = (currentPageStart + episodesPerPage - 1).coerceAtMost(totalEpisodes)
        val canPickRange = searchQuery.isBlank() && pageGroups.size > 1
        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onAnchorPositioned(coordinates.size, coordinates.positionInRoot())
                }
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, AppColors.border.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .background(AppColors.surface.copy(alpha = 0.85f))
                .clickable(enabled = canPickRange) { onPageDropdownClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        searchQuery.isNotBlank() -> "$visibleCount of $totalEpisodes episodes"
                        totalEpisodes <= episodesPerPage -> "$totalEpisodes episodes"
                        else -> "Episodes $currentPageStart–$rangeEnd · $totalEpisodes total"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (canPickRange) {
                    Icon(
                        imageVector = if (isPageDropdownExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = "Select episode range",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f).height(50.dp),
                placeholder = {
                    Text(
                        "Search episode...",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White.copy(alpha = 0.2f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
            )

            IconButton(
                onClick = onSortClick,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.border, RoundedCornerShape(10.dp))
                    .background(AppColors.surface),
            ) {
                Icon(
                    imageVector = if (isAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isAscending) "Ascending" else "Descending",
                    tint = AppColors.text,
                )
            }
        }
    }
}

@Composable
private fun EpisodeListRow(
    episode: to.kuudere.anisuge.data.models.EpisodeItem,
    isSelected: Boolean,
    watchedEpisode: Int?,
    currentProgressSeconds: Double?,
    episodeProgress: WatchEpisodeProgress?,
    onClick: () -> Unit,
) {
    val progressFraction = if (episodeProgress != null && episodeProgress.duration > 0) {
        (episodeProgress.currentTime / episodeProgress.duration).coerceIn(0.0, 1.0)
    } else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF171717) else Color.White.copy(alpha = 0.05f))
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                }
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = episode.number.toString(),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val title =
                    episode.title ?: episode.titles?.filterNotNull()?.firstOrNull() ?: "Episode ${episode.number}"
                Text(
                    title,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    when {
                        episodeProgress != null && progressFraction >= 0.9 -> WatchProgressBadge("WATCHED")
                        episodeProgress != null && progressFraction > 0.0 -> WatchProgressBadge("IN PROGRESS")
                        watchedEpisode != null && episode.number < watchedEpisode -> WatchProgressBadge("WATCHED")
                        watchedEpisode != null && episode.number == watchedEpisode -> {
                            WatchProgressBadge(
                                if ((currentProgressSeconds ?: 0.0) > 0.0) "IN PROGRESS" else "LAST WATCHED"
                            )
                        }
                    }
                }

                val meta = buildList {
                    if (episode.filler == true) add("FILLER")
                    if (episode.recap == true) add("RECAP")
                    episode.ago?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" • "),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isSelected) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        if (progressFraction > 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.1f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.toFloat())
                        .height(3.dp)
                        .background(Color(0xFFE50914)),
                )
            }
        }
    }
}

@Composable
private fun WatchProgressBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun WatchVideoPlayer(
    uiState: WatchUiState,
    viewModel: WatchViewModel,
    modifier: Modifier = Modifier,
    isPremiumUser: Boolean = false,
    onFullscreenToggle: () -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit = {},
    onInfoClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onCommentsClick: (() -> Unit)? = null,
    showPlayerLibraryActions: Boolean = true,
    showFullscreenButton: Boolean = isDesktopPlatform || isAndroidPlatform,
    compactControls: Boolean = false,
) {
    var showWatchlistSheet by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (uiState.isLoadingVideo) {
        Box(modifier = modifier.background(Color.Black)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // High-Performance Dual-Circle Loader (Consistency)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                    val infiniteTransition = rememberInfiniteTransition()

                    val rotateCW by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing)
                        ),
                        label = "OuterRotate"
                    )
                    val rotateCCW by infiniteTransition.animateFloat(
                        initialValue = 360f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing)
                        ),
                        label = "InnerRotate"
                    )

                    // Outer Circle
                    CircularProgressIndicator(
                        progress = { 0.75f },
                        modifier = Modifier.size(60.dp).graphicsLayer { rotationZ = rotateCW },
                        color = Color.White,
                        strokeWidth = 2.dp,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeCap = StrokeCap.Round
                    )

                    // Inner Circle
                    CircularProgressIndicator(
                        progress = { 0.6f },
                        modifier = Modifier.size(35.dp).graphicsLayer { rotationZ = rotateCCW },
                        color = Color.White.copy(alpha = 0.6f),
                        strokeWidth = 2.dp,
                        trackColor = Color.White.copy(alpha = 0.05f),
                        strokeCap = StrokeCap.Round
                    )
                }

                uiState.loadingMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = msg.uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ESTABLISHING SECURE STREAMING TUNNEL",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.alpha(0.5f)
                    )
                }
            }

            // Always allow back while loading video
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            to.kuudere.anisuge.platform.DraggableWindowArea(
                modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.TopStart)
            ) { }

            IconButton(
                onClick = { viewModel.toggleSettingsOverlay() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 16.dp, end = 56.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            to.kuudere.anisuge.platform.WindowManagementButtons(
                onClose = onExit,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )

            AnimatedWatchSettingsOverlay(visible = uiState.showSettingsOverlay) {
                val servers = uiState.servers
                SettingsOverlay(
                    uiState = uiState,
                    servers = servers,
                    onDismiss = { viewModel.toggleSettingsOverlay() },
                    onQualitySelected = { viewModel.setQuality(it) },
                    onSubtitleSelected = { url ->
                        val selectedSub = uiState.availableSubtitles.firstOrNull { it.url == url }
                        val lang = selectedSub?.title ?: selectedSub?.resolvedLang
                        viewModel.setSubtitle(url, lang)
                    },
                    onServerSelected = { serverLabel ->
                        val currentSub = uiState.availableSubtitles.firstOrNull { it.url == uiState.currentSubtitleUrl }
                        viewModel.changeServerWithState(
                            newServer = serverLabel,
                            position = uiState.savedWatchPosition,
                            targetAudioLang = uiState.targetLang,
                            targetSubtitleLang = currentSub?.title ?: currentSub?.resolvedLang,
                            targetSubtitleLangCode = currentSub?.language ?: currentSub?.lang,
                        )
                    },
                    onSpeedSelected = { viewModel.setSpeed(it) },
                    onCycleAudio = { },
                    audioTracks = emptyList(),
                    selectedAudioTrack = null,
                    onAudioTrackSelected = { },
                    subtitleTracks = emptyList(),
                    selectedSubtitleTrack = null,
                    onSubtitleTrackSelected = { },
                    onSubtitleSizeSelected = { viewModel.setSubtitleSize(it) },
                    onWatchlistStatusSelected = { folder -> viewModel.updateWatchlistStatus(folder) },
                    onAutoPlayToggle = { viewModel.setAutoPlay(it) },
                    onAutoNextToggle = { viewModel.setAutoNext(it) },
                    onAutoSkipIntroToggle = { viewModel.setAutoSkipIntro(it) },
                    onAutoSkipOutroToggle = { viewModel.setAutoSkipOutro(it) },
                    onVideoScaleModeSelected = { viewModel.setVideoScaleMode(it) },
                    onPlayerEnhancementsChanged = viewModel::setSessionPlayerEnhancements,
                    onPlayerUtilitiesChanged = viewModel::setSessionPlayerUtilities,
                    onScreenshot = { },
                    onSleepTimerChanged = viewModel::setSleepTimer,
                    onSleepAfterEpisodeChanged = viewModel::setSleepAfterEpisode,
                )
            }
        }
    } else {
        val currentUrl = uiState.availableQualities.find { it.first == uiState.currentQuality }?.second
            ?: uiState.availableQualities.firstOrNull()?.second

        if (currentUrl != null) {
            // Get headers from the matching source in streamingData
            val currentSource = uiState.streamingData?.sources?.find { it.url == currentUrl }
            val streamHeaders = currentSource?.headers ?: uiState.streamingData?.headers
            val streamHeaderKey = remember(streamHeaders) {
                streamHeaders?.entries
                    ?.sortedBy { it.key }
                    ?.joinToString("\u0001") { "${it.key}=${it.value}" }
                    .orEmpty()
            }
            val playbackUrl = remember(currentUrl, streamHeaderKey) {
                if (shouldUseLocalStreamProxy(currentUrl)) {
                    StreamProxy.proxyUrl(currentUrl, streamHeaders)
                } else {
                    currentUrl
                }
            }

            // Desktop: We now use our custom Compose PlayerControls instead of mpv's OSC
            val useOsc = false
            // Offline videos (MKV) may have fonts embedded in the container — allow them.
            // Online streams always use API-downloaded fonts, so embedded fonts must be off.
            val useEmbeddedFonts = uiState.offlinePath != null
            val playerState = rememberVideoPlayerState(
                url = playbackUrl,
                mediaKey = "${uiState.animeId}:${uiState.currentEpisodeNumber}",
                startPosition = uiState.savedWatchPosition,
                fontsDir = uiState.currentFontsDir,
                embeddedFonts = useEmbeddedFonts,
                showControls = useOsc,
                autoPlay = uiState.autoPlay,
                speed = if (uiState.isBoostSpeedActive) 2.0 else uiState.playbackSpeed,
                subtitleSize = uiState.subtitleSize,
                headers = streamHeaders,
                enhancements = uiState.playerEnhancements,
                utilities = uiState.playerUtilities,
                screenshotAnimeTitle = uiState.episodeData?.title?.resolveDisplayTitle()
                    ?: uiState.offlineTitle
                    ?: "Anime",
                screenshotEpisodeNumber = uiState.currentEpisodeNumber,
            )

            LaunchedEffect(uiState.playerEnhancements) {
                playerState.enhancements = uiState.playerEnhancements
            }

            LaunchedEffect(uiState.playerUtilities) {
                playerState.utilities = uiState.playerUtilities
            }

            LaunchedEffect(uiState.sleepTimerDeadlineMs) {
                val deadline = uiState.sleepTimerDeadlineMs ?: return@LaunchedEffect
                val remaining = deadline - Clock.System.now().toEpochMilliseconds()
                if (remaining > 0) kotlinx.coroutines.delay(remaining)
                playerState.pauseRequested = true
                val currentAudioLabel =
                    playerState.audioTracks.firstOrNull { it.first == playerState.selectedAudioTrack }
                        ?.second?.lowercase().orEmpty()
                viewModel.flushWatchProgress(
                    playerState.position,
                    playerState.duration.takeIf { it > 0 } ?: (playerState.position + 120.0),
                    if (currentAudioLabel.contains("eng")) "dub" else "sub",
                )
                viewModel.setSleepTimer(null)
                playerState.indicatorText = "Sleep timer finished"
            }

            LaunchedEffect(playerState.screenshotResult) {
                val result = playerState.screenshotResult ?: return@LaunchedEffect
                playerState.indicatorText = result
                kotlinx.coroutines.delay(3000)
                if (playerState.indicatorText == result) playerState.indicatorText = null
                playerState.screenshotResult = null
            }

            DisposableEffect(playbackUrl, uiState.currentEpisodeNumber) {
                onDispose {
                    if (uiState.offlinePath == null && playerState.position >= 5.0) {
                        val audioLabel = playerState.audioTracks
                            .firstOrNull { it.first == playerState.selectedAudioTrack }
                            ?.second
                            ?.lowercase()
                            .orEmpty()
                        val trackLang = if (audioLabel.contains("eng")) "dub" else "sub"
                        val duration = playerState.duration.takeIf { it > 0 }
                            ?: (playerState.position + 120.0)
                        viewModel.flushWatchProgress(
                            playerState.position,
                            duration,
                            trackLang,
                        )
                    }
                    if (shouldUseLocalStreamProxy(currentUrl)) {
                        StreamProxy.release(currentUrl)
                    }
                }
            }

            val skipIntro = uiState.effectiveIntroSkip()
            val skipOutro = uiState.effectiveOutroSkip()

            androidx.compose.runtime.SideEffect {
                if (playerState.seekTarget != null) {
                    playerState.lastUserSeekAtMs = Clock.System.now().toEpochMilliseconds()
                }
            }

            LaunchedEffect(uiState.currentEpisodeNumber, playbackUrl) {
                playerState.peakPlaybackPosition = 0.0
                playerState.peakPlaybackDuration = 0.0
            }

            LaunchedEffect(playerState.position) {
                if (playerState.position > playerState.peakPlaybackPosition) {
                    playerState.peakPlaybackPosition = playerState.position
                }
            }

            LaunchedEffect(playerState.duration) {
                if (playerState.duration > playerState.peakPlaybackDuration) {
                    playerState.peakPlaybackDuration = playerState.duration
                }
            }

            LaunchedEffect(playbackUrl) {
                playerState.error = null
            }
            LaunchedEffect(uiState.isLoadingVideo) {
                if (uiState.isLoadingVideo) playerState.error = null
            }
            LaunchedEffect(playerState.position) {
                if (playerState.position > 15.0) playerState.error = null
            }
            LaunchedEffect(playerState.error) {
                val error = playerState.error ?: return@LaunchedEffect
                if (error.contains("trying another server", ignoreCase = true)) {
                    playerState.error = "Stream failed to start — choose another server in Settings"
                    viewModel.tryNextServerAfterPlaybackFailure(0.0)
                }
            }

            // Resume: [rememberVideoPlayerState] keys only on [playbackUrl], so [savedWatchPosition] can
            // arrive after the player is created (e.g. GET /watch returns after the stream is ready).
            // Seek once duration is known and playback is still at the beginning.
            LaunchedEffect(playbackUrl, uiState.currentEpisodeNumber, uiState.savedWatchPosition) {
                val resumeAt = uiState.savedWatchPosition
                if (resumeAt < 1.0) return@LaunchedEffect
                var waited = 0
                fun durationIsPlausible(): Boolean {
                    val duration = playerState.duration
                    if (duration < 0.25) return false
                    return resumeAt <= 60.0 || duration >= resumeAt + 30.0
                }
                while (waited < 6_000 && !durationIsPlausible()) {
                    kotlinx.coroutines.delay(24)
                    waited += 24
                }
                if (!durationIsPlausible()) return@LaunchedEffect
                if (playerState.position > 2.0) return@LaunchedEffect
                val safeEnd = (playerState.duration - 0.75).coerceAtLeast(0.0)
                val target = resumeAt.coerceIn(0.0, safeEnd)
                playerState.seekTarget = target
            }

            LaunchedEffect(Unit) {
                if (to.kuudere.anisuge.platform.isDesktopPlatform) {
                    focusRequester.requestFocus()
                }
            }

            // Skip button states mirroring Zen
            var skipIntroElapsed by remember(uiState.currentEpisodeNumber) { mutableStateOf(0L) }
            var skipIntroTimedOut by remember(uiState.currentEpisodeNumber) { mutableStateOf(false) }
            var skipIntroManualDismissed by remember(uiState.currentEpisodeNumber) { mutableStateOf(false) }

            var skipOutroElapsed by remember(uiState.currentEpisodeNumber) { mutableStateOf(0L) }
            var skipOutroTimedOut by remember(uiState.currentEpisodeNumber) { mutableStateOf(false) }
            var skipOutroManualDismissed by remember(uiState.currentEpisodeNumber) { mutableStateOf(false) }

            val SKIP_TIMEOUT = 10000L // 10 seconds

            // Update timers
            LaunchedEffect(playerState.position, playerState.isPlaying, playerState.isPaused) {
                if (!playerState.isPlaying || playerState.isPaused) return@LaunchedEffect

                val current = playerState.position
                val intro = skipIntro
                val outro = skipOutro

                // Intro timer logic
                if (intro != null && intro.start != null && intro.end != null && current >= intro.start && current < intro.end - 1.0) {
                    if (!skipIntroTimedOut && !skipIntroManualDismissed) {
                        // We use a fixed tick since this effect runs on position change (usually high frequency)
                        // For simplicity in KMP, we'll just use a separate timer for ticks
                    }
                } else {
                    // Reset if we leave the intro range
                    if (skipIntroTimedOut || skipIntroManualDismissed) {
                        skipIntroTimedOut = false
                        skipIntroManualDismissed = false
                        skipIntroElapsed = 0L
                    }
                }

                // Outro timer logic
                if (outro != null && outro.start != null && outro.end != null && current >= outro.start && current < outro.end - 1.0) {
                    // Similar for outro
                } else {
                    if (skipOutroTimedOut || skipOutroManualDismissed) {
                        skipOutroTimedOut = false
                        skipOutroManualDismissed = false
                        skipOutroElapsed = 0L
                    }
                }
            }

            // High-frequency tick for progress bar
            LaunchedEffect(uiState.currentEpisodeNumber) {
                while (true) {
                    kotlinx.coroutines.delay(100)
                    if (playerState.isPlaying && !playerState.isPaused) {
                        val current = playerState.position
                        val intro = skipIntro
                        val outro = skipOutro

                        if (intro != null && intro.start != null && intro.end != null && current >= intro.start && current < intro.end - 1.0) {
                            if (!skipIntroTimedOut && !skipIntroManualDismissed) {
                                skipIntroElapsed += 100
                                if (skipIntroElapsed >= SKIP_TIMEOUT) skipIntroTimedOut = true
                            }
                        } else {
                            skipIntroElapsed = 0L
                        }

                        if (outro != null && outro.start != null && outro.end != null && current >= outro.start && current < outro.end - 1.0) {
                            if (!skipOutroTimedOut && !skipOutroManualDismissed) {
                                skipOutroElapsed += 100
                                if (skipOutroElapsed >= SKIP_TIMEOUT) skipOutroTimedOut = true
                            }
                        } else {
                            skipOutroElapsed = 0L
                        }
                    }
                }
            }

            LaunchedEffect(uiState.availableSubtitles, uiState.currentSubtitleUrl, uiState.subtitlesDisabled) {
                if (uiState.availableSubtitles.isNotEmpty() && !uiState.subtitlesDisabled) {
                    playerState.allSubUrls = uiState.availableSubtitles.mapNotNull { sub ->
                        sub.url?.let { url ->
                            Triple(
                                url,
                                sub.title ?: sub.resolvedLang ?: "Subtitle",
                                url == uiState.currentSubtitleUrl,
                            )
                        }
                    }
                } else {
                    playerState.allSubUrls = null
                }
            }

            LaunchedEffect(
                uiState.currentSubtitleUrl,
                uiState.isLoadingVideo,
                uiState.subtitlesDisabled,
                uiState.availableSubtitles,
            ) {
                if (uiState.isLoadingVideo) return@LaunchedEffect
                playerState.subFileUrl = when {
                    uiState.subtitlesDisabled -> "NONE"
                    uiState.currentSubtitleUrl != null -> uiState.currentSubtitleUrl
                    uiState.availableSubtitles.isEmpty() -> "NONE"
                    else -> null
                }
                playerState.subFileName =
                    uiState.availableSubtitles.firstOrNull { it.url == uiState.currentSubtitleUrl }
                        ?.let { it.title ?: it.resolvedLang } ?: "Subtitle"
            }

            LaunchedEffect(uiState.playbackSpeed, uiState.isBoostSpeedActive) {
                playerState.playbackSpeed = if (uiState.isBoostSpeedActive) 2.0 else uiState.playbackSpeed
                playerState.indicatorText = if (uiState.isBoostSpeedActive) "2x" else null
            }

            LaunchedEffect(uiState.videoScaleMode) {
                playerState.aspectRatio = uiState.videoScaleMode
            }

            LaunchedEffect(uiState.autoPlay) {
                playerState.pauseRequested = !uiState.autoPlay
            }

            LaunchedEffect(playerState.duration, uiState.currentEpisodeNumber) {
                if (playerState.duration >= 60.0) {
                    viewModel.clampSkipRangesToDuration(playerState.duration)
                    viewModel.refreshSkipTimesWhenDurationKnown(playerState.duration)
                }
            }

            LaunchedEffect(uiState.subtitleSize) {
                playerState.subtitleSize = uiState.subtitleSize
            }

            LaunchedEffect(playerState.isPlaying, playerState.isPaused) {
                while (playerState.isPlaying && !playerState.isPaused) {
                    kotlinx.coroutines.delay(5000)
                    if (playerState.position >= 5.0) {
                        val currentAudioLabel =
                            playerState.audioTracks.firstOrNull { it.first == playerState.selectedAudioTrack }?.second?.lowercase()
                                ?: ""
                        val trackLang = if (currentAudioLabel.contains("eng")) "dub" else "sub"
                        val duration = playerState.duration.takeIf { it > 0 }
                            ?: (playerState.position + 120.0)
                        viewModel.saveProgress(playerState.position, duration, language = trackLang)
                        val watchedPercent = if (duration > 0.0) {
                            (playerState.position / duration) * 100.0
                        } else {
                            0.0
                        }
                        if (watchedPercent >= uiState.syncPercentage) {
                            val anime = uiState.episodeData?.anime
                            viewModel.markEpisodeWatched(
                                anilistId = anime?.anilistId ?: uiState.episodeData?.anilistId,
                                malId = anime?.malId ?: uiState.episodeData?.malId,
                                totalEpisodes = anime?.epCount,
                            )
                        }
                    }
                }
            }

            LaunchedEffect(playerState.audioTracks, uiState.targetLang) {
                if (playerState.audioTracks.isNotEmpty() && uiState.targetLang != null) {
                    val target = uiState.targetLang
                    val track = playerState.audioTracks.find {
                        val label = it.second.lowercase()
                        if (target == "dub") label.contains("eng") else (label.contains("jpn") || label.contains("ja"))
                    }
                    if (track != null && playerState.selectedAudioTrack != track.first) {
                        playerState.selectedAudioTrack = track.first
                    }
                }
            }

            LaunchedEffect(uiState.episodeData, uiState.currentEpisodeNumber) {
                val allEps = uiState.episodeData?.episodes ?: emptyList()
                val current = uiState.currentEpisodeNumber
                playerState.hasPrevEpisode = allEps.any { it.number < current }
                playerState.hasNextEpisode = allEps.any { it.number > current }
            }

            LaunchedEffect(playerState.mediaNextEpisodeCount, uiState.offlinePath) {
                if (playerState.mediaNextEpisodeCount == 0) return@LaunchedEffect
                if (uiState.offlinePath != null) return@LaunchedEffect
                if (playerState.hasNextEpisode) {
                    val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }
                        ?.minByOrNull { it.number }
                    if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                }
            }

            LaunchedEffect(playerState.mediaPrevEpisodeCount, uiState.offlinePath) {
                if (playerState.mediaPrevEpisodeCount == 0) return@LaunchedEffect
                if (uiState.offlinePath != null) return@LaunchedEffect
                if (playerState.hasPrevEpisode) {
                    val prevEp = uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }
                        ?.maxByOrNull { it.number }
                    if (prevEp != null) viewModel.onEpisodeSelected(prevEp.number)
                }
            }

            val episodeData = uiState.episodeData
            val currentEp = uiState.episodeData?.episodes?.find { it.number == uiState.currentEpisodeNumber }
            val animeTitle = episodeData?.title?.resolveDisplayTitle()?.takeIf { it.isNotBlank() }
                ?: uiState.offlineTitle
            val title = buildString {
                if (animeTitle != null) append(animeTitle)
                if (currentEp != null || uiState.offlinePath != null) {
                    if (isNotEmpty()) append(" • ")
                    append("Episode ${uiState.currentEpisodeNumber}")
                    currentEp?.titles?.firstOrNull()?.let { epTitle ->
                        if (epTitle.isNotEmpty()) append(" - $epTitle")
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose { DiscordRichPresenceManager.clear() }
            }

            val discordPresenceEnabled by AppComponent.settingsStore.discordRichPresenceFlow
                .collectAsState(initial = false)
            LaunchedEffect(discordPresenceEnabled) {
                DiscordRichPresenceManager.configure(discordPresenceEnabled)
                if (!discordPresenceEnabled) DiscordRichPresenceManager.clear()
            }

            val presencePositionBucket = (playerState.position / 15).toInt()
            LaunchedEffect(
                animeTitle,
                currentEp?.titles,
                uiState.currentEpisodeNumber,
                playerState.isPlaying,
                playerState.isPaused,
                playerState.duration,
                presencePositionBucket,
            ) {
                if (!discordPresenceEnabled) return@LaunchedEffect
                val presenceTitle = animeTitle?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                val episodeLabel = currentEp?.titles?.firstOrNull()?.takeIf { !it.isNullOrBlank() }
                val state = buildString {
                    append(if (playerState.isPaused || !playerState.isPlaying) "Paused" else "Episode")
                    append(" ${uiState.currentEpisodeNumber}")
                    episodeLabel?.let { append(" - $it") }
                }

                val now = Clock.System.now().toEpochMilliseconds()
                val positionMillis = playerState.position.coerceAtLeast(0.0).toLong() * 1000L
                val durationMillis = playerState.duration.coerceAtLeast(0.0).toLong() * 1000L
                val isActivelyPlaying = playerState.isPlaying && !playerState.isPaused
                DiscordRichPresenceManager.update(
                    DiscordPresenceActivity(
                        details = presenceTitle,
                        state = state,
                        startTimestampMillis = if (isActivelyPlaying && positionMillis > 0L) now - positionMillis else null,
                        endTimestampMillis = if (isActivelyPlaying && durationMillis > positionMillis) now + (durationMillis - positionMillis) else null,
                        largeImageText = "Watching on Anisurge",
                        smallImageText = if (isActivelyPlaying) "Playing" else "Paused",
                    )
                )
            }

            LaunchedEffect(playerState.position, playerState.duration, playerState.lastUserSeekAtMs) {
                if (playerState.duration <= 0) return@LaunchedEffect
                val now = Clock.System.now().toEpochMilliseconds()
                if (isWithinUserSeekCooldown(playerState.lastUserSeekAtMs, now)) return@LaunchedEffect
                val pos = playerState.position
                val dur = maxOf(playerState.duration, playerState.peakPlaybackDuration)

                if (playerState.hasNextEpisode &&
                    watchedEnoughForAutoNext(pos, dur, playerState.peakPlaybackPosition)
                ) {
                    if (viewModel.tryAutoAdvanceToNextEpisode(
                            pos,
                            dur,
                            playerState.peakPlaybackPosition,
                            playerState.lastUserSeekAtMs,
                        )
                    ) {
                        return@LaunchedEffect
                    }
                }
            }

            // Auto-skip: poll position via snapshotFlow so seeks fire when Aniskip data arrives late.
            LaunchedEffect(
                uiState.autoSkipIntro,
                uiState.autoSkipOutro,
                skipIntro,
                skipOutro,
                uiState.currentEpisodeNumber,
            ) {
                if (!uiState.autoSkipIntro && !uiState.autoSkipOutro) return@LaunchedEffect
                val autoIntro = uiState.autoSkipIntro
                val autoOutro = uiState.autoSkipOutro

                var lastSkipAt = -1.0
                snapshotFlow {
                    Triple(
                        playerState.position,
                        playerState.duration,
                        Pair(skipIntro, skipOutro),
                    )
                }
                    .filter { (_, dur, markers) -> dur >= 60.0 && (markers.first != null || markers.second != null) }
                    .collect { (pos, dur, markers) ->
                        val now = Clock.System.now().toEpochMilliseconds()
                        if (isWithinUserSeekCooldown(playerState.lastUserSeekAtMs, now)) return@collect
                        if (playerState.seekTarget != null) return@collect
                        val intro = if (autoIntro) markers.first else null
                        val outro = if (autoOutro) markers.second else null
                        val target = skipSeekTargetForPosition(intro, outro, pos, dur) ?: return@collect
                        if (kotlin.math.abs(target - lastSkipAt) < 1.0) return@collect
                        // Only auto-skip forward — never override a backward scrub.
                        if (target < pos - 1.0) return@collect
                        lastSkipAt = target
                        playerState.seekTarget = target
                    }
            }

            val pipManager = rememberPipManager()

            Box(
                modifier = modifier
                    .background(Color.Black)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (isDesktopPlatform) {
                            val boostActive = event.isShiftPressed && event.type == KeyEventType.KeyDown
                            if (boostActive != uiState.isBoostSpeedActive) {
                                viewModel.setBoostSpeedActive(boostActive)
                                playerState.canvasPointerMoved = Clock.System.now().toEpochMilliseconds()
                            }
                        }
                        if (event.type == KeyEventType.KeyDown) {
                            val now = Clock.System.now().toEpochMilliseconds()
                            when (event.key) {
                                Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.K -> {
                                    playerState.pauseRequested = !playerState.isPaused
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.DirectionLeft, Key.J -> {
                                    val nPos = (playerState.position - 10).coerceAtLeast(0.0)
                                    playerState.seekTarget = nPos
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.DirectionRight, Key.L -> {
                                    val nPos = (playerState.position + 10).coerceAtMost(playerState.duration)
                                    playerState.seekTarget = nPos
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.F -> {
                                    onFullscreenToggle()
                                    true
                                }

                                Key.M -> {
                                    playerState.isMuted = !playerState.isMuted
                                    playerState.indicatorText = if (playerState.isMuted) "Muted" else "Unmuted"
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.DirectionUp -> {
                                    val newVol = (playerState.volume + 5.0).coerceIn(0.0, 130.0)
                                    playerState.volume = newVol
                                    playerState.indicatorText = "Volume: ${newVol.toInt()}%"
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.DirectionDown -> {
                                    val newVol = (playerState.volume - 5.0).coerceIn(0.0, 130.0)
                                    playerState.volume = newVol
                                    playerState.indicatorText = "Volume: ${newVol.toInt()}%"
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.N -> {
                                    if (playerState.hasNextEpisode) {
                                        val nextEp =
                                            uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }
                                                ?.minByOrNull { it.number }
                                        if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                                        true
                                    } else false
                                }

                                Key.P -> {
                                    if (playerState.hasPrevEpisode) {
                                        val prevEp =
                                            uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }
                                                ?.maxByOrNull { it.number }
                                        if (prevEp != null) viewModel.onEpisodeSelected(prevEp.number)
                                        true
                                    } else false
                                }

                                Key.S -> {
                                    val target = skipSeekTargetForPosition(
                                        skipIntro,
                                        skipOutro,
                                        playerState.position,
                                        playerState.duration,
                                    )
                                    if (target != null) {
                                        playerState.seekTarget = target
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.Escape, Key.Back -> {
                                    if (uiState.isFullscreen) {
                                        onBack()
                                        true
                                    } else if (isAndroidTvPlatform) {
                                        onBack()
                                        true
                                    } else false
                                }
                                // ── Earphone / headphone media buttons (cross-platform) ──
                                // Linux:   XF86AudioPlay / XF86AudioPause / XF86AudioNext / XF86AudioPrev
                                // Windows: VK_MEDIA_PLAY_PAUSE / VK_MEDIA_NEXT_TRACK / VK_MEDIA_PREV_TRACK
                                // macOS:   NX_KEYTYPE_PLAY / NX_KEYTYPE_NEXT / NX_KEYTYPE_PREVIOUS
                                Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                                    playerState.pauseRequested = !playerState.isPaused
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.MediaStop -> {
                                    playerState.pauseRequested = true
                                    playerState.canvasPointerMoved = now
                                    true
                                }

                                Key.MediaNext -> {
                                    if (playerState.hasNextEpisode) {
                                        val nextEp =
                                            uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }
                                                ?.minByOrNull { it.number }
                                        if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                                        true
                                    } else false
                                }

                                Key.MediaPrevious -> {
                                    if (playerState.hasPrevEpisode) {
                                        val prevEp =
                                            uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }
                                                ?.maxByOrNull { it.number }
                                        if (prevEp != null) viewModel.onEpisodeSelected(prevEp.number)
                                        true
                                    } else false
                                }

                                else -> false
                            }
                        } else false
                    }
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                    }
            ) {
                VideoPlayerSurface(
                    state = playerState,
                    modifier = Modifier.fillMaxSize(),
                    onFinished = {
                        if (uiState.sleepAfterEpisode) {
                            playerState.pauseRequested = true
                            viewModel.setSleepAfterEpisode(false)
                            playerState.indicatorText = "Sleep timer finished"
                            val currentAudioLabel =
                                playerState.audioTracks.firstOrNull { it.first == playerState.selectedAudioTrack }
                                    ?.second?.lowercase().orEmpty()
                            viewModel.flushWatchProgress(
                                playerState.position,
                                maxOf(playerState.duration, playerState.peakPlaybackDuration),
                                if (currentAudioLabel.contains("eng")) "dub" else "sub",
                            )
                        } else {
                            viewModel.tryAutoAdvanceToNextEpisode(
                                playerState.position,
                                maxOf(playerState.duration, playerState.peakPlaybackDuration),
                                playerState.peakPlaybackPosition,
                                playerState.lastUserSeekAtMs,
                            )
                        }
                    }
                )

                playerState.error?.let { err ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .clickable { playerState.error = null }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(err, color = Color(0xFFFFB74D), fontSize = 13.sp)
                    }
                }

                // Render out our cross-platform compose player controls overlay
                val isOffline = uiState.offlinePath != null
                val malId = uiState.episodeData?.anime?.malId ?: uiState.episodeData?.animeId?.toIntOrNull()
                val anilistId = uiState.episodeData?.anime?.anilistId ?: uiState.episodeData?.animeId?.toIntOrNull()
                val totalEpisodes = uiState.episodeData?.anime?.episodes
                PlayerControls(
                    playerState = playerState,
                    streamingData = uiState.streamingData,
                    introMarker = skipIntro,
                    outroMarker = skipOutro,
                    title = title,
                    isFullscreen = uiState.isFullscreen,
                    onFullscreenToggle = onFullscreenToggle,
                    onBack = onBack,
                    onNextEpisode = {
                        if (!isOffline) {
                            val nextEp =
                                uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }
                                    ?.minByOrNull { it.number }
                            if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                        }
                    },
                    onPrevEpisode = {
                        if (!isOffline) {
                            val prevEp =
                                uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }
                                    ?.maxByOrNull { it.number }
                            if (prevEp != null) viewModel.onEpisodeSelected(prevEp.number)
                        }
                    },
                    onCaptionsClick = { viewModel.toggleSettingsOverlay(SettingsMenuPage.SUBTITLES) },
                    onSettingsClick = { viewModel.toggleSettingsOverlay() },
                    onInfoClick = { if (!isOffline) (onInfoClick ?: { viewModel.toggleSidePanel("info") }).invoke() },
                    onEpisodesClick = {
                        if (!isOffline) (onEpisodesClick ?: { viewModel.toggleSidePanel("episodes") }).invoke()
                    },
                    onCommentsClick = {
                        if (!isOffline) (onCommentsClick ?: { viewModel.toggleSidePanel("comments") }).invoke()
                    },
                    onWatchlistClick = { if (!isOffline) viewModel.toggleSettingsOverlay(SettingsMenuPage.WATCHLIST) },
                    onBoostSpeedChange = { viewModel.setBoostSpeedActive(it) },
                    isInWatchlist = uiState.episodeData?.folder != null,
                    currentFolder = uiState.episodeData?.folder,
                    isOffline = isOffline,
                    onExit = onExit,
                    onSyncMALClick = if (uiState.hasMalToken && malId != null) {
                        { viewModel.syncToMAL(malId, totalEpisodes) }
                    } else null,
                    onSyncAniListClick = if (uiState.hasAnilistToken && anilistId != null) {
                        { viewModel.syncToAniList(anilistId, totalEpisodes) }
                    } else null,
                    isSyncingMAL = uiState.isSyncingMal,
                    isSyncingAniList = uiState.isSyncingAnilist,
                    pipManager = pipManager,
                    showLibraryActions = showPlayerLibraryActions,
                    showFullscreenButton = showFullscreenButton,
                    compactControls = compactControls,
                    modifier = Modifier.fillMaxSize()
                )

                // Next Episode Autoplay Overlay
                if (!isOffline && playerState.duration > 0 && playerState.hasNextEpisode && playerState.position >= playerState.duration - 15.0) {
                    val remaining = (playerState.duration - playerState.position).toInt().coerceAtLeast(0)
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 132.dp, end = 12.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.3f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .background(Color.Black.copy(alpha = 0.8f))
                                .clickable {
                                    val nextEp =
                                        uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }
                                            ?.minByOrNull { it.number }
                                    if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Next episode",
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            if (uiState.autoNext) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "in ${remaining}s",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                androidx.compose.material.icons.Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Skip Intro Overlay
                val intro = skipIntro
                if (intro != null && intro.start != null && intro.end != null) {
                    val inRange = intro.isPositionInRange(playerState.position)
                    if (inRange && !skipIntroTimedOut && !skipIntroManualDismissed) {
                        val progress = (1.0f - (skipIntroElapsed.toFloat() / SKIP_TIMEOUT)).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(bottom = 88.dp, start = 12.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .clickable {
                                        skipSeekTargetForPosition(
                                            intro,
                                            null,
                                            playerState.position,
                                            playerState.duration,
                                        )?.let { playerState.seekTarget = it }
                                        skipIntroManualDismissed = true
                                    }
                            ) {
                                // Progress bar background (draining effect)
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .fillMaxWidth(progress)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )

                                Text(
                                    "Skip Intro",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Skip Outro Overlay
                val outro = skipOutro
                if (outro != null && outro.start != null && outro.end != null) {
                    val inRange = outro.isPositionInRange(playerState.position)
                    if (inRange && !skipOutroTimedOut && !skipOutroManualDismissed) {
                        val progress = (1.0f - (skipOutroElapsed.toFloat() / SKIP_TIMEOUT)).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(bottom = 88.dp, end = 12.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .clickable {
                                        skipSeekTargetForPosition(
                                            null,
                                            outro,
                                            playerState.position,
                                            playerState.duration,
                                        )?.let { playerState.seekTarget = it }
                                        skipOutroManualDismissed = true
                                    }
                            ) {
                                // Progress bar background (draining effect)
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .fillMaxWidth(progress)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )

                                Text(
                                    "Skip Outro",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            AnimatedWatchSettingsOverlay(visible = uiState.showSettingsOverlay) {
                val isOffline = uiState.offlinePath != null
                val servers = if (isOffline) emptyList() else uiState.servers
                Box(Modifier.fillMaxSize().zIndex(50f)) {
                    SettingsOverlay(
                        uiState = uiState,
                        servers = servers,
                        onDismiss = { viewModel.toggleSettingsOverlay() },
                        onQualitySelected = { quality ->
                            viewModel.saveCurrentPosition(playerState.position)
                            viewModel.setQuality(quality)
                        },
                        onSubtitleSelected = { url ->
                            val selectedSub = uiState.availableSubtitles.firstOrNull { it.url == url }
                            val lang = selectedSub?.title ?: selectedSub?.resolvedLang
                            viewModel.setSubtitle(url, lang)
                        },
                        onServerSelected = { serverLabel ->
                            if (isOffline) return@SettingsOverlay
                            val currentAudioLabel =
                                playerState.audioTracks.firstOrNull { it.first == playerState.selectedAudioTrack }?.second?.lowercase()
                                    ?: ""
                            val currentTrackLang = if (currentAudioLabel.contains("eng")) "dub" else "sub"

                            val currentSubData =
                                uiState.availableSubtitles.firstOrNull { it.url == uiState.currentSubtitleUrl }
                            val targetSubtitleLang = currentSubData?.title ?: currentSubData?.resolvedLang
                            val targetSubtitleLangCode = currentSubData?.language ?: currentSubData?.lang

                            viewModel.changeServerWithState(
                                newServer = serverLabel,
                                position = playerState.position,
                                targetAudioLang = currentTrackLang,
                                targetSubtitleLang = targetSubtitleLang,
                                targetSubtitleLangCode = targetSubtitleLangCode
                            )
                        },
                        onSpeedSelected = { viewModel.setSpeed(it) },
                        onCycleAudio = { playerState.cycleAudio = true },
                        audioTracks = playerState.audioTracks,
                        selectedAudioTrack = playerState.selectedAudioTrack,
                        onAudioTrackSelected = { playerState.selectedAudioTrack = it },
                        subtitleTracks = playerState.subtitleTracks,
                        selectedSubtitleTrack = playerState.selectedSubtitleTrack,
                        onSubtitleTrackSelected = { playerState.selectedSubtitleTrack = it },
                        onSubtitleSizeSelected = { viewModel.setSubtitleSize(it) },
                        onWatchlistStatusSelected = { folder -> viewModel.updateWatchlistStatus(folder) },
                        onAutoPlayToggle = { viewModel.setAutoPlay(it) },
                        onAutoNextToggle = { viewModel.setAutoNext(it) },
                        onAutoSkipIntroToggle = { viewModel.setAutoSkipIntro(it) },
                        onAutoSkipOutroToggle = { viewModel.setAutoSkipOutro(it) },
                        onVideoScaleModeSelected = { viewModel.setVideoScaleMode(it) },
                        onPlayerEnhancementsChanged = viewModel::setSessionPlayerEnhancements,
                        onPlayerUtilitiesChanged = viewModel::setSessionPlayerUtilities,
                        onScreenshot = { playerState.screenshotRequestCount++ },
                        onSleepTimerChanged = viewModel::setSleepTimer,
                        onSleepAfterEpisodeChanged = viewModel::setSleepAfterEpisode,
                    )
                }
            }
        } else if (!uiState.isLoading && !uiState.isLoadingVideo) {
            Box(modifier = modifier.background(Color.Black)) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No streaming links available for server: ${uiState.currentServer}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.toggleSettingsOverlay() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Change Server", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            AnimatedWatchSettingsOverlay(visible = uiState.showSettingsOverlay) {
                val servers = uiState.servers
                SettingsOverlay(
                    uiState = uiState,
                    servers = servers,
                    onDismiss = { viewModel.toggleSettingsOverlay() },
                    onQualitySelected = { viewModel.setQuality(it) },
                    onSubtitleSelected = { url ->
                        val selectedSub = uiState.availableSubtitles.firstOrNull { it.url == url }
                        val lang = selectedSub?.title ?: selectedSub?.resolvedLang
                        viewModel.setSubtitle(url, lang)
                    },
                    onServerSelected = { serverLabel ->
                        viewModel.changeServerWithState(
                            newServer = serverLabel,
                            position = uiState.savedWatchPosition,
                            targetAudioLang = null,
                            targetSubtitleLang = null,
                            targetSubtitleLangCode = null
                        )
                    },
                    onSpeedSelected = { viewModel.setSpeed(it) },
                    onCycleAudio = { },
                    audioTracks = emptyList(),
                    selectedAudioTrack = -1,
                    onAudioTrackSelected = { },
                    subtitleTracks = emptyList(),
                    selectedSubtitleTrack = -1,
                    onSubtitleTrackSelected = { },
                    onSubtitleSizeSelected = { viewModel.setSubtitleSize(it) },
                    onWatchlistStatusSelected = { folder -> viewModel.updateWatchlistStatus(folder) },
                    onAutoPlayToggle = { viewModel.setAutoPlay(it) },
                    onAutoNextToggle = { viewModel.setAutoNext(it) },
                    onAutoSkipIntroToggle = { viewModel.setAutoSkipIntro(it) },
                    onAutoSkipOutroToggle = { viewModel.setAutoSkipOutro(it) },
                    onVideoScaleModeSelected = { viewModel.setVideoScaleMode(it) },
                    onPlayerEnhancementsChanged = viewModel::setSessionPlayerEnhancements,
                    onPlayerUtilitiesChanged = viewModel::setSessionPlayerUtilities,
                    onScreenshot = { },
                    onSleepTimerChanged = viewModel::setSleepTimer,
                    onSleepAfterEpisodeChanged = viewModel::setSleepAfterEpisode,
                )
            }
        }
    }
}

private fun shouldUseLocalStreamProxy(url: String): Boolean {
    val lower = url.substringBefore('#').lowercase()
    val path = lower.substringBefore('?')
    return HlsPngTsStrip.isDisguisedTsCdnHost(url) ||
        HlsPngTsStrip.isVibeplayerHlsHost(url) ||
        HlsPngTsStrip.isAnikageHlsHost(url) ||
        ProgressiveStreamAccel.shouldAccelerate(url) ||
        path.endsWith(".jpg") ||
        path.endsWith(".jpeg")
}

private fun formatTime(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val hStr = h.toString().padStart(2, '0')
    val mStr = m.toString().padStart(2, '0')
    val sStr = s.toString().padStart(2, '0')
    return if (h > 0) "$hStr:$mStr:$sStr" else "$mStr:$sStr"
}

private fun String.prettyInfoLabel(): String =
    replace('_', ' ')
        .lowercase()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }

private fun stripWatchInfoHtml(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .replace("<br>", "\n", ignoreCase = true)
        .replace("<br/>", "\n", ignoreCase = true)
        .replace("<br />", "\n", ignoreCase = true)
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
