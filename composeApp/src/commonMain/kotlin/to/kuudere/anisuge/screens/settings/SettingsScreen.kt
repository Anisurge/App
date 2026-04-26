package to.kuudere.anisuge.screens.settings

import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.absolutePath

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import to.kuudere.anisuge.ui.OfflineState
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anisurge.composeapp.generated.resources.Res
import anisurge.composeapp.generated.resources.anilist
import org.jetbrains.compose.resources.painterResource
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.SessionInfoResponse
import to.kuudere.anisuge.data.models.StorageInfo
import to.kuudere.anisuge.data.models.AnimeFolderInfo
import to.kuudere.anisuge.platform.AppVersion
import to.kuudere.anisuge.platform.AppBuildNumber
import to.kuudere.anisuge.platform.PlatformName
import to.kuudere.anisuge.platform.TvQrPairingAction
import to.kuudere.anisuge.platform.isDesktopPlatform
import to.kuudere.anisuge.platform.isAndroidTvPlatform
import to.kuudere.anisuge.ui.ConfirmDialog
import to.kuudere.anisuge.screens.settings.SettingsTab
import androidx.compose.ui.text.style.TextAlign

// ── Colors ── Black & white theme ────────────────────────────────────────────────
private val BG       = Color(0xFF000000)
private val BG_CARD  = Color(0xFF0A0A0A)
private val BG_HOVER = Color(0xFF141414)
private val BORDER   = Color.White.copy(alpha = 0.08f)
private val MUTED    = Color.White.copy(alpha = 0.5f)
private val TEXT     = Color.White

// ── Data ────────────────────────────────────────────────────────────────────────
data class SettingsNavItem(
    val tab: SettingsTab,
    val label: String,
    val icon: ImageVector
)

// ── Main Screen ─────────────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit,
    onRefresh: () -> Unit = {},
    isLoggingOut: Boolean = false,
    initialTab: SettingsTab? = null,
    onExit: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf<SettingsTab>(initialTab ?: SettingsTab.Preferences) }

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            selectedTab = initialTab
        }
    }



    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
            // Refresh global session on success for security/profile stuff
            if (it.contains("Password", ignoreCase = true) || 
                it.contains("MFA", ignoreCase = true) || 
                it.contains("Profile", ignoreCase = true)) {
                onRefresh()
            }
        }
    }

    LaunchedEffect(selectedTab) {
        viewModel.onTabSelected(selectedTab)
    }


    val navItems = buildList {
        add(SettingsNavItem(SettingsTab.Profile, "Profile", Icons.Default.Person))
        add(SettingsNavItem(SettingsTab.Preferences, "Preferences", Icons.Default.Settings))
        add(SettingsNavItem(SettingsTab.Servers, "Servers", Icons.Default.Dns))
        add(SettingsNavItem(SettingsTab.Sync, "Sync", Icons.Default.Sync))
        add(SettingsNavItem(SettingsTab.Storage, "Storage", Icons.Default.Storage))
        add(SettingsNavItem(SettingsTab.Sessions, "Sessions", Icons.Default.Devices))
        add(SettingsNavItem(SettingsTab.Security, "Security", Icons.Default.Lock))
        if (!isDesktopPlatform) {
            add(SettingsNavItem(SettingsTab.Notifications, "Notifications", Icons.Default.Notifications))
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (uiState.errorMessage != null) Color(0xFFBF80FF) else Color(0xFF1B5E20),
                    contentColor = Color.White
                )
            }
        },
        containerColor = BG,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isLargeScreen = maxWidth >= 900.dp

            if (isLargeScreen) {
                // Desktop: Sidebar + Centered Content
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sidebar
                    Sidebar(
                        navItems = navItems,
                        selectedTab = selectedTab,
                        onTabSelect = { selectedTab = it },
                        uiState = uiState,
                        onLogout = onLogout,
                        isLoggingOut = isLoggingOut,
                        modifier = Modifier.width(260.dp)
                    )

                    VerticalDivider(thickness = 1.dp, color = BORDER)

                    // Content Area - Centered with max width
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth()) {
                                to.kuudere.anisuge.platform.WindowManagementButtons(
                                    onClose = onExit,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp)
                                )
                            }
                            SettingsContent(
                                selectedTab = selectedTab,
                                uiState = uiState,
                                navItems = navItems,
                                onLogout = onLogout,
                                viewModel = viewModel,
                                modifier = Modifier
                                    .widthIn(max = 900.dp)
                                    .padding(horizontal = 48.dp, vertical = 40.dp)
                            )
                        }
                    }
                }
            } else {
                // Mobile: List menu with navigation to detail screens
                var showDetail by remember { mutableStateOf<SettingsTab?>(null) }

                to.kuudere.anisuge.platform.PlatformBackHandler(enabled = showDetail != null) {
                    showDetail = null
                }

                AnimatedContent(
                    targetState = showDetail,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "mobile_settings"
                ) { detailTab ->
                    if (detailTab == null) {
                        MobileSettingsList(
                            navItems = navItems.filter { it.tab != SettingsTab.Profile },
                            uiState = uiState,
                            onLogout = onLogout,
                            isLoggingOut = isLoggingOut,
                            onRetry = { viewModel.refresh() },
                            onItemClick = {
                                showDetail = it
                                // Load data when opening detail
                                viewModel.onTabSelected(it)
                            }
                        )
                    } else {
                        // Detail page
                        MobileSettingsDetail(
                            tab = detailTab,
                            navItems = navItems,
                            uiState = uiState,
                            onBack = { showDetail = null },
                            onLogout = onLogout,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        // Confirmation Dialogs
        if (uiState.showDisconnectConfirm) {
            ConfirmDialog(
                title = "Disconnect AniList",
                message = "Are you sure you want to disconnect your AniList account? Your progress will no longer be synced.",
                confirmLabel = "Disconnect",
                onConfirm = {
                    viewModel.setShowDisconnectConfirm(false)
                    viewModel.disconnectAniList()
                },
                onDismiss = { viewModel.setShowDisconnectConfirm(false) }
            )
        }

        if (uiState.showDeleteAllSessionsConfirm) {
            ConfirmDialog(
                title = "End All Sessions",
                message = "Are you sure you want to end all other active sessions? You will be logged out on all other devices.",
                confirmLabel = "End All",
                onConfirm = {
                    viewModel.setShowDeleteAllSessionsConfirm(false)
                    viewModel.deleteAllSessions()
                },
                onDismiss = { viewModel.setShowDeleteAllSessionsConfirm(false) }
            )
        }

        uiState.deleteSessionId?.let { sessionId ->
            ConfirmDialog(
                title = "End Session",
                message = "Are you sure you want to end this session?",
                confirmLabel = "End Session",
                onConfirm = {
                    viewModel.setDeleteSessionId(null)
                    viewModel.deleteSession(sessionId)
                },
                onDismiss = { viewModel.setDeleteSessionId(null) }
            )
        }

        if (uiState.showClearCacheConfirm) {
            ConfirmDialog(
                title = "Clear Font Cache",
                message = "This will delete all cached subtitle fonts. Proceed?",
                confirmLabel = "Clear",
                onConfirm = {
                    viewModel.setShowClearCacheConfirm(false)
                    viewModel.clearFontCache()
                },
                onDismiss = { viewModel.setShowClearCacheConfirm(false) }
            )
        }

        uiState.deleteAnimeId?.let { animeId ->
            ConfirmDialog(
                title = "Delete Downloads",
                message = "Delete all downloaded episodes for \"${uiState.deleteAnimeTitle ?: "this anime"}\"?",
                confirmLabel = "Delete",
                onConfirm = {
                    viewModel.setDeleteAnime(null, null)
                    viewModel.deleteAnimeDownloads(animeId)
                },
                onDismiss = { viewModel.setDeleteAnime(null, null) }
            )
        }
    }
}

// ── Sidebar ─────────────────────────────────────────────────────────────────────
@Composable
private fun Sidebar(
    navItems: List<SettingsNavItem>,
    selectedTab: SettingsTab,
    onTabSelect: (SettingsTab) -> Unit,
    uiState: SettingsUiState,
    onLogout: () -> Unit,
    isLoggingOut: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(BG)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // No overall header as per user request
        Spacer(modifier = Modifier.height(8.dp))

        // Nav Items
        navItems.forEach { item ->
            val isSelected = selectedTab == item.tab
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) BG_CARD else Color.Transparent,
                animationSpec = tween(200)
            )
            val textColor = if (isSelected) TEXT else MUTED

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onTabSelect(item.tab) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    item.label,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Logout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !isLoggingOut) { onLogout() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Logout",
                color = Color(0xFFE50914),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        val uriHandler = LocalUriHandler.current
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { uriHandler.openUri("https://anisurge.lol/donate") }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Donate",
                color = Color(0xFFE91E63),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // App Stats
        val displayPlatform = (uiState.currentSession?.osName?.takeIf { it.isNotBlank() } ?: PlatformName)
            .let { p ->
                if (p.lowercase() == "macos") "macOS" 
                else p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

        AppStatsSection(
            version = "${uiState.currentSession?.clientVersion?.takeIf { it.isNotBlank() } ?: AppVersion}+$AppBuildNumber",
            platform = displayPlatform,
            userId = uiState.currentSession?.userId ?: "Not logged in",
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun AppStatItem(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MUTED, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, color = TEXT.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppStatsSection(
    version: String,
    platform: String,
    userId: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            "APP STATS",
            color = MUTED,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        AppStatItem("Client Version", version)
        AppStatItem("Platform", platform)
        AppStatItem("User ID", userId)
    }
}

// ── Mobile Settings List ───────────────────────────────────────────────────────
@Composable
private fun MobileSettingsList(
    navItems: List<SettingsNavItem>,
    uiState: SettingsUiState,
    onLogout: () -> Unit,
    onItemClick: (SettingsTab) -> Unit,
    isLoggingOut: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        // Profile Card at the Top
        if (uiState.isOffline && uiState.userProfile == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BG_CARD)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = MUTED,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Internet connection lost", color = MUTED, fontSize = 14.sp)
                    TextButton(
                        onClick = onRetry
                    ) {
                        Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (!uiState.isLoadingProfile && uiState.userProfile != null) {
            val user = uiState.userProfile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BG_CARD)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarUrl = user.effectiveAvatar
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, BORDER, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(BG_HOVER),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MUTED,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                user.displayName ?: user.username ?: "Anonymous",
                                color = TEXT,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (user.isEmailVerified == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                VerifiedBadge(size = 13.dp)
                            }
                        }
                        user.username?.let {
                            Text(
                                "@$it",
                                color = MUTED,
                                fontSize = 13.sp
                            )
                        }
                        user.joinDate?.let {
                            Text(
                                "Joined ${it.split("T").first()}",
                                color = MUTED,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        } else if (uiState.isLoadingProfile) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Menu Items
        navItems.forEach { item ->
            MobileSettingsItem(
                icon = item.icon,
                label = item.label,
                onClick = { onItemClick(item.tab) }
            )
        }

        if (!isAndroidTvPlatform) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BG_CARD)
                    .border(1.dp, BORDER, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "Connect to Android TV",
                        color = TEXT,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Scan the QR shown on your TV to sign in there.",
                        color = MUTED,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TvQrPairingAction()
                }
            }
        }

        // Logout
        MobileSettingsItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            label = "Logout",
            tint = Color(0xFFE50914),
            onClick = onLogout,
            isLoading = isLoggingOut
        )

        val uriHandler = LocalUriHandler.current
        
        MobileSettingsItem(
            icon = Icons.Default.Favorite,
            label = "Donate",
            tint = Color(0xFFE91E63),
            onClick = { uriHandler.openUri("https://anisurge.lol/donate") }
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(thickness = 1.dp, color = BORDER)
        Spacer(modifier = Modifier.height(16.dp))

        // App Stats
        val displayPlatform = (uiState.currentSession?.osName?.takeIf { it.isNotBlank() } ?: PlatformName)
            .let { p ->
                if (p.lowercase() == "macos") "macOS"
                else p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

        AppStatsSection(
            version = "${uiState.currentSession?.clientVersion?.takeIf { it.isNotBlank() } ?: AppVersion}+$AppBuildNumber",
            platform = displayPlatform,
            userId = uiState.currentSession?.userId ?: "Not logged in"
        )
    }
}

@Composable
private fun MobileSettingsItem(
    icon: ImageVector,
    label: String,
    tint: Color = TEXT,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = if (isLoading) ({}) else onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                label,
                color = tint,
                fontSize = 16.sp,
                fontWeight = if (isLoading) FontWeight.Medium else FontWeight.Normal
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MUTED,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Mobile Settings Detail ─────────────────────────────────────────────────────
@Composable
private fun MobileSettingsDetail(
    tab: SettingsTab,
    navItems: List<SettingsNavItem>,
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel
) {
    val navItem = navItems.find { it.tab == tab }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
    ) {
        // Header with back
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TEXT
                )
            }
            Text(
                navItem?.label ?: "",
                color = TEXT,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        HorizontalDivider(thickness = 1.dp, color = BORDER)

        // Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            when (tab) {
                is SettingsTab.Profile -> MobileProfileContent(
                uiState = uiState,
                onRetry = { viewModel.refresh() }
            )
                is SettingsTab.Preferences -> MobilePreferencesContent(
                    uiState = uiState,
                    onAutoPlayChange = viewModel::setAutoPlay,
                    onAutoNextChange = viewModel::setAutoNext,
                    onSkipIntroChange = viewModel::setSkipIntro,
                    onSkipOutroChange = viewModel::setSkipOutro,
                    onDefaultLangChange = viewModel::setDefaultLang,
                    onSyncPercentageChange = viewModel::setSyncPercentage,
                    onSubtitleSizeChange = viewModel::setSubtitleSize,
                    onDownloadPathChange = viewModel::setDownloadPath,
                    onSave = viewModel::savePreferences
                )
                is SettingsTab.Sessions -> MobileSessionsContent(
                    uiState = uiState,
                    onDeleteSession = { viewModel.setDeleteSessionId(it) },
                    onDeleteAllSessions = { viewModel.setShowDeleteAllSessionsConfirm(true) },
                    onLogout = onLogout
                )
                is SettingsTab.Security -> MobileSecurityContent(
                    uiState = uiState,
                    onToggleMfa = viewModel::toggleMfa,
                    onSetupTotp = viewModel::setupTotp,
                    onVerifyTotp = viewModel::verifyTotp,
                    onLoadRecoveryCodes = viewModel::loadRecoveryCodes,
                    onDismissRecoveryCodes = viewModel::dismissRecoveryCodes,
                    onDismissTotpSetup = viewModel::dismissTotpSetup,
                    onPasswordChange = viewModel::changePassword,
                    onCurrentPasswordChange = viewModel::setCurrentPassword,
                    onNewPasswordChange = viewModel::setNewPassword,
                    onConfirmPasswordChange = viewModel::setConfirmPassword
                )
                is SettingsTab.Sync -> MobileSyncContent(
                    uiState = uiState,
                    onConnect = { viewModel.onConnectAniList { uriHandler.openUri(it) } },
                    onDisconnect = { viewModel.setShowDisconnectConfirm(true) },
                    onImport = viewModel::importFromAniList,
                    onExport = viewModel::exportToAniList,
                    onCancel = viewModel::cancelSyncOperation,
                    onRefreshStatus = { viewModel.loadAniListStatus() }
                )
                is SettingsTab.Storage -> MobileStorageContent(
                    uiState = uiState,
                    onRefresh = viewModel::loadStorageInfo,
                    onClearFontCache = { viewModel.setShowClearCacheConfirm(true) },
                    onDeleteAnime = { id, title ->
                        viewModel.setDeleteAnime(id, title)
                    },
                    formatBytes = viewModel::formatBytes,
                    formatBytesCompact = viewModel::formatBytesCompact
                )
                is SettingsTab.Servers -> MobileServersContent(
                    uiState = uiState,
                    onReorder = viewModel::updateServerPriority,
                    onSave = viewModel::saveServerPriority,
                    onReset = viewModel::resetServerPriority
                )
                is SettingsTab.Notifications -> NotificationsTab(
                    enabled = uiState.notificationsEnabled,
                    hasChanges = uiState.hasNotificationPrefsChanges,
                    onEnabledChange = viewModel::setNotificationsEnabled,
                    onSave = viewModel::saveNotificationPreferences
                )
            }
        }
    }
}

// ── Content ─────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsContent(
    selectedTab: SettingsTab,
    uiState: SettingsUiState,
    navItems: List<SettingsNavItem>,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "settings_content",
        modifier = modifier
    ) { tab ->
        when (tab) {
            is SettingsTab.Profile -> ProfileTab(uiState = uiState, onRetry = { viewModel.refresh() })
            is SettingsTab.Preferences -> PreferencesTab(
                uiState = uiState,
                onAutoPlayChange = viewModel::setAutoPlay,
                onAutoNextChange = viewModel::setAutoNext,
                onSkipIntroChange = viewModel::setSkipIntro,
                onSkipOutroChange = viewModel::setSkipOutro,
                onDefaultLangChange = viewModel::setDefaultLang,
                onSyncPercentageChange = viewModel::setSyncPercentage,
                onSubtitleSizeChange = viewModel::setSubtitleSize,
                onDownloadPathChange = viewModel::setDownloadPath,
                onSave = viewModel::savePreferences
            )
            is SettingsTab.Sessions -> SessionsTab(
                uiState = uiState,
                onDeleteSession = { viewModel.setDeleteSessionId(it) },
                onDeleteAllSessions = { viewModel.setShowDeleteAllSessionsConfirm(true) },
                onLogout = onLogout
            )
            is SettingsTab.Security -> SecurityTab(
                uiState = uiState,
                onToggleMfa = viewModel::toggleMfa,
                onSetupTotp = viewModel::setupTotp,
                onVerifyTotp = viewModel::verifyTotp,
                onLoadRecoveryCodes = viewModel::loadRecoveryCodes,
                onDismissRecoveryCodes = viewModel::dismissRecoveryCodes,
                onDismissTotpSetup = viewModel::dismissTotpSetup,
                onPasswordChange = viewModel::changePassword,
                onCurrentPasswordChange = viewModel::setCurrentPassword,
                onNewPasswordChange = viewModel::setNewPassword,
                onConfirmPasswordChange = viewModel::setConfirmPassword
            )
            is SettingsTab.Sync -> SyncTab(
                uiState = uiState,
                onConnect = { viewModel.onConnectAniList { uriHandler.openUri(it) } },
                onDisconnect = { viewModel.setShowDisconnectConfirm(true) },
                onImport = viewModel::importFromAniList,
                onExport = viewModel::exportToAniList,
                onCancel = viewModel::cancelSyncOperation,
                onRefreshStatus = { viewModel.loadAniListStatus() }
            )
            is SettingsTab.Storage -> StorageTab(
                uiState = uiState,
                onRefresh = viewModel::loadStorageInfo,
                onClearFontCache = { viewModel.setShowClearCacheConfirm(true) },
                onDeleteAnime = { id, title ->
                    viewModel.setDeleteAnime(id, title)
                },
                formatBytes = viewModel::formatBytes,
                formatBytesCompact = viewModel::formatBytesCompact
            )
            is SettingsTab.Servers -> ServersTab(
                uiState = uiState,
                onReorder = viewModel::updateServerPriority,
                onSave = viewModel::saveServerPriority,
                onReset = viewModel::resetServerPriority
            )
            is SettingsTab.Notifications -> NotificationsTab(
                enabled = uiState.notificationsEnabled,
                hasChanges = uiState.hasNotificationPrefsChanges,
                onEnabledChange = viewModel::setNotificationsEnabled,
                onSave = viewModel::saveNotificationPreferences
            )
        }
    }
}

// ── Preferences Tab ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesTab(
    uiState: SettingsUiState,
    onAutoPlayChange: (Boolean) -> Unit,
    onAutoNextChange: (Boolean) -> Unit,
    onSkipIntroChange: (Boolean) -> Unit,
    onSkipOutroChange: (Boolean) -> Unit,
    onDefaultLangChange: (Boolean) -> Unit,
    onSyncPercentageChange: (Int) -> Unit,
    onSubtitleSizeChange: (Int) -> Unit,
    onDownloadPathChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val directoryPickerLauncher = rememberDirectoryPickerLauncher {
        it?.let { dir -> 
            val path = dir.absolutePath()
            to.kuudere.anisuge.platform.persistFolderPermission(path)
            onDownloadPathChange(path) 
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Large Title
        Text(
            "Preferences",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Two Column Layout
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            maxItemsInEachRow = 2,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Auto Play
            SettingCard(
                title = "Auto Play",
                description = "Automatically start playing videos when page loads",
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.preferences.autoPlay,
                    onCheckedChange = onAutoPlayChange,
                    label = "Enable Auto Play"
                )
            }

            // Auto Next
            SettingCard(
                title = "Auto Next",
                description = "Automatically play next episode when current ends",
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.preferences.autoNext,
                    onCheckedChange = onAutoNextChange,
                    label = "Enable Auto Next"
                )
            }

            // Skip Intro
            SettingCard(
                title = "Skip Intro",
                description = "Automatically skip anime intro sequences",
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.preferences.skipIntro,
                    onCheckedChange = onSkipIntroChange,
                    label = "Skip intro automatically"
                )
            }

            // Skip Outro
            SettingCard(
                title = "Skip Outro",
                description = "Automatically skip anime outro/ending sequences",
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.preferences.skipOutro,
                    onCheckedChange = onSkipOutroChange,
                    label = "Skip outro automatically"
                )
            }

            // Default Language
            SettingCard(
                title = "Default Language",
                description = "Use English dubbed audio when available",
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.preferences.defaultLang,
                    onCheckedChange = onDefaultLangChange,
                    label = "Default to English Dub"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sync Section - Full Width
        SettingCard(
            title = "Watch Progress Sync",
            description = "The watch percentage required to mark an episode as watched",
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${uiState.preferences.syncPercentage}%", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.preferences.syncPercentage.toFloat(),
                    onValueChange = { onSyncPercentageChange(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 49,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = BORDER
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SettingCard(
            title = "Subtitle Size",
            description = "Adjust caption text size for this device",
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("${uiState.subtitleSize}%", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.subtitleSize.toFloat(),
                    onValueChange = { onSubtitleSizeChange(it.toInt()) },
                    valueRange = 60f..200f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = BORDER
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Download Path Section - Full Width
        SettingCard(
            title = "Download Path",
            description = "Custom directory for your downloaded anime files",
            modifier = Modifier.fillMaxWidth()
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, BORDER, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPathValid = remember(uiState.downloadPath) {
                if (uiState.downloadPath.isBlank()) true
                else to.kuudere.anisuge.platform.isFolderWritable(uiState.downloadPath)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPathValid) to.kuudere.anisuge.platform.formatDisplayPath(uiState.downloadPath) else "Location Unavailable",
                    color = if (uiState.downloadPath.isBlank() || !isPathValid) MUTED else TEXT,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isPathValid) {
                    Text(
                        "Choose a folder with write access.",
                        color = Color.Red.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "Change",
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { directoryPickerLauncher.launch() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        }

        // Save Button
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSave,
            enabled = uiState.hasPreferencesChanges && !uiState.isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.hasPreferencesChanges) Color.White else BG_CARD,
                contentColor = if (uiState.hasPreferencesChanges) Color.Black else MUTED,
                disabledContainerColor = BG_CARD,
                disabledContentColor = MUTED
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.End)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Save Changes", fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Setting Card Component ──────────────────────────────────────────────────────
@Composable
private fun SettingCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(title, color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, color = MUTED, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BG_CARD)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TEXT, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = BORDER
            )
        )
    }
}

// ── Sessions Tab ─────────────────────────────────────────────────────────────────
@Composable
private fun SessionsTab(
    uiState: SettingsUiState,
    onDeleteSession: (String) -> Unit,
    onDeleteAllSessions: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Sessions",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Manage your active sessions across all devices",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            uiState.currentSession?.let { session ->
                Text("Current Session", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                SessionCard(session = session, isCurrent = true, onDelete = null)
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (uiState.sessions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Other Sessions", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = { onDeleteAllSessions() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBF80FF))
                    ) {
                        Text("End All", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                uiState.sessions.forEach { session ->
                    SessionCard(
                        session = session,
                        isCurrent = false,
                        onDelete = { onDeleteSession(session.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionInfoResponse,
    isCurrent: Boolean,
    onDelete: (() -> Unit)?
) {
    // Better device detection from multiple fields
    val clientName = session.clientName?.takeIf { it.isNotBlank() && it != "---" }
        ?: session.deviceModel?.takeIf { it.isNotBlank() }
        ?: "Unknown Device"

    val deviceIcon = when {
        clientName.contains("Mobile", true) -> Icons.Default.PhoneAndroid
        clientName.contains("Tablet", true) -> Icons.Default.TabletAndroid
        session.deviceName?.contains("Phone", true) == true -> Icons.Default.PhoneAndroid
        session.deviceName?.contains("Tablet", true) == true -> Icons.Default.TabletAndroid
        else -> Icons.Default.Computer
    }

    // Build location info from available fields
    val locationInfo = buildList {
        session.osName?.takeIf { it.isNotBlank() && it != "---" }?.let { add(it) }
        session.countryName?.takeIf { it.isNotBlank() && it != "---" }?.let { add(it) }
        session.ip?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" • ").takeIf { it.isNotEmpty() } ?: "Unknown location"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BG_CARD)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(deviceIcon, contentDescription = null, tint = MUTED, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        clientName,
                        color = TEXT,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        locationInfo,
                        color = MUTED,
                        fontSize = 12.sp
                    )
                }
            }

            if (isCurrent) {
                Text("Current", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            } else {
                onDelete?.let {
                    TextButton(
                        onClick = it,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBF80FF))
                    ) {
                        Text("End", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Security Tab ─────────────────────────────────────────────────────────────────
@Composable
private fun SecurityTab(
    uiState: SettingsUiState,
    onToggleMfa: (Boolean) -> Unit,
    onSetupTotp: () -> Unit,
    onVerifyTotp: (String) -> Unit,
    onLoadRecoveryCodes: () -> Unit,
    onDismissRecoveryCodes: () -> Unit,
    onDismissTotpSetup: () -> Unit,
    onPasswordChange: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
) {
    var showTotpDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Security",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Manage your account security and authentication",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // MFA Section
        SettingCard(
            title = "Two-Factor Authentication",
            description = "Add an extra layer of security to your account",
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (uiState.mfaStatus?.totpEnabled == true) "TOTP is enabled" else "TOTP is disabled",
                        color = TEXT,
                        fontSize = 14.sp
                    )
                    Switch(
                        checked = uiState.mfaStatus?.totpEnabled == true,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                onSetupTotp()
                                showTotpDialog = true
                            } else {
                                onToggleMfa(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.White.copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = BORDER
                        )
                    )
                }
                if (uiState.mfaStatus?.totpEnabled == true) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onLoadRecoveryCodes,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BORDER))
                    ) {
                        Text("View Recovery Codes")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Password Section - Inline Form
        SettingCard(
            title = "Change Password",
            description = "Update your account password. Must be at least 8 characters.",
            modifier = Modifier.fillMaxWidth()
        ) {
            PasswordChangeForm(
                currentPassword = uiState.currentPassword,
                newPassword = uiState.newPassword,
                confirmPassword = uiState.confirmPassword,
                isLoading = uiState.isChangingPassword,
                onCurrentPasswordChange = onCurrentPasswordChange,
                onNewPasswordChange = onNewPasswordChange,
                onConfirmPasswordChange = onConfirmPasswordChange,
                onSubmit = onPasswordChange
            )
        }

        // TOTP Setup Dialog
        if (showTotpDialog && uiState.totpSetupData != null) {
            TotpSetupDialog(
                totpData = uiState.totpSetupData!!,
                onDismiss = {
                    showTotpDialog = false
                    onDismissTotpSetup()
                },
                onVerify = { code ->
                    onVerifyTotp(code)
                    showTotpDialog = false
                }
            )
        }

        // Recovery Codes Dialog
        if (uiState.showRecoveryCodes) {
            RecoveryCodesDialog(
                codes = uiState.recoveryCodes,
                onDismiss = onDismissRecoveryCodes
            )
        }

    }
}

// ── About Tab (Desktop) ─────────────────────────────────────────────────────────
@Composable
private fun AboutTab() {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Large Title
        Text(
            "About",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // App Info Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BG_CARD)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Anisuge", color = TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("v$AppVersion", color = MUTED, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 1.dp, color = BORDER)
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Anisuge is a Kuudere client for streaming anime content.",
                    color = TEXT,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Stats Section
        Text(
            "App Stats",
            color = TEXT,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BG_CARD)
                .padding(16.dp)
        ) {
            Column {
                DesktopAboutStatRow("Hostname", "kuudere.to")
                HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 12.dp))
                DesktopAboutStatRow("Backend", "Kuudere API")
                HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 12.dp))
                DesktopAboutStatRow("Version", AppVersion)
            }
        }
    }
}

@Composable
private fun DesktopAboutStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MUTED, fontSize = 14.sp)
        Text(value, color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Sync Tab ────────────────────────────────────────────────────────────────────
@Composable
private fun SyncTab(
    uiState: SettingsUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onRefreshStatus: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth()) {
        // Large Title
        Text(
            "Sync",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Connect your AniList account to sync your watch progress",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (uiState.isLoadingAniList && !uiState.anilistConnected) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (uiState.anilistConnected && uiState.anilistProfile != null) {
            // Connected Profile Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BG_CARD)
            ) {
                Column {
                    // Banner image or gradient header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        // Show banner image if available, otherwise gradient
                        if (uiState.anilistProfile?.bannerImage != null) {
                            AsyncImage(
                                model = uiState.anilistProfile.bannerImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Dark overlay for better text contrast
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF000000),
                                                Color(0xFF000000)
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Profile section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar overlapping the banner
                            uiState.anilistProfile?.avatar?.large?.let { avatarUrl ->
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .offset(y = (-40).dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(4.dp, BG_CARD, RoundedCornerShape(12.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.offset(y = (-8).dp)) {
                                Text(
                                    uiState.anilistProfile?.name ?: "",
                                    color = TEXT,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Connected",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = if (uiState.isLoadingAniList) ({}) else onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE50914)),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE50914).copy(alpha = 0.5f))),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (uiState.isLoadingAniList) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Disconnect", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Section
            uiState.anilistProfile?.statistics?.anime?.let { stats ->
                Text(
                    "Your Anime Stats",
                    color = TEXT,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        value = "${stats.count}",
                        label = "Total Anime",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "${stats.episodesWatched}",
                        label = "Episodes",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "${(stats.minutesWatched / 60 / 24).coerceAtLeast(1)}",
                        label = "Days Watched",
                        modifier = Modifier.weight(1f)
                    )
                    if (stats.meanScore > 0) {
                        StatCard(
                            value = "${stats.meanScore}",
                            label = "Mean Score",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Import/Export Section
                Text(
                    "Sync Actions",
                    color = TEXT,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Import Progress
                if (uiState.isImportingFromAniList) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BG_CARD)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Importing from AniList...",
                            color = TEXT,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.importProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = BG_HOVER
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            uiState.importStatus,
                            color = MUTED,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Export Progress
                if (uiState.isExportingToAniList) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BG_CARD)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Exporting to AniList...",
                            color = TEXT,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.exportProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = BG_HOVER
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            uiState.exportStatus,
                            color = MUTED,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Sync Log
                if (uiState.syncLog.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF000000))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        uiState.syncLog.forEach { line ->
                            Text(
                                line,
                                color = when {
                                    "ERROR" in line || "✗" in line -> Color(0xFFBF80FF)
                                    "✓" in line -> Color(0xFF22C55E)
                                    "SKIP" in line -> Color(0xFFEAB308)
                                    else -> Color(0xFF9CA3AF)
                                },
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (uiState.isImportingFromAniList || uiState.isExportingToAniList) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBF80FF)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBF80FF).copy(alpha = 0.5f))),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Import/Export Buttons
                if (!uiState.isImportingFromAniList && !uiState.isExportingToAniList) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BORDER)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import from AniList")
                        }

                        OutlinedButton(
                            onClick = onExport,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BORDER)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export to AniList")
                        }
                    }
                }
            }
        } else {
            // Not connected state - centered
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // AniList logo
                    Image(
                        painter = painterResource(Res.drawable.anilist),
                        contentDescription = "AniList",
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Connect to AniList",
                        color = TEXT,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sync your watch progress and anime lists",
                        color = MUTED,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 32.dp).padding(top = 12.dp)
                    ) {
                        Text("Connect Account", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    
                    TextButton(
                        onClick = onRefreshStatus,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = MUTED)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Status", color = MUTED, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BG_CARD)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            color = TEXT,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = MUTED,
            fontSize = 12.sp
        )
    }
}

// ── Mobile Sync Content ─────────────────────────────────────────────────────────
@Composable
private fun MobileSyncContent(
    uiState: SettingsUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onRefreshStatus: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.isLoadingAniList && !uiState.anilistConnected) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (uiState.anilistConnected && uiState.anilistProfile != null) {
            // Profile Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BG_CARD)
            ) {
                Column {
                    // Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        // Show banner image if available, otherwise gradient
                        if (uiState.anilistProfile?.bannerImage != null) {
                            AsyncImage(
                                model = uiState.anilistProfile.bannerImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Dark overlay for better text contrast
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF000000),
                                                Color(0xFF000000)
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Profile info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            uiState.anilistProfile?.avatar?.large?.let { avatarUrl ->
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .offset(y = (-32).dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(3.dp, BG_CARD, RoundedCornerShape(12.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.offset(y = (-4).dp)) {
                                Text(
                                    uiState.anilistProfile?.name ?: "",
                                    color = TEXT,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Connected",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = if (uiState.isLoadingAniList) ({}) else onDisconnect,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE50914))
                        ) {
                            if (uiState.isLoadingAniList) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Disconnect", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stats
            uiState.anilistProfile?.statistics?.anime?.let { stats ->
                Text(
                    "Your Stats",
                    color = TEXT,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BG_CARD)
                        .padding(16.dp)
                ) {
                    MobileSyncStatRow("Total Anime", "${stats.count}")
                    HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 12.dp))
                    MobileSyncStatRow("Episodes Watched", "${stats.episodesWatched}")
                    HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 12.dp))
                    MobileSyncStatRow("Minutes Watched", "${stats.minutesWatched}")
                    if (stats.meanScore > 0) {
                        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 12.dp))
                        MobileSyncStatRow("Mean Score", "${stats.meanScore}")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Import/Export Section
                Text(
                    "Sync Actions",
                    color = TEXT,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Import Progress
                if (uiState.isImportingFromAniList) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BG_CARD)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Importing from AniList...",
                            color = TEXT,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.importProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = BG_HOVER
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            uiState.importStatus,
                            color = MUTED,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Export Progress
                if (uiState.isExportingToAniList) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BG_CARD)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Exporting to AniList...",
                            color = TEXT,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.exportProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = BG_HOVER
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            uiState.exportStatus,
                            color = MUTED,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Sync Log (mobile)
                if (uiState.syncLog.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF000000))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        uiState.syncLog.forEach { line ->
                            Text(
                                line,
                                color = when {
                                    "ERROR" in line || "\u2717" in line -> Color(0xFFBF80FF)
                                    "\u2713" in line -> Color(0xFF22C55E)
                                    "SKIP" in line -> Color(0xFFEAB308)
                                    else -> Color(0xFF9CA3AF)
                                },
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (uiState.isImportingFromAniList || uiState.isExportingToAniList) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBF80FF)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBF80FF).copy(alpha = 0.5f))),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Import/Export Buttons
                if (!uiState.isImportingFromAniList && !uiState.isExportingToAniList) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BORDER)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import from AniList")
                        }

                        OutlinedButton(
                            onClick = onExport,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BORDER)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export to AniList")
                        }
                    }
                }
            }
        } else {
            // Not connected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(Res.drawable.anilist),
                        contentDescription = "AniList",
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Connect to AniList",
                        color = TEXT,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sync your watch progress",
                        color = MUTED,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 12.dp)
                    ) {
                        Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }

                    TextButton(
                        onClick = onRefreshStatus,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = MUTED)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Status", color = MUTED, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSyncStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MUTED, fontSize = 14.sp)
        Text(value, color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Mobile Content Composables ──────────────────────────────────────────────────

@Composable
private fun MobilePreferencesContent(
    uiState: SettingsUiState,
    onAutoPlayChange: (Boolean) -> Unit,
    onAutoNextChange: (Boolean) -> Unit,
    onSkipIntroChange: (Boolean) -> Unit,
    onSkipOutroChange: (Boolean) -> Unit,
    onDefaultLangChange: (Boolean) -> Unit,
    onSyncPercentageChange: (Int) -> Unit,
    onSubtitleSizeChange: (Int) -> Unit,
    onDownloadPathChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val directoryPickerLauncher = rememberDirectoryPickerLauncher {
        it?.let { dir -> 
            val path = dir.absolutePath()
            to.kuudere.anisuge.platform.persistFolderPermission(path)
            onDownloadPathChange(path) 
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        MobileSettingRow(
            title = "Auto Play",
            description = "Automatically start playing videos",
            checked = uiState.preferences.autoPlay,
            onCheckedChange = onAutoPlayChange
        )
        MobileSettingRow(
            title = "Auto Next",
            description = "Automatically play next episode",
            checked = uiState.preferences.autoNext,
            onCheckedChange = onAutoNextChange
        )
        MobileSettingRow(
            title = "Skip Intro",
            description = "Automatically skip anime intros",
            checked = uiState.preferences.skipIntro,
            onCheckedChange = onSkipIntroChange
        )
        MobileSettingRow(
            title = "Skip Outro",
            description = "Automatically skip anime outros",
            checked = uiState.preferences.skipOutro,
            onCheckedChange = onSkipOutroChange
        )
        MobileSettingRow(
            title = "Default to English Dub",
            description = "Use English dubbed audio when available",
            checked = uiState.preferences.defaultLang,
            onCheckedChange = onDefaultLangChange
        )

        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))

        Text(
            "Watch Progress Sync",
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "The watch percentage required to mark an episode as watched",
            color = MUTED,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = uiState.preferences.syncPercentage.toFloat(),
                onValueChange = { onSyncPercentageChange(it.toInt()) },
                valueRange = 50f..100f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = BORDER
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text("${uiState.preferences.syncPercentage}%", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))

        Text(
            "Subtitle Size",
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Adjust caption text size for this device",
            color = MUTED,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = uiState.subtitleSize.toFloat(),
                onValueChange = { onSubtitleSizeChange(it.toInt()) },
                valueRange = 60f..200f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = BORDER
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text("${uiState.subtitleSize}%", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))

        Text(
            "Download Path",
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Custom directory for your downloaded anime files",
            color = MUTED,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, BORDER, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPathValid = remember(uiState.downloadPath) {
                if (uiState.downloadPath.isBlank()) true
                else to.kuudere.anisuge.platform.isFolderWritable(uiState.downloadPath)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPathValid) to.kuudere.anisuge.platform.formatDisplayPath(uiState.downloadPath) else "Location Restricted",
                    color = if (uiState.downloadPath.isBlank() || !isPathValid) MUTED else TEXT,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isPathValid) {
                    Text(
                        "Choose a folder with write access.",
                        color = Color.Red.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Change",
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { directoryPickerLauncher.launch() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (uiState.hasPreferencesChanges) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MobileSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TEXT, fontSize = 16.sp)
            Text(description, color = MUTED, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = BORDER
            )
        )
    }
}

@Composable
private fun MobileSessionsContent(
    uiState: SettingsUiState,
    onDeleteSession: (String) -> Unit,
    onDeleteAllSessions: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            uiState.currentSession?.let { session ->
                Text("Current Session", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                SessionCard(session = session, isCurrent = true, onDelete = null)
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (uiState.sessions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Other Sessions", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = { onDeleteAllSessions() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBF80FF))
                    ) {
                        Text("End All", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                uiState.sessions.forEach { session ->
                    SessionCard(
                        session = session,
                        isCurrent = false,
                        onDelete = { onDeleteSession(session.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Text("No other active sessions", color = MUTED, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MobileSecurityContent(
    uiState: SettingsUiState,
    onToggleMfa: (Boolean) -> Unit,
    onSetupTotp: () -> Unit,
    onVerifyTotp: (String) -> Unit,
    onLoadRecoveryCodes: () -> Unit,
    onDismissRecoveryCodes: () -> Unit,
    onDismissTotpSetup: () -> Unit,
    onPasswordChange: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
) {
    var showTotpDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // MFA Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Two-Factor Authentication", color = TEXT, fontSize = 16.sp)
                Text(
                    if (uiState.mfaStatus?.totpEnabled == true) "Enabled" else "Disabled",
                    color = MUTED,
                    fontSize = 13.sp
                )
            }
            Switch(
                checked = uiState.mfaStatus?.totpEnabled == true,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        onSetupTotp()
                        showTotpDialog = true
                    } else {
                        onToggleMfa(false)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = BORDER
                )
            )
        }

        if (uiState.mfaStatus?.totpEnabled == true) {
            OutlinedButton(
                onClick = onLoadRecoveryCodes,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BORDER)),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("View Recovery Codes")
            }
        }

        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))

        // Password Section
        Text("Change Password", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("Update your account password", color = MUTED, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        PasswordChangeForm(
            currentPassword = uiState.currentPassword,
            newPassword = uiState.newPassword,
            confirmPassword = uiState.confirmPassword,
            isLoading = uiState.isChangingPassword,
            onCurrentPasswordChange = onCurrentPasswordChange,
            onNewPasswordChange = onNewPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onSubmit = onPasswordChange
        )

        // TOTP Setup Dialog
        if (showTotpDialog && uiState.totpSetupData != null) {
            TotpSetupDialog(
                totpData = uiState.totpSetupData!!,
                onDismiss = {
                    showTotpDialog = false
                    onDismissTotpSetup()
                },
                onVerify = { code ->
                    onVerifyTotp(code)
                    showTotpDialog = false
                }
            )
        }

        // Recovery Codes Dialog
        if (uiState.showRecoveryCodes) {
            RecoveryCodesDialog(
                codes = uiState.recoveryCodes,
                onDismiss = onDismissRecoveryCodes
            )
        }
    }
}

// ── About Content ───────────────────────────────────────────────────────────────
@Composable
private fun MobileAboutContent() {
    Column(modifier = Modifier.fillMaxWidth()) {
        // App Icon / Logo area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // App name/logo placeholder
                Text(
                    "Anisuge",
                    color = TEXT,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "v$AppVersion",
                    color = MUTED,
                    fontSize = 14.sp
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = BORDER)

        // App Stats Section
        Text(
            "APP STATS",
            color = MUTED,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        AboutStatItem("Hostname", "kuudere.to")
        AboutStatItem("Backend", "Kuudere API")
        AboutStatItem("Version", AppVersion)

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider(thickness = 1.dp, color = BORDER)

        // Credits / Info
        Text(
            "ABOUT",
            color = MUTED,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Text(
            "Anisuge is a Kuudere client for streaming anime content.",
            color = TEXT,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun AboutStatItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MUTED, fontSize = 14.sp)
        Text(value, color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────────
@Composable
private fun TotpSetupDialog(
    totpData: to.kuudere.anisuge.data.models.TotpSetupData,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BG_CARD,
        title = { Text("Setup Authenticator", color = TEXT) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Scan the QR code with your authenticator app", color = MUTED, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                AsyncImage(
                    model = totpData.qrCode,
                    contentDescription = "TOTP QR Code",
                    modifier = Modifier.size(200.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Or enter the secret manually:", color = MUTED, fontSize = 13.sp)
                Text(
                    totpData.secret,
                    color = TEXT,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Verification Code") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TEXT,
                        unfocusedTextColor = TEXT,
                        focusedLabelColor = MUTED,
                        unfocusedLabelColor = MUTED,
                        focusedBorderColor = TEXT,
                        unfocusedBorderColor = BORDER
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(code) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MUTED)
            }
        }
    )
}

@Composable
private fun RecoveryCodesDialog(
    codes: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BG_CARD,
        title = { Text("Recovery Codes", color = TEXT) },
        text = {
            Column {
                Text("Save these codes in a secure location. They can be used to recover your account if you lose access to your authenticator.", color = MUTED, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BG)
                        .padding(16.dp)
                ) {
                    Column {
                        codes.chunked(2).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { code ->
                                    Text(
                                        code,
                                        color = TEXT,
                                        fontSize = 14.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun PasswordChangeForm(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    isLoading: Boolean,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val isValid = newPassword == confirmPassword && newPassword.length >= 8

    Column {
        OutlinedTextField(
            value = currentPassword,
            onValueChange = onCurrentPasswordChange,
            label = { Text("Current Password") },
            singleLine = true,
            visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showCurrent = !showCurrent }) {
                    Icon(
                        if (showCurrent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MUTED
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TEXT,
                unfocusedTextColor = TEXT,
                focusedLabelColor = MUTED,
                unfocusedLabelColor = MUTED,
                focusedBorderColor = TEXT,
                unfocusedBorderColor = BORDER
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = { Text("New Password") },
            singleLine = true,
            visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showNew = !showNew }) {
                    Icon(
                        if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MUTED
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TEXT,
                unfocusedTextColor = TEXT,
                focusedLabelColor = MUTED,
                unfocusedLabelColor = MUTED,
                focusedBorderColor = TEXT,
                unfocusedBorderColor = BORDER
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password") },
            singleLine = true,
            isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
            visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showConfirm = !showConfirm }) {
                    Icon(
                        if (showConfirm) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MUTED
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TEXT,
                unfocusedTextColor = TEXT,
                focusedLabelColor = MUTED,
                unfocusedLabelColor = MUTED,
                focusedBorderColor = TEXT,
                unfocusedBorderColor = BORDER
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = !isLoading && isValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Changing...")
            } else {
                Text("Change Password")
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────────
private fun formatRelativeTime(timestamp: String): String {
    return try {
        val instant = kotlinx.datetime.Instant.parse(timestamp)
        val now = kotlinx.datetime.Clock.System.now()
        val diff = now - instant

        when {
            diff.inWholeMinutes < 1 -> "just now"
            diff.inWholeMinutes < 60 -> "${diff.inWholeMinutes}m ago"
            diff.inWholeHours < 24 -> "${diff.inWholeHours}h ago"
            diff.inWholeDays < 30 -> "${diff.inWholeDays}d ago"
            else -> "${diff.inWholeDays / 30}mo ago"
        }
    } catch (e: Exception) {
        timestamp
    }
}

// ── Storage Tab ────────────────────────────────────────────────────────────────
@Composable
private fun StorageTab(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onClearFontCache: () -> Unit,
    onDeleteAnime: (String, String) -> Unit,
    formatBytes: (Long) -> String,
    formatBytesCompact: (Long) -> String,
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Title
        Text(
            "Storage",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Manage downloaded content and cache",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (uiState.isLoadingStorage) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            val storageInfo = uiState.storageInfo
            val downloadInfo = uiState.downloadStorageInfo

            if (storageInfo != null) {
                // Storage Overview Card
                StorageOverviewCard(storageInfo, formatBytes, formatBytesCompact)

                Spacer(modifier = Modifier.height(32.dp))

                // Downloads Section
                if (downloadInfo != null && downloadInfo.animeFolders.isNotEmpty()) {
                    Text(
                        "Downloads",
                        color = TEXT,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    downloadInfo.animeFolders.forEach { anime ->
                        AnimeStorageCard(
                            anime = anime,
                            formatBytes = formatBytes,
                            onDelete = { onDeleteAnime(anime.animeId, anime.title) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                } else {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BG_CARD)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No downloads yet",
                            color = MUTED,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Cache Actions
                Text(
                    "Cache Management",
                    color = TEXT,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearFontCache,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE50914)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE50914).copy(alpha = 0.5f))),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear Font Cache (${formatBytesCompact(storageInfo.fontCache.size)})")
                    }

                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(BORDER)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    storageInfo: StorageInfo,
    formatBytes: (Long) -> String,
    formatBytesCompact: (Long) -> String,
) {
    val totalSpace = storageInfo.totalUsed + storageInfo.freeSpace
    val usedPercent = if (totalSpace > 0) (storageInfo.totalUsed * 100 / totalSpace).toInt() else 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BG_CARD)
            .padding(24.dp)
    ) {
        Column {
            // Total usage
            Text(
                "Storage Usage",
                color = TEXT,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BG_HOVER)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Downloads
                    val downloadsPercent = if (storageInfo.totalUsed > 0) {
                        (storageInfo.downloads.size.toFloat() / storageInfo.totalUsed)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(downloadsPercent.coerceAtLeast(0.01f))
                            .background(Color(0xFF3B82F6))
                    )
                    // Font Cache
                    val fontPercent = if (storageInfo.totalUsed > 0) {
                        (storageInfo.fontCache.size.toFloat() / storageInfo.totalUsed)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(fontPercent.coerceAtLeast(0.01f))
                            .background(Color(0xFF8B5CF6))
                    )
                    // Settings
                    val settingsPercent = if (storageInfo.totalUsed > 0) {
                        (storageInfo.settings.size.toFloat() / storageInfo.totalUsed)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(settingsPercent.coerceAtLeast(0.01f))
                            .background(Color(0xFF10B981))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Text(
                "${formatBytes(storageInfo.totalUsed)} used of ${formatBytes(totalSpace)} ($usedPercent%)",
                color = MUTED,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StorageLegendItem(
                    color = Color(0xFF3B82F6),
                    label = "Downloads",
                    value = formatBytesCompact(storageInfo.downloads.size)
                )
                StorageLegendItem(
                    color = Color(0xFF8B5CF6),
                    label = "Font Cache",
                    value = formatBytesCompact(storageInfo.fontCache.size)
                )
                StorageLegendItem(
                    color = Color(0xFF10B981),
                    label = "Settings",
                    value = formatBytesCompact(storageInfo.settings.size)
                )
            }
        }
    }
}

@Composable
private fun StorageLegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, color = MUTED, fontSize = 12.sp)
            Text(value, color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AnimeStorageCard(
    anime: to.kuudere.anisuge.data.models.AnimeFolderInfo,
    formatBytes: (Long) -> String,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BG_CARD)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    anime.title,
                    color = TEXT,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${anime.episodeCount} episodes • ${formatBytes(anime.size)}",
                    color = MUTED,
                    fontSize = 13.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFE50914)
                )
            }
        }
    }
}

// ── Mobile Storage Content ─────────────────────────────────────────────────────
@Composable
private fun MobileStorageContent(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onClearFontCache: () -> Unit,
    onDeleteAnime: (String, String) -> Unit,
    formatBytes: (Long) -> String,
    formatBytesCompact: (Long) -> String,
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.isLoadingStorage) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            val storageInfo = uiState.storageInfo
            val downloadInfo = uiState.downloadStorageInfo

            if (storageInfo != null) {
                // Storage Overview
                MobileStorageOverview(storageInfo, formatBytes, formatBytesCompact)

                Spacer(modifier = Modifier.height(24.dp))

                // Downloads Section
                if (downloadInfo != null && downloadInfo.animeFolders.isNotEmpty()) {
                    Text(
                        "Downloads (${downloadInfo.animeFolders.size} anime)",
                        color = TEXT,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    downloadInfo.animeFolders.forEach { anime ->
                        AnimeStorageCard(
                            anime = anime,
                            formatBytes = formatBytes,
                            onDelete = { onDeleteAnime(anime.animeId, anime.title) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BG_CARD)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No downloads yet",
                            color = MUTED,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Cache Actions
                 OutlinedButton(
                    onClick = onClearFontCache,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE50914)),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE50914).copy(alpha = 0.5f))),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear Font Cache (${formatBytesCompact(storageInfo.fontCache.size)})")
                }
            }
        }
    }
}

@Composable
private fun MobileStorageOverview(
    storageInfo: StorageInfo,
    formatBytes: (Long) -> String,
    formatBytesCompact: (Long) -> String,
) {
    val totalSpace = storageInfo.totalUsed + storageInfo.freeSpace
    val usedPercent = if (totalSpace > 0) (storageInfo.totalUsed * 100 / totalSpace).toInt() else 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BG_CARD)
            .padding(20.dp)
    ) {
        Column {
            Text(
                formatBytes(storageInfo.totalUsed),
                color = TEXT,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "used of ${formatBytes(totalSpace)}",
                color = MUTED,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BG_HOVER)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(
                                if (storageInfo.totalUsed > 0)
                                    (storageInfo.downloads.size.toFloat() / storageInfo.totalUsed).coerceAtLeast(0.01f)
                                else 0.01f
                            )
                            .background(Color(0xFF3B82F6))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(
                                if (storageInfo.totalUsed > 0)
                                    (storageInfo.fontCache.size.toFloat() / storageInfo.totalUsed).coerceAtLeast(0.01f)
                                else 0.01f
                            )
                            .background(Color(0xFF8B5CF6))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(
                                if (storageInfo.totalUsed > 0)
                                    (storageInfo.settings.size.toFloat() / storageInfo.totalUsed).coerceAtLeast(0.01f)
                                else 0.01f
                            )
                            .background(Color(0xFF10B981))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageLegendItem(
                    color = Color(0xFF3B82F6),
                    label = "Downloads",
                    value = formatBytesCompact(storageInfo.downloads.size)
                )
                StorageLegendItem(
                    color = Color(0xFF8B5CF6),
                    label = "Font Cache",
                    value = formatBytesCompact(storageInfo.fontCache.size)
                )
                StorageLegendItem(
                    color = Color(0xFF10B981),
                    label = "Settings",
                    value = formatBytesCompact(storageInfo.settings.size)
                )
            }
        }
    }
}

// ── Servers Tab ────────────────────────────────────────────────────────────────
@Composable
private fun ServersTab(
    uiState: SettingsUiState,
    onReorder: (List<String>) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header with title and reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Servers",
                color = TEXT,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )

            // Reset button (outlined style like the design)
            OutlinedButton(
                onClick = onReset,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.3f))),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Reset", fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "Drag and drop the servers to change the order in which they are used to find streams.",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        if (uiState.isLoadingServers || uiState.availableServers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            // Compute sorted servers based on priority
            val serverList = remember(uiState.availableServers, uiState.serverPriority) {
                val priority = uiState.serverPriority
                if (priority.isEmpty()) {
                    // Default sort: zen2, zen, then others
                    uiState.availableServers.sortedWith(compareBy(
                        { it.id != "zen2" },
                        { it.id != "zen" },
                        { it.id }
                    ))
                } else {
                    // User priority
                    uiState.availableServers.sortedBy { server ->
                        val index = priority.indexOf(server.id)
                        if (index == -1) Int.MAX_VALUE else index
                    }
                }
            }

            // Local mutable state for reordering
            var localServerList by remember(serverList) {
                mutableStateOf(serverList)
            }

            // Update when priority changes
            LaunchedEffect(uiState.serverPriority) {
                localServerList = serverList
            }

            // Auto-save on reorder
            val autoSaveReorder = { newList: List<to.kuudere.anisuge.data.models.ServerInfo> ->
                localServerList = newList
                onReorder(newList.map { it.id })
                onSave()
            }

            // Track drag state
            var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffset by remember { mutableStateOf(0f) }
            val itemHeightPx = 58f // card height + spacing in pixels

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                localServerList.forEachIndexed { currentIndex, server ->
                    val isDragging = draggingItemIndex == currentIndex
                    val visualOffset = if (isDragging) dragOffset.dp else 0.dp

                    DraggableServerItem(
                        server = server,
                        isDragging = isDragging,
                        offsetY = visualOffset,
                        onDragStart = { draggingItemIndex = currentIndex },
                        onDrag = { delta ->
                            dragOffset += delta

                            if (draggingItemIndex != null) {
                                val currentDragIndex = draggingItemIndex!!
                                // Calculate target index based on drag distance
                                val dragItems = (dragOffset / itemHeightPx).toInt()
                                val targetIndex = (currentDragIndex + dragItems)
                                    .coerceIn(0, localServerList.size - 1)

                                if (targetIndex != currentDragIndex) {
                                    val newList = localServerList.toMutableList()
                                    val item = newList.removeAt(currentDragIndex)
                                    newList.add(targetIndex, item)
                                    localServerList = newList
                                    draggingItemIndex = targetIndex
                                    // Adjust offset to account for the position change
                                    dragOffset = dragOffset - (dragItems * itemHeightPx)
                                }
                            }
                        },
                        onDragEnd = {
                            draggingItemIndex = null
                            dragOffset = 0f
                            autoSaveReorder(localServerList)
                        },
                        onMoveUp = {
                            if (currentIndex > 0) {
                                val newList = localServerList.toMutableList()
                                val item = newList.removeAt(currentIndex)
                                newList.add(currentIndex - 1, item)
                                autoSaveReorder(newList)
                            }
                        },
                        onMoveDown = {
                            if (currentIndex < localServerList.size - 1) {
                                val newList = localServerList.toMutableList()
                                val item = newList.removeAt(currentIndex)
                                newList.add(currentIndex + 1, item)
                                autoSaveReorder(newList)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableServerItem(
    server: to.kuudere.anisuge.data.models.ServerInfo,
    isDragging: Boolean,
    offsetY: androidx.compose.ui.unit.Dp,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val elevation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 8f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150)
    )

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = androidx.compose.animation.core.tween(150)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
            .scale(scale)
            .zIndex(if (isDragging) 1f else 0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDragging) BG_HOVER else BG_CARD,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drag handle icon (6 dots) - now actually draggable
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MUTED,
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                onDrag(dragAmount.y)
                            }
                        )
                    }
                    .clickable { /* Consume clicks */ }
            )

            // Server name
            Text(
                server.displayName,
                color = TEXT,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            // Reorder buttons (up/down arrows)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = MUTED,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = MUTED,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Mobile Servers Content ─────────────────────────────────────────────────────
@Composable
private fun MobileServersContent(
    uiState: SettingsUiState,
    onReorder: (List<String>) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Drag and drop the servers to change the order in which they are used to find streams.",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
        )

        if (uiState.isLoadingServers || uiState.availableServers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            // Compute sorted servers based on priority
            val serverList = remember(uiState.availableServers, uiState.serverPriority) {
                val priority = uiState.serverPriority
                if (priority.isEmpty()) {
                    // Default sort: zen2, zen, then others
                    uiState.availableServers.sortedWith(compareBy(
                        { it.id != "zen2" },
                        { it.id != "zen" },
                        { it.id }
                    ))
                } else {
                    // User priority
                    uiState.availableServers.sortedBy { server ->
                        val index = priority.indexOf(server.id)
                        if (index == -1) Int.MAX_VALUE else index
                    }
                }
            }

            // Local mutable state for reordering
            var localServerList by remember(serverList) {
                mutableStateOf(serverList)
            }

            // Update when priority changes
            LaunchedEffect(uiState.serverPriority) {
                localServerList = serverList
            }

            // Auto-save on reorder
            val autoSaveReorder = { newList: List<to.kuudere.anisuge.data.models.ServerInfo> ->
                localServerList = newList
                onReorder(newList.map { it.id })
                onSave()
            }

            // Track drag state
            var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffset by remember { mutableStateOf(0f) }
            val itemHeightPxMobile = 58f

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                localServerList.forEachIndexed { currentIndex, server ->
                    val isDragging = draggingItemIndex == currentIndex
                    val visualOffset = if (isDragging) dragOffset.dp else 0.dp

                    DraggableServerItem(
                        server = server,
                        isDragging = isDragging,
                        offsetY = visualOffset,
                        onDragStart = { draggingItemIndex = currentIndex },
                        onDrag = { delta ->
                            dragOffset += delta

                            if (draggingItemIndex != null) {
                                val currentDragIndex = draggingItemIndex!!
                                val dragItems = (dragOffset / itemHeightPxMobile).toInt()
                                val targetIndex = (currentDragIndex + dragItems)
                                    .coerceIn(0, localServerList.size - 1)

                                if (targetIndex != currentDragIndex) {
                                    val newList = localServerList.toMutableList()
                                    val item = newList.removeAt(currentDragIndex)
                                    newList.add(targetIndex, item)
                                    localServerList = newList
                                    draggingItemIndex = targetIndex
                                    dragOffset = dragOffset - (dragItems * itemHeightPxMobile)
                                }
                            }
                        },
                        onDragEnd = {
                            draggingItemIndex = null
                            dragOffset = 0f
                            autoSaveReorder(localServerList)
                        },
                        onMoveUp = {
                            if (currentIndex > 0) {
                                val newList = localServerList.toMutableList()
                                val item = newList.removeAt(currentIndex)
                                newList.add(currentIndex - 1, item)
                                autoSaveReorder(newList)
                            }
                        },
                        onMoveDown = {
                            if (currentIndex < localServerList.size - 1) {
                                val newList = localServerList.toMutableList()
                                val item = newList.removeAt(currentIndex)
                                newList.add(currentIndex + 1, item)
                                autoSaveReorder(newList)
                            }
                        }
                    )
                }
            }
        }

        // Reset button at the bottom
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.3f))),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Reset to Defaults", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Profile Tab ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(
    uiState: SettingsUiState,
    onRetry: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Profile",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Your account information and profile details",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (uiState.isOffline && uiState.userProfile == null) {
            OfflineState(
                onRetry = onRetry,
                isLoading = uiState.isLoadingProfile,
                modifier = Modifier.fillMaxWidth().height(400.dp)
            )
        } else if (uiState.isLoadingProfile) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (uiState.errorMessage != null && uiState.userProfile == null) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.errorMessage, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) { 
                        Text("Retry", color = Color.Black) 
                    }
                }
            }
        } else if (uiState.userProfile == null) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("Please log in to view your profile", color = MUTED)
            }
        } else {
            val user = uiState.userProfile!!
            
            // Profile Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BG_CARD)
                    .padding(32.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar
                        val avatarUrl = user.effectiveAvatar
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, BORDER, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(BG_HOVER),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MUTED,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user.displayName ?: user.username ?: "Anonymous",
                                    color = TEXT,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (user.isEmailVerified == true) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    VerifiedBadge(size = 18.dp)
                                }
                            }
                            Text(
                                "@${user.username}",
                                color = MUTED,
                                fontSize = 16.sp
                            )
                            if (!user.location.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MUTED, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(user.location, color = MUTED, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    HorizontalDivider(color = BORDER)
                    Spacer(modifier = Modifier.height(32.dp))

                    // Bio
                    if (!user.bio.isNullOrBlank()) {
                        Text("About", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(user.bio, color = MUTED, fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    // Details Grid
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        maxItemsInEachRow = 3
                    ) {
                        ProfileDetailItem("Email", user.email ?: "Not provided")
                        ProfileDetailItem("Joined", user.joinDate?.let { it.split("T").first() } ?: user.ago ?: "Unknown")
                        ProfileDetailItem("Timezone", "UTC") // Hardcoded from example but could be dynamic
                            }
                        }
                    }

                }
            }
        }

@Composable
private fun ProfileDetailItem(label: String, value: String) {
    Column {
        Text(label, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Mobile Profile Content ───────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileProfileContent(
    uiState: SettingsUiState,
    onRetry: () -> Unit = {}
) {
    if (uiState.isOffline && uiState.userProfile == null) {
        OfflineState(
            onRetry = onRetry,
            isLoading = uiState.isLoadingProfile,
            modifier = Modifier.fillMaxWidth().height(400.dp)
        )
    } else if (uiState.isLoadingProfile) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else if (uiState.userProfile != null) {
        val user = uiState.userProfile
        Column(modifier = Modifier.fillMaxWidth()) {
            // Profile Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val avatarUrl = user.effectiveAvatar
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(3.dp, BORDER, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(BG_HOVER),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MUTED,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    user.displayName ?: user.username ?: "Anonymous",
                    color = TEXT,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "@${user.username}",
                    color = MUTED,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bio
            if (!user.bio.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BG_CARD)
                        .padding(16.dp)
                ) {
                    Column {
                        Text("About", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(user.bio, color = MUTED, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Mobile Details List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BG_CARD)
            ) {
                Column {
                    MobileProfileInfoItem("Email", user.email ?: "Not provided")
                    HorizontalDivider(color = BORDER, modifier = Modifier.padding(horizontal = 16.dp))
                    MobileProfileInfoItem("Joined", user.joinDate?.let { it.split("T").first() } ?: user.ago ?: "Unknown")
                    HorizontalDivider(color = BORDER, modifier = Modifier.padding(horizontal = 16.dp))
                    MobileProfileInfoItem("Location", user.location ?: "Not provided")
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Failed to load profile", color = Color(0xFFBF80FF))
        }
    }
}

@Composable
private fun MobileProfileInfoItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MUTED, fontSize = 14.sp)
        Text(value, color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun VerifiedBadge(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified",
            tint = Color.Black,
            modifier = Modifier.size(size * 0.65f)
        )
    }
}
