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
import to.kuudere.anisuge.platform.isDesktopPlatform
import to.kuudere.anisuge.platform.isAndroidTvPlatform
import to.kuudere.anisuge.player.PlayerControls
import to.kuudere.anisuge.player.StreamProxy
import to.kuudere.anisuge.player.VideoPlayerState
import to.kuudere.anisuge.player.VideoPlayerSurface
import to.kuudere.anisuge.player.rememberVideoPlayerState
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
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.SessionCheckResult
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
    viewModel: WatchViewModel,
    onBack: () -> Unit,
    onExit: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var isLandscape by remember { mutableStateOf(true) }

    LockScreenOrientation(isLandscape)
    to.kuudere.anisuge.platform.SyncFullscreen(uiState.isFullscreen)

    val handleBack = {
        isLandscape = false
        onBack()
    }

    to.kuudere.anisuge.platform.PlatformBackHandler {
        handleBack()
    }

    LaunchedEffect(animeId, episodeNumber, offlinePath, server, lang, offlineTitle, resumeAtSeconds) {
        viewModel.initialize(
            animeId,
            episodeNumber,
            server,
            lang,
            offlinePath,
            offlineTitle,
            resumeFromContinueSeconds = resumeAtSeconds,
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

    Scaffold(
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
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
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    val isPanelActive = uiState.activeSidePanel != null
                    val sidePanelWidth = 350.dp

                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            // Use unique key so player FULLY resets when video changes
                            // This prevents old video from persisting when switching online->offline
                            val playerKey = "$animeId-${uiState.currentEpisodeNumber}-${offlinePath ?: "online"}"
                            key(playerKey) {
                                WatchVideoPlayer(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    onFullscreenToggle = { viewModel.setFullscreen(!uiState.isFullscreen) },
                                    onBack = handleBack,
                                    onExit = onExit
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isPanelActive,
                            enter = slideInHorizontally(animationSpec = tween(300)) { it } + expandHorizontally(animationSpec = tween(300), expandFrom = Alignment.Start) + fadeIn(animationSpec = tween(300)),
                            exit = slideOutHorizontally(animationSpec = tween(300)) { it } + shrinkHorizontally(animationSpec = tween(300), shrinkTowards = Alignment.Start) + fadeOut(animationSpec = tween(300))
                        ) {
                            Box(Modifier.width(sidePanelWidth).fillMaxHeight()) {
                                SidePanelContent(uiState, viewModel, animeId)
                            }
                        }
                    }
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
            .background(Color.Black)
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
                        val watchlistButtonLabel = if (isInWatchlist) {
                            episodeData?.folder?.takeIf { it.isNotBlank() && it != "Remove" }
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
                                                    .background(Color(0xFF000000))
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
                                                        .background(Color(0xFF1D1D1D))
                                                        .clickable { showWatchlistSheet = true }
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            if (episodeData?.folder != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                            null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            text = watchlistButtonLabel,
                                                            color = Color.White,
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

                        LaunchedEffect(uiState.activeSidePanel, searchQuery, isAscending, currentPageStart, isPageDropdownExpanded) {
                            if (uiState.activeSidePanel == "episodes" && !hasScrolled) {
                                val currentEpIndex = visibleEpisodes.indexOfFirst { it.number == uiState.currentEpisodeNumber }
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
                                        Text("No episodes found", color = Color.Gray)
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
                                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                                    .background(Color.Black)
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
                                                        val end = (currentPageStart + episodesPerPage - 1).coerceAtMost(totalEpisodes)
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
                                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                                        .background(Color.Black)
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
                                                    if (isSelected) Color(0xFF1E1E1E)
                                                    else Color(0xFF0D0D0D)
                                                )
                                                .then(
                                                    if (isSelected) Modifier.border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.15f),
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
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 14.sp
                                                )
                                                val title = episode.titles?.firstOrNull()
                                                if (!title.isNullOrBlank()) {
                                                    Spacer(Modifier.height(3.dp))
                                                    Text(
                                                        title,
                                                        color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.Gray,
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
                                                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                                            y = pageDropdownAnchorOffset.y + pageDropdownAnchorSize.height + with(density) { 4.dp.roundToPx() }
                                        )
                                    }
                                    .width(with(density) { pageDropdownAnchorSize.width.toDp() })
                                    .heightIn(max = pageDropdownItemHeight * pageDropdownVisibleItems)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1D1D1D)),
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
                                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                        } // end outer Box
                    }
                    "comments" -> {
                        var fastUserId by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(Unit) {
                            fastUserId = AppComponent.sessionStore.get()?.let { "me" }
                        }

                        val userProfile by produceState<to.kuudere.anisuge.data.models.UserProfile?>(null) {
                            val result = AppComponent.authService.checkSession()
                            value = if (result is SessionCheckResult.Valid) result.user else null
                        }
                        
                        // Use the Kuudere string slug passed to WatchScreen, not the anilist int
                        CommentsSection(
                            animeId = animeId,
                            episodeNumber = uiState.currentEpisodeNumber,
                            userId = userProfile?.effectiveId ?: fastUserId,
                            username = userProfile?.username,
                            userPfp = userProfile?.avatar,
                            onClose = { viewModel.toggleSidePanel(null) }
                        )
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
fun WatchVideoPlayer(
    uiState: WatchUiState,
    viewModel: WatchViewModel,
    modifier: Modifier = Modifier,
    onFullscreenToggle: () -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit = {}
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

            to.kuudere.anisuge.platform.WindowManagementButtons(
                onClose = onExit,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )
        }
    } else {
        val currentUrl = uiState.availableQualities.find { it.first == uiState.currentQuality }?.second
            ?: if (uiState.currentServer.equals("suzu", ignoreCase = true)) {
                uiState.availableQualities.firstOrNull { it.first.equals("HardSub", ignoreCase = true) }?.second
            } else {
                null
            }
            ?: uiState.availableQualities.firstOrNull()?.second

        if (currentUrl != null) {
            // Get headers from the matching source in streamingData
            val currentSource = uiState.streamingData?.sources?.find { it.url == currentUrl }
            val streamHeaders = currentSource?.headers ?: uiState.streamingData?.headers
            val playbackUrl = remember(currentUrl, streamHeaders) {
                StreamProxy.proxyUrl(currentUrl, streamHeaders)
            }
            DisposableEffect(playbackUrl) {
                onDispose { StreamProxy.release(currentUrl) }
            }

            // Desktop: We now use our custom Compose PlayerControls instead of mpv's OSC
            val useOsc = false
            // Offline videos (MKV) may have fonts embedded in the container — allow them.
            // Online streams always use API-downloaded fonts, so embedded fonts must be off.
            val useEmbeddedFonts = uiState.offlinePath != null
            val playerState = rememberVideoPlayerState(
                url = playbackUrl,
                startPosition = uiState.savedWatchPosition,
                fontsDir = uiState.currentFontsDir,
                embeddedFonts = useEmbeddedFonts,
                showControls = useOsc,
                autoPlay = uiState.autoPlay,
                speed = uiState.playbackSpeed,
                subtitleSize = uiState.subtitleSize,
                headers = uiState.streamingData?.headers
            )

            // Resume: [rememberVideoPlayerState] keys only on [playbackUrl], so [savedWatchPosition] can
            // arrive after the player is created (e.g. GET /watch returns after the stream is ready).
            // Seek once duration is known and playback is still at the beginning.
            LaunchedEffect(playbackUrl, uiState.currentEpisodeNumber, uiState.savedWatchPosition) {
                val resumeAt = uiState.savedWatchPosition
                if (resumeAt < 1.0) return@LaunchedEffect
                var waited = 0
                while (waited < 6_000 && playerState.duration < 0.25) {
                    kotlinx.coroutines.delay(24)
                    waited += 24
                }
                if (playerState.duration < 0.25) return@LaunchedEffect
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
                val intro = uiState.streamingData?.intro
                val outro = uiState.streamingData?.outro

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
                while(true) {
                    kotlinx.coroutines.delay(100)
                    if (playerState.isPlaying && !playerState.isPaused) {
                        val current = playerState.position
                        val intro = uiState.streamingData?.intro
                        val outro = uiState.streamingData?.outro

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
            
            LaunchedEffect(uiState.availableSubtitles) {
                if (uiState.availableSubtitles.isNotEmpty()) {
                    playerState.allSubUrls = uiState.availableSubtitles.mapNotNull { sub ->
                        sub.url?.let { Triple(it, sub.title ?: sub.resolvedLang ?: "Subtitle", it == uiState.currentSubtitleUrl) }
                    }
                }
            }

            LaunchedEffect(uiState.currentSubtitleUrl) {
                playerState.subFileUrl = uiState.currentSubtitleUrl ?: "NONE"
                playerState.subFileName = uiState.availableSubtitles.firstOrNull { it.url == uiState.currentSubtitleUrl }?.let { it.title ?: it.resolvedLang } ?: "Subtitle"
            }
            
            LaunchedEffect(uiState.playbackSpeed) {
                playerState.playbackSpeed = uiState.playbackSpeed
            }

            LaunchedEffect(uiState.subtitleSize) {
                playerState.subtitleSize = uiState.subtitleSize
            }

            LaunchedEffect(playerState.isPlaying, playerState.isPaused) {
                while (playerState.isPlaying && !playerState.isPaused) {
                    kotlinx.coroutines.delay(5000)
                    if (playerState.duration > 0) {
                        val currentAudioLabel = playerState.audioTracks.firstOrNull { it.first == playerState.selectedAudioTrack }?.second?.lowercase() ?: ""
                        val trackLang = if (currentAudioLabel.contains("eng")) "dub" else "sub"
                        viewModel.saveProgress(playerState.position, playerState.duration, language = trackLang)
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
                    val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }?.minByOrNull { it.number }
                    if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                }
            }

            LaunchedEffect(playerState.mediaPrevEpisodeCount, uiState.offlinePath) {
                if (playerState.mediaPrevEpisodeCount == 0) return@LaunchedEffect
                if (uiState.offlinePath != null) return@LaunchedEffect
                if (playerState.hasPrevEpisode) {
                    val prevEp = uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }?.maxByOrNull { it.number }
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

            LaunchedEffect(playerState.position) {
                if (playerState.duration <= 0) return@LaunchedEffect
                val pos = playerState.position
                val dur = playerState.duration
                
                // Proactive Auto Next at the very end of the video
                if (uiState.autoNext && playerState.hasNextEpisode && pos >= dur - 0.5) {
                    val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }?.minByOrNull { it.number }
                    if (nextEp != null) {
                        viewModel.onEpisodeSelected(nextEp.number)
                        return@LaunchedEffect
                    }
                }

                // Auto skip intro
                if (uiState.autoSkipIntro) {
                    val intro = uiState.streamingData?.intro
                    if (intro != null && intro.start != null && intro.end != null) {
                        // Wait 1s into the range to match Zen logic
                        if (pos >= intro.start + 1.0 && pos < intro.end - 1.0) {
                            playerState.seekTarget = (intro.end + 0.5).toDouble()
                        }
                    }
                }

                // Auto skip outro
                if (uiState.autoSkipOutro) {
                    val outro = uiState.streamingData?.outro
                    if (outro != null && outro.start != null && outro.end != null) {
                        // Wait 1s into the range to match Zen logic
                        if (pos >= outro.start + 1.0 && pos < outro.end - 1.0) {
                            val target = (outro.end + 0.5).toDouble()
                            playerState.seekTarget = if (dur > 0) target.coerceAtMost(dur - 0.5) else target
                        }
                    }
                }
            }

            Box(
                modifier = modifier
                    .background(Color.Black)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
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
                                        val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }?.minByOrNull { it.number }
                                        if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                                        true
                                    } else false
                                }
                                Key.P -> {
                                    if (playerState.hasPrevEpisode) {
                                        val prevEp = uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }?.maxByOrNull { it.number }
                                        if (prevEp != null) viewModel.onEpisodeSelected(prevEp.number)
                                        true
                                    } else false
                                }
                                Key.S -> {
                                    val intro = uiState.streamingData?.intro
                                    val outro = uiState.streamingData?.outro
                                    val pos = playerState.position
                                    
                                    if (intro != null && intro.start != null && intro.end != null && pos >= intro.start && pos < intro.end) {
                                        playerState.seekTarget = (intro.end + 0.5).toDouble()
                                        true
                                    } else if (outro != null && outro.start != null && outro.end != null && pos >= outro.start && pos < outro.end) {
                                        val target = (outro.end + 0.5).toDouble()
                                        playerState.seekTarget = if (playerState.duration > 0) target.coerceAtMost(playerState.duration - 0.5) else target
                                        true
                                    } else false
                                }
                                Key.Escape, Key.Back -> {
                                    if (uiState.isFullscreen) {
                                        onFullscreenToggle()
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
                                        val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }?.minByOrNull { it.number }
                                        if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                                        true
                                    } else false
                                }
                                Key.MediaPrevious -> {
                                    if (playerState.hasPrevEpisode) {
                                        val prevEp = uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }?.maxByOrNull { it.number }
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
                        if (uiState.autoNext && playerState.hasNextEpisode) {
                            val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }?.minByOrNull { it.number }
                            if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                        }
                    }
                )

                // Render out our cross-platform compose player controls overlay
                val isOffline = uiState.offlinePath != null
                PlayerControls(
                    playerState = playerState,
                    streamingData = uiState.streamingData,
                    title = title,
                    isFullscreen = uiState.isFullscreen,
                    onFullscreenToggle = onFullscreenToggle,
                    onBack = onBack,
                    onNextEpisode = {
                        if (!isOffline) {
                            val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }?.minByOrNull { it.number }
                            if (nextEp != null) viewModel.onEpisodeSelected(nextEp.number)
                        }
                    },
                    onPrevEpisode = {
                        if (!isOffline) {
                            val prevEp = uiState.episodeData?.episodes?.filter { it.number < uiState.currentEpisodeNumber }?.maxByOrNull { it.number }
                            if (prevEp != null) viewModel.onEpisodeSelected(prevEp.number)
                        }
                    },
                    onCaptionsClick = { viewModel.toggleSettingsOverlay(SettingsMenuPage.SUBTITLES) },
                    onSettingsClick = { viewModel.toggleSettingsOverlay() },
                    onInfoClick = { if (!isOffline) viewModel.toggleSidePanel("info") },
                    onEpisodesClick = { if (!isOffline) viewModel.toggleSidePanel("episodes") },
                    onCommentsClick = { if (!isOffline) viewModel.toggleSidePanel("comments") },
                    onWatchlistClick = { if (!isOffline) viewModel.toggleSettingsOverlay(SettingsMenuPage.WATCHLIST) },
                    isInWatchlist = uiState.episodeData?.folder != null,
                    currentFolder = uiState.episodeData?.folder,
                    isOffline = isOffline,
                    onExit = onExit,
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
                                .border(1.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .clickable {
                                    val nextEp = uiState.episodeData?.episodes?.filter { it.number > uiState.currentEpisodeNumber }?.minByOrNull { it.number }
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
                val intro = uiState.streamingData?.intro
                if (intro != null && intro.start != null && intro.end != null) {
                    val inRange = playerState.position >= intro.start && playerState.position < intro.end - 1.0
                    if (inRange && !uiState.autoSkipIntro && !skipIntroTimedOut && !skipIntroManualDismissed) {
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
                                        playerState.seekTarget = (intro.end + 0.5).toDouble()
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
                val outro = uiState.streamingData?.outro
                if (outro != null && outro.start != null && outro.end != null) {
                    val inRange = playerState.position >= outro.start && playerState.position < outro.end - 1.0
                    if (inRange && !uiState.autoSkipOutro && !skipOutroTimedOut && !skipOutroManualDismissed) {
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
                                        val target = (outro.end + 0.5).toDouble()
                                        playerState.seekTarget = if (playerState.duration > 0) target.coerceAtMost(playerState.duration - 0.5) else target
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
            
            if (uiState.showSettingsOverlay) {
                val isOffline = uiState.offlinePath != null
                val servers = if (isOffline) emptyList() else viewModel.getAvailableServers()
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
                        if (isOffline) return@SettingsOverlay
                        val currentServer = uiState.currentServer.lowercase()
                        val newServer = serverLabel.lowercase()

                        val currentAudioLabel = playerState.audioTracks.firstOrNull { it.first == playerState.selectedAudioTrack }?.second?.lowercase() ?: ""
                        val currentTrackLang = if (currentAudioLabel.contains("eng")) "dub" else "sub"
                        
                        var targetAudioLang: String? = currentTrackLang
                        val currentSubData = uiState.availableSubtitles.firstOrNull { it.url == uiState.currentSubtitleUrl }
                        var targetSubtitleLang = currentSubData?.title ?: currentSubData?.resolvedLang
                        var targetSubtitleLangCode = currentSubData?.language ?: currentSubData?.lang
                        
                        viewModel.changeServerWithState(
                            newServer = serverLabel, 
                            position = playerState.position, 
                            targetAudioLang = targetAudioLang,
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
                    onAutoSkipOutroToggle = { viewModel.setAutoSkipOutro(it) }
                )
            }
        } else {
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

            if (uiState.showSettingsOverlay) {
                val servers = viewModel.getAvailableServers()
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
                    onAutoSkipOutroToggle = { viewModel.setAutoSkipOutro(it) }
                )
            }
        }
    }
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
