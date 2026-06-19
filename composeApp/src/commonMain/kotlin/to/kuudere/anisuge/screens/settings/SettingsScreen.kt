package to.kuudere.anisuge.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import to.kuudere.anisuge.ui.OfflineState
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.material3.AlertDialog
import to.kuudere.anisuge.player.ColorPreset
import to.kuudere.anisuge.player.PlayerEnhancementOptions
import to.kuudere.anisuge.player.PlayerEnhancementSettings
import to.kuudere.anisuge.player.ShaderCost
import to.kuudere.anisuge.player.ShaderPreset
import to.kuudere.anisuge.player.PlayerUtilitySettings
import to.kuudere.anisuge.platform.isAndroidPlatform
import to.kuudere.anisuge.platform.isDesktopPlatform
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import anisurge.composeapp.generated.resources.Res
import anisurge.composeapp.generated.resources.ic_discord
import anisurge.composeapp.generated.resources.ic_telegram
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.painter.Painter

import to.kuudere.anisuge.data.models.Comment
import to.kuudere.anisuge.data.models.StorageInfo
import to.kuudere.anisuge.data.models.AnimeFolderInfo
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.platform.AppVersion
import to.kuudere.anisuge.platform.AppBuildNumber
import to.kuudere.anisuge.platform.PlatformName
import to.kuudere.anisuge.platform.isDesktopPlatform
import to.kuudere.anisuge.ui.ConfirmDialog
import to.kuudere.anisuge.i18n.AppStrings
import to.kuudere.anisuge.i18n.AppLocale
import to.kuudere.anisuge.i18n.LocalAppStrings
import to.kuudere.anisuge.screens.settings.SettingsTab
import to.kuudere.anisuge.platform.openUrl
import to.kuudere.anisuge.utils.rememberDownloadDirectoryPicker
import to.kuudere.anisuge.theme.AppThemeId
import to.kuudere.anisuge.theme.AppColors
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

// ── Colors ── theme-driven (see theme/AppColors.kt) ──────────────────────────────
private val BG: Color get() = AppColors.background
private val BG_CARD: Color get() = AppColors.surface
private val BG_HOVER: Color get() = AppColors.surfaceVariant
private val BORDER: Color get() = AppColors.border
private val MUTED: Color get() = AppColors.textMuted
private val TEXT: Color get() = AppColors.text

private const val ANISURGE_DISCORD_URL = "https://discord.gg/yR4T2dbeCx"
private const val ANISURGE_TELEGRAM_URL = "https://t.me/anisurge"
private val PREMIUM_BENEFITS = listOf(
    "No chat cooldown",
    "50-episode season downloads",
    "Faster parallel downloads",
    "300 Berries per purchase",
    "Lifetime animated/MP4 PFP",
    "Premium chat badge and gradient name",
    "40% off shop items",
)

// ── Data ────────────────────────────────────────────────────────────────────────
data class SettingsNavItem(
    val tab: SettingsTab,
    val label: String,
    val icon: ImageVector,
    /** Berries tab uses the Beli mark (฿) instead of [icon]. */
    val useBeliIcon: Boolean = false,
)

@Composable
private fun SupportAndGiveawaysSection(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BG_CARD)
            .border(1.dp, BORDER, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            "Support & Giveaways",
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Join our community for updates, support, and giveaways.",
            color = MUTED,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        SupportBrandLinkRow(
            icon = painterResource(Res.drawable.ic_discord),
            label = "Discord",
            contentDescription = "Discord",
            onClick = { uriHandler.openUri(ANISURGE_DISCORD_URL) },
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            thickness = 1.dp,
            color = BORDER,
        )
        SupportBrandLinkRow(
            icon = painterResource(Res.drawable.ic_telegram),
            label = "Telegram",
            contentDescription = "Telegram",
            onClick = { uriHandler.openUri(ANISURGE_TELEGRAM_URL) },
        )
    }
}

@Composable
private fun SupportBrandLinkRow(
    icon: Painter,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                color = TEXT,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MUTED,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Stable AnimatedContent key so detail tab survives until the exit transition finishes. */
private sealed class MobileSettingsTarget {
    data object List : MobileSettingsTarget()
    data object Account : MobileSettingsTarget()
    data class Detail(val tab: SettingsTab) : MobileSettingsTarget()
}

// ── Main Screen ─────────────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit,
    onRefresh: () -> Unit = {},
    isLoggingOut: Boolean = false,
    initialTab: SettingsTab? = null,
    onExit: () -> Unit = {},
    onOpenHomeLayout: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var selectedTab by remember { mutableStateOf<SettingsTab>(initialTab ?: SettingsTab.Preferences) }

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            selectedTab = initialTab
        }
    }



    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(localizedSettingsMessage(it, strings))
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(localizedSettingsMessage(it, strings))
            viewModel.clearMessages()
            // Refresh global session on success for security/profile stuff
            if (it.contains("Password", ignoreCase = true) ||
                it.contains("Profile", ignoreCase = true)
            ) {
                onRefresh()
            }
        }
    }

    LaunchedEffect(selectedTab) {
        viewModel.onTabSelected(selectedTab)
    }


    val navItems = buildList {
        add(SettingsNavItem(SettingsTab.Profile, strings.profile, Icons.Default.Person))
        add(SettingsNavItem(SettingsTab.Shop, "Store", Icons.Default.ShoppingBag))
        add(SettingsNavItem(SettingsTab.Berries, "Berries", Icons.Default.Star, useBeliIcon = true))
        add(SettingsNavItem(SettingsTab.Preferences, strings.preferences, Icons.Default.Settings))
        add(SettingsNavItem(SettingsTab.Appearance, strings.appearance, Icons.Default.Visibility))
        add(SettingsNavItem(SettingsTab.Sync, strings.sync, Icons.Default.Sync))
        add(SettingsNavItem(SettingsTab.Connect, "Connect", Icons.Default.Link))
        // Community — not ready yet
        // add(SettingsNavItem(SettingsTab.Community, "Community", Icons.Default.Sync))
        add(SettingsNavItem(SettingsTab.Servers, strings.servers, Icons.Default.Dns))
        add(SettingsNavItem(SettingsTab.Storage, strings.storage, Icons.Default.Storage))
        if (!isDesktopPlatform) {
            add(SettingsNavItem(SettingsTab.Notifications, strings.notifications, Icons.Default.Notifications))
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (uiState.errorMessage != null) Color(0xFFBF80FF) else Color(0xFF1B5E20),
                    contentColor = AppColors.onAccent
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
                        onBuyPremium = { viewModel.startPremiumCheckout(uriHandler::openUri) },
                        modifier = Modifier.width(260.dp)
                    )

                    VerticalDivider(thickness = 1.dp, color = BORDER)

                    // Content Area - Centered with max width
                    val isShopTab = selectedTab is SettingsTab.Shop
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!isShopTab) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier = if (isShopTab) Modifier.fillMaxSize() else Modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
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
                                onOpenHomeLayout = onOpenHomeLayout,
                                modifier = Modifier
                                    .then(
                                        if (isShopTab) {
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        } else {
                                            Modifier.widthIn(max = 900.dp)
                                        },
                                    )
                                    .padding(horizontal = 48.dp, vertical = if (isShopTab) 16.dp else 40.dp),
                            )
                        }
                    }
                }
            } else {
                // Mobile: List menu with navigation to detail screens
                val showProfileAccount = uiState.showProfileAccount
                val showDetail = uiState.mobileSettingsDetailTab
                val mobileTarget = when {
                    showProfileAccount -> MobileSettingsTarget.Account
                    showDetail != null -> MobileSettingsTarget.Detail(showDetail)
                    else -> MobileSettingsTarget.List
                }

                AnimatedContent(
                    targetState = mobileTarget,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "mobile_settings",
                ) { screen ->
                    when (screen) {
                        MobileSettingsTarget.Account -> MobileProfileAccountDetail(
                            uiState = uiState,
                            viewModel = viewModel,
                            onBack = { viewModel.closeProfileAccount() },
                        )

                        is MobileSettingsTarget.Detail -> MobileSettingsDetail(
                            tab = screen.tab,
                            navItems = navItems,
                            uiState = uiState,
                            onBack = { viewModel.closeMobileSettingsDetail() },
                            onLogout = onLogout,
                            viewModel = viewModel,
                            onOpenHomeLayout = onOpenHomeLayout,
                        )

                        MobileSettingsTarget.List -> MobileSettingsList(
                            navItems = navItems.filter { it.tab != SettingsTab.Profile },
                            uiState = uiState,
                            onLogout = onLogout,
                            isLoggingOut = isLoggingOut,
                            onBuyPremium = { viewModel.startPremiumCheckout(uriHandler::openUri) },
                            onRetry = { viewModel.refresh() },
                            onProfileClick = { viewModel.openProfileAccount() },
                            onItemClick = {
                                viewModel.openMobileSettingsDetail(it)
                                viewModel.onTabSelected(it)
                            },
                        )
                    }
                }
            }
        }

        // Confirmation Dialogs
        if (uiState.showClearCacheConfirm) {
            ConfirmDialog(
                title = strings.clearFontCache,
                message = strings.clearFontCacheMessage,
                confirmLabel = strings.clear,
                onConfirm = {
                    viewModel.setShowClearCacheConfirm(false)
                    viewModel.clearFontCache()
                },
                onDismiss = { viewModel.setShowClearCacheConfirm(false) }
            )
        }

        uiState.deleteAnimeId?.let { animeId ->
            ConfirmDialog(
                title = strings.deleteDownloads,
                message = strings.deleteDownloadsMessage(uiState.deleteAnimeTitle ?: strings.thisAnime),
                confirmLabel = strings.delete,
                onConfirm = {
                    viewModel.setDeleteAnime(null, null)
                    viewModel.deleteAnimeDownloads(animeId)
                },
                onDismiss = { viewModel.setDeleteAnime(null, null) }
            )
        }
    }
}

private fun localizedSettingsMessage(message: String, strings: AppStrings): String = when (message) {
    "Settings saved successfully" -> strings.preferencesSavedSuccessfully
    "Failed to save settings" -> strings.failedToSavePreferences
    "Failed to load settings" -> strings.failedToLoadPreferences
    "Failed to load user profile" -> strings.failedToLoadUserProfile
    "Server priority saved" -> strings.serverPrioritySaved
    "Reset to default priority" -> strings.resetToDefaultPriority
    else -> message
}

@Composable
private fun SettingsNavLeadingIcon(
    item: SettingsNavItem,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    if (item.useBeliIcon) {
        BerryIcon(size = size, color = tint)
    } else {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
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
    onBuyPremium: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(BG)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // No overall header as per user request
        Spacer(modifier = Modifier.height(8.dp))

        PremiumSidebarRow(
            uiState = uiState,
            onBuyPremium = onBuyPremium,
        )

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
                SettingsNavLeadingIcon(item = item, tint = textColor, size = 18.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    item.label,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }

        SupportAndGiveawaysSection(
            modifier = Modifier.padding(vertical = 8.dp),
        )

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
                CircularProgressIndicator(color = TEXT, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                strings.logout,
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
        val displayPlatform = PlatformName
            .let { p ->
                if (p.lowercase() == "macos") "macOS"
                else p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

        AppStatsSection(
            version = "$AppVersion+$AppBuildNumber",
            platform = displayPlatform,
            userId = uiState.userProfile?.id ?: "Not logged in",
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun PremiumSidebarRow(
    uiState: SettingsUiState,
    onBuyPremium: () -> Unit,
) {
    if (uiState.userProfile?.isPremium == true) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFD54F).copy(alpha = 0.08f))
            .clickable(enabled = !uiState.isStartingPremiumCheckout) { onBuyPremium() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.isStartingPremiumCheckout) {
            CircularProgressIndicator(color = Color(0xFFFFD54F), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            if (uiState.userProfile?.isPremium == true) "Extend Premium" else "Buy Premium",
            color = Color(0xFFFFD54F),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
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
    onProfileClick: () -> Unit = {},
    isLoggingOut: Boolean = false,
    onBuyPremium: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 156.dp)
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
                        Text("Retry", color = TEXT, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (!uiState.isLoadingProfile && uiState.userProfile != null) {
            ProfileSummaryCard(
                user = uiState.userProfile,
                modifier = Modifier.padding(vertical = 16.dp),
                onClick = onProfileClick,
                showChevron = true,
            )
        } else if (uiState.isLoadingProfile) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.userProfile?.isPremium != true) {
            MobileSettingsItem(
                icon = Icons.Default.WorkspacePremium,
                label = "Buy Premium",
                tint = Color(0xFFFFD54F),
                isLoading = uiState.isStartingPremiumCheckout,
                onClick = onBuyPremium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFD54F).copy(alpha = 0.07f))
                .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            PremiumBenefitsList()
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Menu Items
        navItems.forEach { item ->
            MobileSettingsItem(
                icon = item.icon,
                label = item.label,
                useBeliIcon = item.useBeliIcon,
                onClick = { onItemClick(item.tab) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        SupportAndGiveawaysSection()
        Spacer(modifier = Modifier.height(8.dp))

        // Logout
        MobileSettingsItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            label = "Logout",
            tint = Color(0xFFE50914),
            onClick = onLogout,
            isLoading = isLoggingOut
        )

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
        val displayPlatform = PlatformName
            .let { p ->
                if (p.lowercase() == "macos") "macOS"
                else p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

        AppStatsSection(
            version = "$AppVersion+$AppBuildNumber",
            platform = displayPlatform,
            userId = uiState.userProfile?.id ?: "Not logged in"
        )
    }
}

@Composable
private fun MobileSettingsItem(
    icon: ImageVector,
    label: String,
    useBeliIcon: Boolean = false,
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
                    color = AppColors.accent,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else if (useBeliIcon) {
                BerryIcon(size = 22.dp, color = tint)
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
    viewModel: SettingsViewModel,
    onOpenHomeLayout: () -> Unit,
) {
    val navItem = navItems.find { it.tab == tab }
    val uriHandler = LocalUriHandler.current
    val pickPfp = to.kuudere.anisuge.platform.rememberProfileImagePicker(viewModel::onCustomPfpPicked)
    val isShopTab = tab is SettingsTab.Shop

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .then(
                if (!isShopTab) Modifier.verticalScroll(rememberScrollState()) else Modifier,
            ),
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
                .then(
                    if (isShopTab) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = if (isShopTab) 24.dp else 156.dp),
        ) {
            when (tab) {
                is SettingsTab.Profile -> MobileProfileContent(
                    uiState = uiState,
                    onRetry = { viewModel.refresh() },
                    onPickCustomPfp = pickPfp,
                    onEquipFrame = viewModel::equipShopFrame,
                    onEditProfile = viewModel::openProfileAccount,
                    onBuyPremium = { viewModel.startPremiumCheckout(uriHandler::openUri) },
                )

                is SettingsTab.Shop -> ShopSettingsTab(
                    uiState = uiState,
                    onRefresh = viewModel::loadShop,
                    onLoadMore = viewModel::loadMoreShop,
                    onKindChange = viewModel::setShopKind,
                    onPurchase = viewModel::purchaseShopItem,
                    modifier = Modifier.fillMaxSize(),
                )

                is SettingsTab.Berries -> BerriesSettingsTab(
                    uiState = uiState,
                    onCodeChange = viewModel::setRedeemCodeDraft,
                    onRedeem = viewModel::redeemShopCode,
                    onClaimDaily = viewModel::claimDailyReward,
                    onBuyPremium = { viewModel.startBerryPurchase(uriHandler::openUri) },
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
                    onAppLocaleChange = viewModel::setAppLocale,
                    onPlayerEnhancementsChange = viewModel::setPlayerEnhancements,
                    onResetPlayerEnhancements = viewModel::resetPlayerEnhancements,
                    onPlayerUtilitiesChange = viewModel::setPlayerUtilities,
                    onResetPlayerUtilities = viewModel::resetPlayerUtilities,
                    onSave = viewModel::saveSettings
                )

                is SettingsTab.Appearance -> AppearanceTab(
                    uiState = uiState,
                    onFloatingBottomNavChange = viewModel::setFloatingBottomNav,
                    onLiquidGlassBottomNavChange = viewModel::setLiquidGlassBottomNav,
                    onExpandedHeroCarouselChange = viewModel::setExpandedHeroCarousel,
                    onQuickActionMenuChange = viewModel::setQuickActionMenu,
                    onPreferRomajiAnimeTitlesChange = viewModel::setPreferRomajiAnimeTitles,
                    onShowFullAnimeTitlesChange = viewModel::setShowFullAnimeTitles,
                    onLegacyScheduleUiChange = viewModel::setLegacyScheduleUi,
                    onThemeSelected = viewModel::setThemeId,
                    onOpenHomeLayout = onOpenHomeLayout,
                )

                is SettingsTab.Sync -> SyncTab(
                    uiState = uiState,
                    onConnectMal = { viewModel.connectMal { url -> openUrl(url) } },
                    onDisconnectMal = viewModel::disconnectMal,
                    onConnectAnilist = { viewModel.connectAnilist { url -> openUrl(url) } },
                    onDisconnectAnilist = viewModel::disconnectAnilist,
                    onImportFromMAL = viewModel::importLibraryFromMal,
                    onImportFromAniList = viewModel::importLibraryFromAniList,
                    onSyncToMAL = viewModel::syncAllToMAL,
                    onSyncToAniList = viewModel::syncAllToAniList,
                )

                is SettingsTab.Connect -> ConnectTab(
                    uiState = uiState,
                    onConnectReanime = viewModel::connectReanime,
                    onDisconnectReanime = viewModel::disconnectReanime,
                    onSyncLibrary = viewModel::syncLibraryWithReanime,
                    onImportReanime = viewModel::importLibraryFromReanime,
                    onExportReanime = viewModel::exportLibraryToReanime,
                    onConnectLunar = { viewModel.connectLunar { url -> openUrl(url) } },
                    onDisconnectLunar = viewModel::disconnectLunar,
                    onImportLunar = viewModel::importLibraryFromLunar,
                    onExportLunar = viewModel::exportLibraryToLunar,
                )

                is SettingsTab.Community -> {
                    // Community tab hidden — not yet ready (restore CommunityTab when shipping)
                    Box(Modifier.fillMaxSize())
                    /* CommunityTab(
                        uiState = uiState,
                        onRefresh = viewModel::refreshCommunity,
                        onSortChange = viewModel::setCommunitySort,
                        onCategoryChange = viewModel::setCommunityCategory,
                        onLeaderboardPeriodChange = viewModel::setCommunityLeaderboardPeriod,
                        onLoadMore = viewModel::loadMoreCommunityPosts,
                        onVote = viewModel::voteCommunityPost,
                        onDraftTitleChange = viewModel::setCommunityDraftTitle,
                        onDraftContentChange = viewModel::setCommunityDraftContent,
                        onDraftCategoryChange = viewModel::setCommunityDraftCategory,
                        onDraftFlairChange = viewModel::setCommunityDraftFlair,
                        onDraftSpoilerChange = viewModel::setCommunityDraftSpoiler,
                        onCreatePost = viewModel::createCommunityPost,
                        onOpenPost = viewModel::openCommunityPostDetail,
                        onDismissPostDetail = viewModel::dismissCommunityPostDetail,
                        onCommentDraftChange = viewModel::setCommunityDetailCommentDraft,
                        onCommentSpoilerChange = viewModel::setCommunityDetailCommentSpoiler,
                        onSubmitComment = viewModel::submitCommunityPostComment,
                        onImageClick = viewModel::showCommunityFullscreenImage,
                        onDismissFullscreenImage = viewModel::dismissCommunityFullscreenImage,
                    ) */
                }

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
    onOpenHomeLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Box(modifier = modifier.fillMaxSize()) {
        when (selectedTab) {
            is SettingsTab.Profile -> ProfileTab(
                uiState = uiState,
                onRetry = { viewModel.refresh() },
                onPickCustomPfp = viewModel::onCustomPfpPicked,
                onOpenAccount = viewModel::openProfileAccount,
                onCloseAccount = viewModel::closeProfileAccount,
                onUsernameChange = viewModel::setUsernameDraft,
                onSaveUsername = viewModel::saveUsername,
                onBioChange = viewModel::setBioDraft,
                onWebsiteChange = viewModel::setWebsiteDraft,
                onSaveProfileDetails = viewModel::saveProfileDetails,
                onCurrentPasswordChange = viewModel::setCurrentPassword,
                onNewPasswordChange = viewModel::setNewPassword,
                onConfirmPasswordChange = viewModel::setConfirmPassword,
                onChangePassword = viewModel::changePassword,
                onEquipFrame = viewModel::equipShopFrame,
                onChatProfilePrivacyChange = viewModel::setChatProfilePrivate,
                onBuyPremium = { viewModel.startPremiumCheckout(uriHandler::openUri) },
            )

            is SettingsTab.Shop -> ShopSettingsTab(
                uiState = uiState,
                onRefresh = viewModel::loadShop,
                onLoadMore = viewModel::loadMoreShop,
                onKindChange = viewModel::setShopKind,
                onPurchase = viewModel::purchaseShopItem,
                modifier = modifier.fillMaxSize(),
            )

            is SettingsTab.Berries -> BerriesSettingsTab(
                uiState = uiState,
                onCodeChange = viewModel::setRedeemCodeDraft,
                onRedeem = viewModel::redeemShopCode,
                onClaimDaily = viewModel::claimDailyReward,
                onBuyPremium = { viewModel.startBerryPurchase(uriHandler::openUri) },
            )

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
                onAppLocaleChange = viewModel::setAppLocale,
                onPlayerEnhancementsChange = viewModel::setPlayerEnhancements,
                onResetPlayerEnhancements = viewModel::resetPlayerEnhancements,
                onPlayerUtilitiesChange = viewModel::setPlayerUtilities,
                onResetPlayerUtilities = viewModel::resetPlayerUtilities,
                onSave = viewModel::saveSettings
            )

            is SettingsTab.Appearance -> AppearanceTab(
                uiState = uiState,
                onFloatingBottomNavChange = viewModel::setFloatingBottomNav,
                onLiquidGlassBottomNavChange = viewModel::setLiquidGlassBottomNav,
                onExpandedHeroCarouselChange = viewModel::setExpandedHeroCarousel,
                onQuickActionMenuChange = viewModel::setQuickActionMenu,
                onPreferRomajiAnimeTitlesChange = viewModel::setPreferRomajiAnimeTitles,
                onShowFullAnimeTitlesChange = viewModel::setShowFullAnimeTitles,
                onLegacyScheduleUiChange = viewModel::setLegacyScheduleUi,
                onThemeSelected = viewModel::setThemeId,
                onOpenHomeLayout = onOpenHomeLayout,
            )

            is SettingsTab.Sync -> SyncTab(
                uiState = uiState,
                onConnectMal = { viewModel.connectMal { url -> openUrl(url) } },
                onDisconnectMal = viewModel::disconnectMal,
                onConnectAnilist = { viewModel.connectAnilist { url -> openUrl(url) } },
                onDisconnectAnilist = viewModel::disconnectAnilist,
                onImportFromMAL = viewModel::importLibraryFromMal,
                onImportFromAniList = viewModel::importLibraryFromAniList,
                onSyncToMAL = viewModel::syncAllToMAL,
                onSyncToAniList = viewModel::syncAllToAniList,
            )

            is SettingsTab.Connect -> ConnectTab(
                uiState = uiState,
                onConnectReanime = viewModel::connectReanime,
                onDisconnectReanime = viewModel::disconnectReanime,
                onSyncLibrary = viewModel::syncLibraryWithReanime,
                onImportReanime = viewModel::importLibraryFromReanime,
                onExportReanime = viewModel::exportLibraryToReanime,
                onConnectLunar = { viewModel.connectLunar { url -> openUrl(url) } },
                onDisconnectLunar = viewModel::disconnectLunar,
                onImportLunar = viewModel::importLibraryFromLunar,
                onExportLunar = viewModel::exportLibraryToLunar,
            )

            is SettingsTab.Community -> {
                Box(Modifier.fillMaxSize())
                /* CommunityTab(
                    uiState = uiState,
                    onRefresh = viewModel::refreshCommunity,
                    onSortChange = viewModel::setCommunitySort,
                    onCategoryChange = viewModel::setCommunityCategory,
                    onLeaderboardPeriodChange = viewModel::setCommunityLeaderboardPeriod,
                    onLoadMore = viewModel::loadMoreCommunityPosts,
                    onVote = viewModel::voteCommunityPost,
                    onDraftTitleChange = viewModel::setCommunityDraftTitle,
                    onDraftContentChange = viewModel::setCommunityDraftContent,
                    onDraftCategoryChange = viewModel::setCommunityDraftCategory,
                    onDraftFlairChange = viewModel::setCommunityDraftFlair,
                    onDraftSpoilerChange = viewModel::setCommunityDraftSpoiler,
                    onCreatePost = viewModel::createCommunityPost,
                    onOpenPost = viewModel::openCommunityPostDetail,
                    onDismissPostDetail = viewModel::dismissCommunityPostDetail,
                    onCommentDraftChange = viewModel::setCommunityDetailCommentDraft,
                    onCommentSpoilerChange = viewModel::setCommunityDetailCommentSpoiler,
                    onSubmitComment = viewModel::submitCommunityPostComment,
                    onImageClick = viewModel::showCommunityFullscreenImage,
                    onDismissFullscreenImage = viewModel::dismissCommunityFullscreenImage,
                ) */
            }

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
@Composable
private fun AppearanceTab(
    uiState: SettingsUiState,
    onFloatingBottomNavChange: (Boolean) -> Unit,
    onLiquidGlassBottomNavChange: (Boolean) -> Unit,
    onExpandedHeroCarouselChange: (Boolean) -> Unit,
    onQuickActionMenuChange: (Boolean) -> Unit,
    onPreferRomajiAnimeTitlesChange: (Boolean) -> Unit,
    onShowFullAnimeTitlesChange: (Boolean) -> Unit,
    onLegacyScheduleUiChange: (Boolean) -> Unit,
    onThemeSelected: (AppThemeId) -> Unit,
    onOpenHomeLayout: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            strings.appearance,
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        SettingCard(
            title = "Theme presets",
            description = "Pick a theme. It instantly recolors the whole app — backgrounds, cards, text and accents.",
            modifier = Modifier.fillMaxWidth()
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppThemeId.entries.forEach { theme ->
                    ThemePresetChip(
                        theme = theme,
                        selected = uiState.themeId == theme,
                        onClick = { onThemeSelected(theme) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingCard(
            title = strings.animeTitlesDisplay,
            description = strings.animeTitlesDisplayDescription,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingToggle(
                    checked = uiState.preferRomajiAnimeTitles,
                    onCheckedChange = onPreferRomajiAnimeTitlesChange,
                    label = strings.animeTitlesPreferJapanese
                )
                SettingToggle(
                    checked = uiState.showFullAnimeTitles,
                    onCheckedChange = onShowFullAnimeTitlesChange,
                    label = strings.animeTitlesShowFull
                )
                Text(
                    text = strings.animeTitlesShowFullDescription,
                    color = MUTED,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingCard(
            title = "Home Screen",
            description = "Customize how the home screen and schedule tab look.",
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingToggle(
                    checked = uiState.expandedHeroCarousel,
                    onCheckedChange = onExpandedHeroCarouselChange,
                    label = strings.expandedHeroCarousel
                )
                SettingToggle(
                    checked = uiState.quickActionMenu,
                    onCheckedChange = onQuickActionMenuChange,
                    label = "Use quick action menu"
                )
                Text(
                    text = if (uiState.quickActionMenu) "Top bar uses the compact menu for Surge2Gether, downloads, and notifications." else "Top bar uses the old separate Surge2Gether and downloads buttons.",
                    color = MUTED,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Button(
                    onClick = onOpenHomeLayout,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                ) {
                    Text(strings.homeLayout)
                }
                SettingToggle(
                    checked = uiState.legacyScheduleUi,
                    onCheckedChange = onLegacyScheduleUiChange,
                    label = "Use old schedule layout"
                )
                Text(
                    text = if (uiState.legacyScheduleUi) "Calendar uses the classic compact list." else "Calendar uses the new release-guide layout.",
                    color = MUTED,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingCard(
            title = strings.mobileNavigation,
            description = strings.mobileNavigationDescription,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingToggle(
                    checked = uiState.floatingBottomNav,
                    onCheckedChange = onFloatingBottomNavChange,
                    label = strings.floatingBottomNavigation
                )
                SettingToggle(
                    checked = uiState.floatingBottomNav && uiState.liquidGlassBottomNav,
                    onCheckedChange = onLiquidGlassBottomNavChange,
                    label = strings.liquidGlassFloatingStyle,
                    enabled = uiState.floatingBottomNav
                )
                Text(
                    text = when {
                        !uiState.floatingBottomNav -> strings.currentStyleNormalBar
                        uiState.liquidGlassBottomNav -> strings.currentStyleLiquidGlass
                        else -> strings.currentStyleFloatingPill
                    },
                    color = MUTED,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ThemePresetChip(
    theme: AppThemeId,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AppColors.accent else BG_HOVER)
            .border(
                width = 1.dp,
                color = if (selected) AppColors.accent else BORDER,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(themePreviewColor(theme))
                .border(1.dp, Color.Black.copy(alpha = 0.18f), CircleShape)
        )
        Text(
            theme.label,
            color = if (selected) AppColors.onAccent else TEXT,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun themePreviewColor(theme: AppThemeId): Color = when (theme) {
    AppThemeId.Light -> Color(0xFFF5F6F8)
    AppThemeId.Default -> Color.White
    AppThemeId.Amoled -> Color(0xFF2A2A2A)
    AppThemeId.Purple -> Color(0xFFBF80FF)
    AppThemeId.Red -> Color(0xFFE50914)
    AppThemeId.Ocean -> Color(0xFF38BDF8)
    AppThemeId.Midnight -> Color(0xFF818CF8)
    AppThemeId.HighContrast -> Color(0xFFFFFF00)
}

@Composable
private fun SyncTab(
    uiState: SettingsUiState,
    onConnectMal: () -> Unit,
    onDisconnectMal: () -> Unit,
    onConnectAnilist: () -> Unit,
    onDisconnectAnilist: () -> Unit,
    onImportFromMAL: () -> Unit,
    onImportFromAniList: () -> Unit,
    onSyncToMAL: () -> Unit,
    onSyncToAniList: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            strings.sync,
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Link MAL and/or AniList to import lists and sync progress.",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        TrackingSection(
            uiState = uiState,
            onConnectMal = onConnectMal,
            onDisconnectMal = onDisconnectMal,
            onConnectAnilist = onConnectAnilist,
            onDisconnectAnilist = onDisconnectAnilist,
        )

        Spacer(modifier = Modifier.height(24.dp))

        val trackingBusy =
            uiState.isSyncingMal ||
                    uiState.isSyncingAnilist ||
                    uiState.isImportingMal ||
                    uiState.isImportingAnilist ||
                    uiState.isOffline
        SettingCard(
            title = "Tracker import",
            description = "Bring MAL/AniList lists into Anisurge, or push Anisurge progress back out.",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TrackerSyncButton(
                    label = "Import from MAL",
                    loading = uiState.isImportingMal,
                    enabled = uiState.malConnected && !trackingBusy,
                    onClick = onImportFromMAL,
                )
                TrackerSyncButton(
                    label = "Import from AniList",
                    loading = uiState.isImportingAnilist,
                    enabled = uiState.anilistConnected && !trackingBusy,
                    onClick = onImportFromAniList,
                )
                HorizontalDivider(color = BORDER)
                TrackerSyncButton(
                    label = "Sync to MAL",
                    loading = uiState.isSyncingMal,
                    enabled = uiState.malConnected && !trackingBusy,
                    onClick = onSyncToMAL,
                )
                TrackerSyncButton(
                    label = "Sync to AniList",
                    loading = uiState.isSyncingAnilist,
                    enabled = uiState.anilistConnected && !trackingBusy,
                    onClick = onSyncToAniList,
                )

                if (uiState.isImportingMal || uiState.isImportingAnilist) {
                    val total = uiState.trackingImportTotal
                    val progress = if (total > 0) {
                        (uiState.trackingImportCurrent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF6C4AB6),
                        trackColor = BORDER.copy(alpha = 0.3f),
                    )
                    Text(
                        text = if (total > 0) {
                            "Importing ${uiState.trackingImportCurrent} / $total"
                        } else {
                            "Preparing import..."
                        },
                        color = MUTED,
                        fontSize = 12.sp,
                    )
                    uiState.trackingImportDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            text = detail,
                            color = TEXT,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerSyncButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6C4AB6),
            disabledContainerColor = BG_HOVER,
            contentColor = AppColors.onAccent,
            disabledContentColor = MUTED,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AppColors.onAccent,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CommunityTab(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onSortChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onLeaderboardPeriodChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onVote: (String, Int) -> Unit,
    onDraftTitleChange: (String) -> Unit,
    onDraftContentChange: (String) -> Unit,
    onDraftCategoryChange: (String) -> Unit,
    onDraftFlairChange: (String) -> Unit,
    onDraftSpoilerChange: (Boolean) -> Unit,
    onCreatePost: () -> Unit,
    onOpenPost: (String) -> Unit,
    onDismissPostDetail: () -> Unit,
    onCommentDraftChange: (String) -> Unit,
    onCommentSpoilerChange: (Boolean) -> Unit,
    onSubmitComment: () -> Unit,
    onImageClick: (String) -> Unit,
    onDismissFullscreenImage: () -> Unit,
) {
    val sortOptions = listOf("hot", "new", "top", "old")
    val periodOptions = listOf("all", "weekly", "monthly")

    uiState.communityFullscreenImageUrl?.let { url ->
        CommunityFullscreenMediaDialog(imageUrl = url, onDismiss = onDismissFullscreenImage)
    }

    uiState.communityDetailPostId?.let { pid ->
        CommunityPostDetailDialog(
            uiState = uiState,
            postId = pid,
            onDismiss = onDismissPostDetail,
            onVote = onVote,
            onCommentDraftChange = onCommentDraftChange,
            onCommentSpoilerChange = onCommentSpoilerChange,
            onSubmitComment = onSubmitComment,
            onImageClick = onImageClick,
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Community",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Read posts, vote, create your own post, and track leaderboard activity.",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingCard(
                title = "Online",
                description = "Users online now",
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "${uiState.communityStats?.onlineCount ?: 0}",
                    color = TEXT,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            SettingCard(
                title = "Members",
                description = "Community members",
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "${uiState.communityStats?.members ?: 0}",
                    color = TEXT,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            SettingCard(
                title = "Unread",
                description = "Unread community posts",
                modifier = Modifier.weight(1f),
            ) {
                Text("${uiState.communityUnreadCount}", color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingCard(
            title = "Filters",
            description = "Sort and category",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortOptions.forEach { option ->
                        val selected = uiState.communitySort == option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) Color.White else BG_CARD)
                                .border(1.dp, if (selected) Color.White else BORDER, RoundedCornerShape(999.dp))
                                .clickable { onSortChange(option) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                option.uppercase(),
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh", maxLines = 1, softWrap = false)
                    }
                }

                val categories = listOf("all") + uiState.communityCategories.map { it.slug }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { slug ->
                        val selected = uiState.communityCategory == slug
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) BG_HOVER else BG_CARD)
                                .border(
                                    1.dp,
                                    if (selected) TEXT.copy(alpha = 0.38f) else BORDER,
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable { onCategoryChange(slug) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(slug, color = TEXT, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingCard(
            title = "Create Post",
            description = "Requires logged-in account",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.communityDraftTitle,
                    onValueChange = onDraftTitleChange,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.communityDraftContent,
                    onValueChange = onDraftContentChange,
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 6,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.communityDraftCategory,
                        onValueChange = onDraftCategoryChange,
                        label = { Text("Category slug") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.communityDraftFlair,
                        onValueChange = onDraftFlairChange,
                        label = { Text("Flair") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = uiState.communityDraftSpoiler,
                        onCheckedChange = onDraftSpoilerChange,
                    )
                    Text("Mark as spoiler", color = TEXT, fontSize = 13.sp)
                }
                Button(
                    onClick = onCreatePost,
                    enabled = !uiState.isCreatingCommunityPost,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isCreatingCommunityPost) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.onAccent
                        )
                    } else {
                        Text("Create Community Post")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingCard(
            title = "Leaderboard",
            description = "Top aura contributors",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periodOptions.forEach { option ->
                        val selected = uiState.communityLeaderboardPeriod == option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) Color.White else BG_CARD)
                                .border(1.dp, if (selected) Color.White else BORDER, RoundedCornerShape(999.dp))
                                .clickable { onLeaderboardPeriodChange(option) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                option.uppercase(),
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                if (uiState.communityLeaderboard.isEmpty()) {
                    Text("No leaderboard data.", color = MUTED, fontSize = 13.sp)
                } else {
                    uiState.communityLeaderboard.take(10).forEach { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "#${user.rank} ${user.displayName ?: user.name}",
                                color = TEXT,
                                fontSize = 13.sp
                            )
                            Text("${user.aura} aura", color = MUTED, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingCard(
                title = "Posts",
                description = "Community feed",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.isLoadingCommunity) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppColors.accent, strokeWidth = 2.dp)
                        }
                    } else if (uiState.communityPosts.isEmpty()) {
                        Text("No posts found.", color = MUTED, fontSize = 13.sp)
                    } else {
                        uiState.communityPosts.forEach { post ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BG_HOVER)
                                    .border(1.dp, BORDER, RoundedCornerShape(10.dp))
                                    .clickable { onOpenPost(post.id) }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(post.title, color = TEXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                CommunityPostContent(
                                    rawContent = post.content,
                                    onMediaClick = onImageClick,
                                )
                                Text(
                                    "${post.category} • ${post.comments} comments • ${post.views} views • ${post.time ?: ""}",
                                    color = MUTED,
                                    fontSize = 11.sp,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val voting = uiState.isVotingCommunityPostIds.contains(post.id)
                                    OutlinedButton(
                                        onClick = { onVote(post.id, 1) },
                                        enabled = !voting,
                                    ) { Text("▲ ${post.votes}") }
                                    OutlinedButton(
                                        onClick = { onVote(post.id, -1) },
                                        enabled = !voting,
                                    ) { Text("▼") }
                                    OutlinedButton(
                                        onClick = { onVote(post.id, 0) },
                                        enabled = !voting,
                                    ) { Text("Clear vote") }
                                }
                            }
                        }
                    }
                    if (uiState.communityHasMore) {
                        Button(
                            onClick = onLoadMore,
                            enabled = !uiState.isLoadingCommunityMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (uiState.isLoadingCommunityMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.onAccent
                                )
                            } else {
                                Text("Load More")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityFullscreenMediaDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Fullscreen media",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.text)
            }
        }
    }
}

@Composable
private fun CommunityPostDetailDialog(
    uiState: SettingsUiState,
    postId: String,
    onDismiss: () -> Unit,
    onVote: (String, Int) -> Unit,
    onCommentDraftChange: (String) -> Unit,
    onCommentSpoilerChange: (Boolean) -> Unit,
    onSubmitComment: () -> Unit,
    onImageClick: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val post = uiState.communityDetailPost
        val voting = uiState.isVotingCommunityPostIds.contains(postId)
        val loggedIn = uiState.userProfile != null

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TEXT,
                        )
                    }
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            "Post",
                            color = TEXT,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                HorizontalDivider(color = BORDER)

                if (post == null) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isLoadingCommunityDetail) {
                            CircularProgressIndicator(color = AppColors.accent, strokeWidth = 2.dp)
                        } else {
                            Text("Post could not be loaded.", color = MUTED, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(post.title, color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${post.category}${post.flair?.let { " • $it" } ?: ""} • ${post.comments} comments • ${post.views} views • ${post.time ?: ""}",
                                    color = MUTED,
                                    fontSize = 11.sp,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onVote(postId, 1) }, enabled = !voting) {
                                        Text("▲ ${post.votes}")
                                    }
                                    OutlinedButton(
                                        onClick = { onVote(postId, -1) },
                                        enabled = !voting
                                    ) { Text("▼") }
                                    OutlinedButton(
                                        onClick = { onVote(postId, 0) },
                                        enabled = !voting
                                    ) { Text("Clear vote") }
                                }
                                CommunityPostContent(
                                    rawContent = post.content,
                                    maxBodyLines = 10_000,
                                    previewImageCap = 10_000,
                                    onMediaClick = onImageClick,
                                )
                            }
                        }

                        item {
                            Text(
                                "Comments (${uiState.communityDetailComments.size})",
                                color = TEXT,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        items(uiState.communityDetailComments, key = { it.id }) { c ->
                            CommunityCommentCard(comment = c, onMediaClick = onImageClick)
                        }
                    }
                }

                if (!loggedIn) {
                    Text(
                        "Log in to reply to this post.",
                        color = MUTED,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else if (post != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(BG_HOVER)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.communityDetailCommentDraft,
                            onValueChange = onCommentDraftChange,
                            label = { Text("Write a comment") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp),
                            maxLines = 5,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = uiState.communityDetailCommentSpoiler,
                                onCheckedChange = onCommentSpoilerChange,
                            )
                            Text("Spoiler", color = TEXT, fontSize = 13.sp)
                        }
                        Button(
                            onClick = onSubmitComment,
                            enabled = !uiState.isPostingCommunityComment && uiState.communityDetailCommentDraft.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (uiState.isPostingCommunityComment) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.onAccent,
                                )
                            } else {
                                Text("Post comment")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityCommentCard(
    comment: Comment,
    onMediaClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BG_HOVER)
            .border(1.dp, BORDER, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val name = comment.authorDisplayName?.takeIf { it.isNotBlank() } ?: comment.author.orEmpty()
        Text(name, color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(comment.created_at ?: "", color = MUTED, fontSize = 10.sp)
        CommunityPostContent(
            rawContent = comment.content,
            maxBodyLines = 10_000,
            previewImageCap = 10_000,
            onMediaClick = onMediaClick,
        )
    }
}

private val markdownImageRegex = Regex("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)")
private val bareImageUrlRegex = Regex("""https?://\S+\.(?:png|jpe?g|gif|webp)(?:\?\S*)?""", RegexOption.IGNORE_CASE)

@Composable
private fun CommunityPostContent(
    rawContent: String,
    maxBodyLines: Int = 6,
    previewImageCap: Int = 3,
    onMediaClick: ((String) -> Unit)? = null,
) {
    if (rawContent.isBlank()) return

    val markdownImageMatches = markdownImageRegex.findAll(rawContent).map { it.groupValues[1] }.toList()
    val bareImageMatches = bareImageUrlRegex.findAll(rawContent).map { it.value }.toList()
    val imageUrls = (markdownImageMatches + bareImageMatches).distinct()

    val textWithoutMarkdownImages = markdownImageRegex.replace(rawContent, "")
    val cleanedText = imageUrls.fold(textWithoutMarkdownImages) { acc, url ->
        acc.replace(url, "")
    }.trim()

    val bodyUnlimited = maxBodyLines >= 1000

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cleanedText.isNotBlank()) {
            Text(
                cleanedText,
                color = TEXT.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = if (bodyUnlimited) Int.MAX_VALUE else maxBodyLines,
                overflow = if (bodyUnlimited) TextOverflow.Visible else TextOverflow.Ellipsis,
            )
        }

        val shownImages = if (previewImageCap >= 1000) imageUrls else imageUrls.take(previewImageCap)
        shownImages.forEach { imageUrl ->
            val base = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, BORDER, RoundedCornerShape(10.dp))
            AsyncImage(
                model = imageUrl,
                contentDescription = "Community media",
                contentScale = ContentScale.Crop,
                modifier =
                    if (onMediaClick != null) base.clickable { onMediaClick(imageUrl) } else base,
            )
        }
    }
}

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
    onAppLocaleChange: (AppLocale) -> Unit,
    onPlayerEnhancementsChange: (PlayerEnhancementSettings) -> Unit,
    onResetPlayerEnhancements: () -> Unit,
    onPlayerUtilitiesChange: (PlayerUtilitySettings) -> Unit,
    onResetPlayerUtilities: () -> Unit,
    onSave: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val launchDirectoryPicker = rememberDownloadDirectoryPicker { path ->
        path?.let {
            to.kuudere.anisuge.platform.persistFolderPermission(path)
            onDownloadPathChange(path)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Large Title
        Text(
            strings.preferences,
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        SettingCard(
            title = strings.appLanguage,
            description = strings.appLanguageDescription,
            modifier = Modifier.fillMaxWidth()
        ) {
            AppLanguageSelector(
                selectedLocale = uiState.appLocale,
                onLocaleSelected = onAppLocaleChange
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Two Column Layout
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            maxItemsInEachRow = 2,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Auto Play
            SettingCard(
                title = strings.autoPlay,
                description = strings.autoPlayDescription,
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.settings.autoPlay,
                    onCheckedChange = onAutoPlayChange,
                    label = strings.enableAutoPlay
                )
            }

            // Auto Next
            SettingCard(
                title = strings.autoNext,
                description = strings.autoNextDescription,
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.settings.autoNext,
                    onCheckedChange = onAutoNextChange,
                    label = strings.enableAutoNext
                )
            }

            // Skip Intro
            SettingCard(
                title = strings.skipIntro,
                description = strings.skipIntroDescription,
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.settings.skipIntro,
                    onCheckedChange = onSkipIntroChange,
                    label = strings.skipIntroAutomatically
                )
            }

            // Skip Outro
            SettingCard(
                title = strings.skipOutro,
                description = strings.skipOutroDescription,
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.settings.skipOutro,
                    onCheckedChange = onSkipOutroChange,
                    label = strings.skipOutroAutomatically
                )
            }

            // Default Language
            SettingCard(
                title = strings.defaultAudioLanguage,
                description = strings.defaultAudioLanguageDescription,
                modifier = Modifier.weight(1f)
            ) {
                SettingToggle(
                    checked = uiState.settings.defaultLang,
                    onCheckedChange = onDefaultLangChange,
                    label = strings.defaultToEnglishDub
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sync Section - Full Width
        SettingCard(
            title = strings.watchProgressSync,
            description = strings.watchProgressSyncDescription,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${uiState.settings.syncPercentage}%",
                        color = TEXT,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.settings.syncPercentage.toFloat(),
                    onValueChange = { onSyncPercentageChange(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 49,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.accent,
                        activeTrackColor = AppColors.accent,
                        inactiveTrackColor = BORDER
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SettingCard(
            title = strings.subtitleSize,
            description = strings.subtitleSizeDescription,
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
                        thumbColor = AppColors.accent,
                        activeTrackColor = AppColors.accent,
                        inactiveTrackColor = BORDER
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isAndroidPlatform || isDesktopPlatform) {
            SettingCard(
                title = "Player enhancements",
                description = "Global defaults for Anime4K, color, and mpv rendering. Player changes remain session-only.",
                modifier = Modifier.fillMaxWidth(),
            ) {
                GlobalEnhancementSettings(
                    settings = uiState.playerEnhancements,
                    onChange = onPlayerEnhancementsChange,
                    onReset = onResetPlayerEnhancements,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))

            SettingCard(
                title = "Player utilities",
                description = "Global subtitle styling, sync, and double-tap seek defaults.",
                modifier = Modifier.fillMaxWidth(),
            ) {
                GlobalPlayerUtilitySettings(
                    settings = uiState.playerUtilities,
                    onChange = onPlayerUtilitiesChange,
                    onReset = onResetPlayerUtilities,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Download Path Section - Full Width
        SettingCard(
            title = strings.downloadPath,
            description = strings.downloadPathDescription,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BG)
                    .border(1.dp, BORDER, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fixedAndroidDownloadPath = PlatformName == "Android"
                val isPathValid = remember(uiState.downloadPath) {
                    if (fixedAndroidDownloadPath || uiState.downloadPath.isBlank()) true
                    else to.kuudere.anisuge.platform.isFolderWritable(uiState.downloadPath)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (fixedAndroidDownloadPath) {
                            "Downloads/Anisurge"
                        } else if (isPathValid) {
                            to.kuudere.anisuge.platform.formatDisplayPath(uiState.downloadPath)
                        } else {
                            strings.locationUnavailable
                        },
                        color = if (fixedAndroidDownloadPath || uiState.downloadPath.isBlank() || !isPathValid) MUTED else TEXT,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!fixedAndroidDownloadPath && !isPathValid) {
                        Text(
                            strings.chooseWritableFolder,
                            color = Color.Red.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (!fixedAndroidDownloadPath) {
                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = strings.change,
                        color = AppColors.onAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.accent)
                            .clickable { launchDirectoryPicker() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Save Button
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSave,
            enabled = uiState.hasSettingsChanges && !uiState.isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.hasSettingsChanges) AppColors.accent else BG_CARD,
                contentColor = if (uiState.hasSettingsChanges) AppColors.onAccent else MUTED,
                disabledContainerColor = BG_CARD,
                disabledContentColor = MUTED
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.End)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AppColors.onAccent,
                    strokeWidth = 2.dp
                )
            } else {
                Text(strings.saveChanges, fontWeight = FontWeight.Medium)
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
    label: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (enabled) TEXT else MUTED.copy(alpha = 0.55f),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.onAccent,
                checkedTrackColor = AppColors.accent,
                uncheckedThumbColor = AppColors.textMuted,
                uncheckedTrackColor = BORDER
            )
        )
    }
}

@Composable
private fun GlobalEnhancementSettings(
    settings: PlayerEnhancementSettings,
    onChange: (PlayerEnhancementSettings) -> Unit,
    onReset: () -> Unit,
) {
    var pendingShader by remember { mutableStateOf<ShaderPreset?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    pendingShader?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingShader = null },
            title = { Text("Use ${preset.cost.label} shader?") },
            text = { Text("This mode can increase heat and battery use and may drop frames on some devices.") },
            confirmButton = {
                TextButton(onClick = {
                    onChange(settings.copy(shaderPreset = preset.id))
                    pendingShader = null
                }) { Text("Use shader") }
            },
            dismissButton = {
                TextButton(onClick = { pendingShader = null }) { Text("Cancel") }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsChoice(
            label = "Anime4K shader",
            selected = ShaderPreset.fromId(settings.shaderPreset).label,
            values = ShaderPreset.entries.map { it.id to "${it.label} • ${it.cost.label}" },
        ) { id ->
            val preset = ShaderPreset.fromId(id)
            if (preset.cost == ShaderCost.HEAVY || preset.cost == ShaderCost.VERY_HEAVY) {
                pendingShader = preset
            } else {
                onChange(settings.copy(shaderPreset = preset.id))
            }
        }

        SettingsChoice(
            label = "Color preset",
            selected = ColorPreset.fromId(settings.colorPreset).label,
            values = ColorPreset.entries.filter { it != ColorPreset.CUSTOM }.map { it.id to it.label },
        ) { id ->
            onChange(settings.withColorPreset(ColorPreset.fromId(id)))
        }

        listOf(
            "Brightness" to settings.brightness,
            "Contrast" to settings.contrast,
            "Saturation" to settings.saturation,
            "Gamma" to settings.gamma,
            "Hue" to settings.hue,
        ).forEach { (label, current) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = TEXT, fontSize = 13.sp)
                    Text(current.toString(), color = MUTED, fontSize = 12.sp)
                }
                Slider(
                    value = current.toFloat(),
                    onValueChange = { raw ->
                        val value = raw.toInt()
                        val updated = when (label) {
                            "Brightness" -> settings.copy(brightness = value)
                            "Contrast" -> settings.copy(contrast = value)
                            "Saturation" -> settings.copy(saturation = value)
                            "Gamma" -> settings.copy(gamma = value)
                            else -> settings.copy(hue = value)
                        }
                        onChange(updated.copy(colorPreset = ColorPreset.CUSTOM.id))
                    },
                    valueRange = -100f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.accent,
                        activeTrackColor = AppColors.accent,
                        inactiveTrackColor = BORDER,
                    ),
                )
            }
        }

        SettingToggle(settings.deband, { onChange(settings.copy(deband = it)) }, "Debanding")
        SettingToggle(settings.interpolation, { onChange(settings.copy(interpolation = it)) }, "Frame interpolation")
        SettingToggle(settings.temporalDither, { onChange(settings.copy(temporalDither = it)) }, "Temporal dithering")

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Icon(
                if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text("Advanced rendering")
        }

        if (showAdvanced) {
            SettingsChoice("Upscaling", settings.scale, PlayerEnhancementOptions.scalers.map { it to it }) {
                onChange(settings.copy(scale = it))
            }
            SettingsChoice("Chroma scaling", settings.cscale, PlayerEnhancementOptions.scalers.map { it to it }) {
                onChange(settings.copy(cscale = it))
            }
            SettingsChoice("Downscaling", settings.dscale, PlayerEnhancementOptions.downscalers.map { it to it }) {
                onChange(settings.copy(dscale = it))
            }
            SettingsChoice("Dither depth", settings.ditherDepth, PlayerEnhancementOptions.ditherDepths.map { it to it }) {
                onChange(settings.copy(ditherDepth = it))
            }
            SettingsChoice("Tone mapping", settings.toneMapping, PlayerEnhancementOptions.toneMappings.map { it to it }) {
                onChange(settings.copy(toneMapping = it))
            }
            SettingsChoice("Video sync", settings.videoSync, PlayerEnhancementOptions.videoSyncModes.map { it to it }) {
                onChange(settings.copy(videoSync = it))
            }
            SettingsChoice(
                "Decoder",
                if (settings.decoder == "no") "Software" else "Auto",
                listOf("auto" to "Auto", "no" to "Software"),
            ) {
                onChange(settings.copy(decoder = it))
            }
        }

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset enhancements")
        }
    }
}

@Composable
private fun GlobalPlayerUtilitySettings(
    settings: PlayerUtilitySettings,
    onChange: (PlayerUtilitySettings) -> Unit,
    onReset: () -> Unit,
) {
    var showSubtitleStyle by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsUtilityDelaySlider("Subtitle delay", settings.subtitleDelaySeconds) {
            onChange(settings.copy(subtitleDelaySeconds = it))
        }
        SettingsUtilityDelaySlider("Audio delay", settings.audioDelaySeconds) {
            onChange(settings.copy(audioDelaySeconds = it))
        }
        SettingsChoice(
            label = "Double-tap seek",
            selected = "${settings.doubleTapSeekSeconds} seconds",
            values = PlayerUtilitySettings.seekDurations.map { it.toString() to "$it seconds" },
        ) { onChange(settings.copy(doubleTapSeekSeconds = it.toInt())) }

        TextButton(onClick = { showSubtitleStyle = !showSubtitleStyle }) {
            Icon(
                if (showSubtitleStyle) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text("Subtitle style")
        }

        if (showSubtitleStyle) {
            SettingsChoice(
                "Font",
                settings.subtitleFont,
                PlayerUtilitySettings.fonts.map { it to it },
            ) { onChange(settings.copy(subtitleFont = it)) }
            SettingsChoice(
                "Text color",
                PlayerUtilitySettings.colors.firstOrNull { it.first == settings.subtitleColor }?.second
                    ?: settings.subtitleColor,
                PlayerUtilitySettings.colors,
            ) { onChange(settings.copy(subtitleColor = it)) }
            SettingsChoice(
                "Outline color",
                PlayerUtilitySettings.colors.firstOrNull { it.first == settings.subtitleOutlineColor }?.second
                    ?: settings.subtitleOutlineColor,
                PlayerUtilitySettings.colors,
            ) { onChange(settings.copy(subtitleOutlineColor = it)) }
            SettingsChoice(
                "Background color",
                PlayerUtilitySettings.colors.firstOrNull { it.first == settings.subtitleBackgroundColor }?.second
                    ?: settings.subtitleBackgroundColor,
                PlayerUtilitySettings.colors,
            ) { onChange(settings.copy(subtitleBackgroundColor = it)) }
            SettingsUtilityIntSlider("Outline width", settings.subtitleOutlineWidth, 0..10) {
                onChange(settings.copy(subtitleOutlineWidth = it))
            }
            SettingsUtilityIntSlider("Subtitle opacity", settings.subtitleOpacity, 10..100) {
                onChange(settings.copy(subtitleOpacity = it))
            }
            SettingsUtilityIntSlider("Background opacity", settings.subtitleBackgroundOpacity, 0..100) {
                onChange(settings.copy(subtitleBackgroundOpacity = it))
            }
            SettingsUtilityIntSlider("Bottom margin", settings.subtitleBottomMargin, 0..25) {
                onChange(settings.copy(subtitleBottomMargin = it))
            }
        }

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset player utilities")
        }
    }
}

@Composable
private fun SettingsUtilityDelaySlider(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TEXT, fontSize = 13.sp)
            Text(
                "${if (value > 0) "+" else ""}${((value * 10).toInt() / 10.0)}s",
                color = MUTED,
                fontSize = 12.sp,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange((it * 10).toInt() / 10.0) },
            valueRange = -10f..10f,
            steps = 199,
            colors = SliderDefaults.colors(
                thumbColor = AppColors.accent,
                activeTrackColor = AppColors.accent,
                inactiveTrackColor = BORDER,
            ),
        )
    }
}

@Composable
private fun SettingsUtilityIntSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TEXT, fontSize = 13.sp)
            Text(value.toString(), color = MUTED, fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.accent,
                activeTrackColor = AppColors.accent,
                inactiveTrackColor = BORDER,
            ),
        )
    }
}

@Composable
private fun SettingsChoice(
    label: String,
    selected: String,
    values: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BORDER),
        ) {
            Text(
                label,
                color = TEXT,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                selected,
                color = MUTED,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MUTED)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppLanguageSelector(
    selectedLocale: AppLocale,
    onLocaleSelected: (AppLocale) -> Unit,
) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(BORDER)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "${selectedLocale.nativeName} (${selectedLocale.displayName})",
                    color = TEXT,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MUTED,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(BG_CARD)
            ) {
                AppLocale.entries.forEach { locale ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(locale.nativeName, color = TEXT, fontSize = 14.sp)
                                if (locale.nativeName != locale.displayName) {
                                    Text(locale.displayName, color = MUTED, fontSize = 12.sp)
                                }
                            }
                        },
                        leadingIcon = if (locale == selectedLocale) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            expanded = false
                            onLocaleSelected(locale)
                        }
                    )
                }
            }
        }
        Text(
            strings.systemDefaultEnglishFallback,
            color = MUTED,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Anonymous device metrics (install id, platform, app version) may be sent to help improve the app. No account or personal data is included. An opt-out setting may be added later.",
                    color = MUTED,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
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
                DesktopAboutStatRow("Hostname", "Project.R")
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
    onAppLocaleChange: (AppLocale) -> Unit,
    onPlayerEnhancementsChange: (PlayerEnhancementSettings) -> Unit,
    onResetPlayerEnhancements: () -> Unit,
    onPlayerUtilitiesChange: (PlayerUtilitySettings) -> Unit,
    onResetPlayerUtilities: () -> Unit,
    onSave: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val launchDirectoryPicker = rememberDownloadDirectoryPicker { path ->
        path?.let {
            to.kuudere.anisuge.platform.persistFolderPermission(path)
            onDownloadPathChange(path)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            strings.appLanguage,
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            strings.appLanguageDescription,
            color = MUTED,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        AppLanguageSelector(
            selectedLocale = uiState.appLocale,
            onLocaleSelected = onAppLocaleChange
        )

        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))

        MobileSettingRow(
            title = strings.autoPlay,
            description = strings.autoPlayDescription,
            checked = uiState.settings.autoPlay,
            onCheckedChange = onAutoPlayChange
        )
        MobileSettingRow(
            title = strings.autoNext,
            description = strings.autoNextDescription,
            checked = uiState.settings.autoNext,
            onCheckedChange = onAutoNextChange
        )
        MobileSettingRow(
            title = strings.skipIntro,
            description = strings.skipIntroDescription,
            checked = uiState.settings.skipIntro,
            onCheckedChange = onSkipIntroChange
        )
        MobileSettingRow(
            title = strings.skipOutro,
            description = strings.skipOutroDescription,
            checked = uiState.settings.skipOutro,
            onCheckedChange = onSkipOutroChange
        )
        MobileSettingRow(
            title = strings.defaultToEnglishDub,
            description = strings.defaultAudioLanguageDescription,
            checked = uiState.settings.defaultLang,
            onCheckedChange = onDefaultLangChange
        )

        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))

        if (isAndroidPlatform || isDesktopPlatform) {
            Text("Player enhancements", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Global Anime4K and mpv rendering defaults",
                color = MUTED,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            GlobalEnhancementSettings(
                settings = uiState.playerEnhancements,
                onChange = onPlayerEnhancementsChange,
                onReset = onResetPlayerEnhancements,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("Player utilities", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Global subtitle styling, sync, and seek defaults",
                color = MUTED,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            GlobalPlayerUtilitySettings(
                settings = uiState.playerUtilities,
                onChange = onPlayerUtilitiesChange,
                onReset = onResetPlayerUtilities,
            )
            HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))
        }

        Text(
            strings.watchProgressSync,
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            strings.watchProgressSyncDescription,
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
                value = uiState.settings.syncPercentage.toFloat(),
                onValueChange = { onSyncPercentageChange(it.toInt()) },
                valueRange = 50f..100f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = AppColors.accent,
                    activeTrackColor = AppColors.accent,
                    inactiveTrackColor = BORDER
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "${uiState.settings.syncPercentage}%",
                color = TEXT,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider(thickness = 1.dp, color = BORDER, modifier = Modifier.padding(vertical = 16.dp))

        Text(
            strings.subtitleSize,
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            strings.subtitleSizeDescription,
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
                    thumbColor = AppColors.accent,
                    activeTrackColor = AppColors.accent,
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
                .background(BG)
                .border(1.dp, BORDER, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fixedAndroidDownloadPath = PlatformName == "Android"
            val isPathValid = remember(uiState.downloadPath) {
                if (fixedAndroidDownloadPath || uiState.downloadPath.isBlank()) true
                else to.kuudere.anisuge.platform.isFolderWritable(uiState.downloadPath)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (fixedAndroidDownloadPath) {
                        "Downloads/Anisurge"
                    } else if (isPathValid) {
                        to.kuudere.anisuge.platform.formatDisplayPath(uiState.downloadPath)
                    } else {
                        "Location Restricted"
                    },
                    color = if (fixedAndroidDownloadPath || uiState.downloadPath.isBlank() || !isPathValid) MUTED else TEXT,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!fixedAndroidDownloadPath && !isPathValid) {
                    Text(
                        "Choose a folder with write access.",
                        color = Color.Red.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (!fixedAndroidDownloadPath) {
                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Change",
                    color = AppColors.onAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.accent)
                        .clickable { launchDirectoryPicker() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (uiState.hasSettingsChanges) {
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
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
                checkedThumbColor = AppColors.onAccent,
                checkedTrackColor = AppColors.accent,
                uncheckedThumbColor = AppColors.textMuted,
                uncheckedTrackColor = BORDER
            )
        )
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

        AboutStatItem("Hostname", "Project.R")
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Anonymous device metrics (install id, platform, app version) may be sent to help improve the app. No account or personal data is included. An opt-out setting may be added later.",
            color = MUTED,
            fontSize = 12.sp,
            lineHeight = 18.sp
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
                CircularProgressIndicator(color = AppColors.accent)
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
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                Color(0xFFE50914).copy(alpha = 0.5f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear Font Cache (${formatBytesCompact(storageInfo.fontCache.size)})")
                    }

                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(BORDER)),
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
                CircularProgressIndicator(color = AppColors.accent)
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
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            Color(0xFFE50914).copy(alpha = 0.5f)
                        )
                    ),
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
                                    (storageInfo.downloads.size.toFloat() / storageInfo.totalUsed).coerceAtLeast(
                                        0.01f
                                    )
                                else 0.01f
                            )
                            .background(Color(0xFF3B82F6))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(
                                if (storageInfo.totalUsed > 0)
                                    (storageInfo.fontCache.size.toFloat() / storageInfo.totalUsed).coerceAtLeast(
                                        0.01f
                                    )
                                else 0.01f
                            )
                            .background(Color(0xFF8B5CF6))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(
                                if (storageInfo.totalUsed > 0)
                                    (storageInfo.settings.size.toFloat() / storageInfo.totalUsed).coerceAtLeast(
                                        0.01f
                                    )
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
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        TEXT.copy(
                            alpha = 0.3f
                        )
                    )
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Reset", fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "Drag and drop the servers to change the order in which they are used to find streams.",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        Text(
            "The list comes from the site catalog. Providers with both Sub and Dub appear as two separate entries.",
            color = MUTED,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (uiState.isLoadingServers || uiState.availableServers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            val serverList = remember(uiState.availableServers, uiState.serverPriority) {
                ServerRepository.sortServersForSettingsDisplay(
                    uiState.availableServers,
                    uiState.serverPriority,
                )
            }

            var localServerList by remember(serverList) {
                mutableStateOf(serverList)
            }

            LaunchedEffect(uiState.serverPriority, uiState.availableServers) {
                localServerList = serverList
            }

            val autoSaveReorder = { newList: List<to.kuudere.anisuge.data.models.ServerInfo> ->
                localServerList = newList
                onReorder(newList.map { it.id })
                onSave()
            }

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
            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
        )
        Text(
            "List from site catalog. Providers with both Sub and Dub appear as two separate entries.",
            color = MUTED,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (uiState.isLoadingServers || uiState.availableServers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            val serverList = remember(uiState.availableServers, uiState.serverPriority) {
                ServerRepository.sortServersForSettingsDisplay(
                    uiState.availableServers,
                    uiState.serverPriority,
                )
            }

            var localServerList by remember(serverList) {
                mutableStateOf(serverList)
            }

            LaunchedEffect(uiState.serverPriority, uiState.availableServers) {
                localServerList = serverList
            }

            val autoSaveReorder = { newList: List<to.kuudere.anisuge.data.models.ServerInfo> ->
                localServerList = newList
                onReorder(newList.map { it.id })
                onSave()
            }

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
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    Color.White.copy(
                        alpha = 0.3f
                    )
                )
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Reset to Defaults", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ChangePfpButton(
    isUploading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth(0.65f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BORDER),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT),
        ) {
            Text(
                if (isUploading) "Uploading…" else "Change PFP",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "JPEG, PNG, GIF, WebP · max 2.5 MB",
            color = MUTED,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ── Profile summary / account ────────────────────────────────────────────────────
@Composable
private fun ProfileSummaryCard(
    user: to.kuudere.anisuge.data.models.UserProfile,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BG_CARD)
            .then(clickableModifier)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            to.kuudere.anisuge.ui.ProfileAvatar(
                url = user.effectiveAvatar,
                avatarSize = 64.dp,
                frameUrl = user.equippedFrameUrl,
                frameCacheKey = user.equippedFrameItemId,
                showBundledTestFrame = false,
                contentDescription = user.displayName,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName ?: user.username ?: "Anonymous",
                        color = TEXT,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user.isPremium) {
                        Spacer(modifier = Modifier.width(6.dp))
                        ProBadge()
                    }
                    if (user.isEmailVerified == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        VerifiedBadge(size = 13.dp)
                    }
                }
                user.username?.let {
                    Text("@$it", color = MUTED, fontSize = 13.sp)
                }
                if (user.isPremium) {
                    val expiry = user.premiumExpiresAt?.substringBefore("T")?.takeIf { it.isNotBlank() }
                    Text(
                        text = if (expiry != null) "Premium ends $expiry" else "Premium active",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (!user.bio.isNullOrBlank()) {
                    Text(
                        user.bio,
                        color = MUTED,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${user.coins} Berries",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    user.website?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it.removePrefix("https://").removePrefix("http://"),
                            color = MUTED,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                user.joinDate?.let {
                    Text(
                        "Joined ${it.split("T").first()}",
                        color = MUTED,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MUTED,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PremiumProfileCard(
    uiState: SettingsUiState,
    onBuyPremium: () -> Unit,
) {
    val user = uiState.userProfile ?: return
    val expiry = user.premiumExpiresAt?.substringBefore("T")?.takeIf { it.isNotBlank() }
    val isPremium = user.isPremium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFD54F).copy(alpha = 0.08f))
            .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .then(
                if (!isPremium) {
                    Modifier.clickable(enabled = !uiState.isStartingPremiumCheckout) { onBuyPremium() }
                } else {
                    Modifier
                }
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.isStartingPremiumCheckout) {
                CircularProgressIndicator(
                    color = Color(0xFFFFD54F),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isPremium) "Premium active" else "Buy Premium",
                    color = TEXT,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        isPremium && expiry != null -> "Expires $expiry"
                        isPremium -> "Premium downloads, chat, shop, and profile perks enabled"
                        else -> "Unlock downloads, chat perks, shop discount, and animated profile media"
                    },
                    color = MUTED,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            if (!isPremium) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PremiumBenefitsList()
    }
}

@Composable
private fun PremiumBenefitsList() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PREMIUM_BENEFITS.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { benefit ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            benefit,
                            color = TEXT.copy(alpha = 0.86f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProfilePfpAndFramesSection(
    uiState: SettingsUiState,
    onPickCustomPfp: () -> Unit,
    onEquipFrame: (to.kuudere.anisuge.data.models.BffShopItem?) -> Unit,
    showPfpPicker: Boolean = true,
) {
    val user = uiState.userProfile ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showPfpPicker) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    to.kuudere.anisuge.ui.ProfileAvatar(
                        url = user.effectiveAvatar,
                        avatarSize = 96.dp,
                        frameUrl = user.equippedFrameUrl,
                        frameCacheKey = user.equippedFrameItemId,
                        showBundledTestFrame = false,
                        contentDescription = user.displayName,
                    )
                    if (uiState.isUploadingPfp) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Change photo",
                    color = Color(0xFFE50914),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        enabled = !uiState.isUploadingPfp,
                        onClick = onPickCustomPfp,
                    ),
                )
                Text(
                    "JPEG, PNG, GIF, WebP max 2.5 MB · Premium MP4 is cropped square and trimmed to 6s",
                    color = MUTED,
                    fontSize = 11.sp,
                )
            }
        }

        ProfileFramePickerSection(
            user = user,
            ownedFrames = uiState.shopOwned,
            selectedItemId = user.equippedFrameItemId,
            isSaving = uiState.isSavingEquippedFrame,
            isLoadingOwned = uiState.isLoadingOwnedFrames,
            onSelectFrame = onEquipFrame,
        )
    }
}

@Composable
private fun ProfileAccountSection(
    uiState: SettingsUiState,
    onPickCustomPfp: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onSaveUsername: () -> Unit,
    onBioChange: (String) -> Unit,
    onWebsiteChange: (String) -> Unit,
    onSaveProfileDetails: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
    onEquipFrame: (to.kuudere.anisuge.data.models.BffShopItem?) -> Unit,
    onChatProfilePrivacyChange: (Boolean) -> Unit,
) {
    val user = uiState.userProfile ?: return
    val usernameChanged = uiState.usernameDraft.trim() != (user.username.orEmpty())
    val profileDetailsChanged =
        uiState.bioDraft.trim() != user.bio.orEmpty() ||
                uiState.websiteDraft.trim() != user.website.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BG_CARD)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            "Account",
            color = TEXT,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Email, photo, and username are stored on Anisurge only — not on reanime.to.",
            color = MUTED,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )

        ProfileDetailItem("Email", user.email ?: "Not provided")
        ProfileDetailItem("Berries", user.coins.toString())

        ProfilePfpAndFramesSection(
            uiState = uiState,
            onPickCustomPfp = onPickCustomPfp,
            onEquipFrame = onEquipFrame,
        )

        SettingToggle(
            checked = user.chatProfilePrivate,
            onCheckedChange = onChatProfilePrivacyChange,
            label = "Hide watchlist on chat profile",
            enabled = user.isPremium && !uiState.isSavingChatProfilePrivacy,
        )
        if (!user.isPremium) {
            Text(
                "Premium users can hide watch history and watchlist from chat profiles.",
                color = MUTED,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Column {
            Text("Username", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.usernameDraft,
                onValueChange = onUsernameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("username", color = MUTED) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TEXT,
                    unfocusedTextColor = TEXT,
                    focusedBorderColor = BORDER,
                    unfocusedBorderColor = BORDER,
                    cursorColor = TEXT,
                ),
            )
            Text(
                "3–32 characters · letters, numbers, underscore",
                color = MUTED,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (usernameChanged) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSaveUsername,
                    enabled = !uiState.isSavingUsername && uiState.usernameDraft.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isSavingUsername) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Save username", color = Color.Black)
                    }
                }
            }
        }

        Column {
            Text("Description", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.bioDraft,
                onValueChange = onBioChange,
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tell people what you watch, like, or build", color = MUTED) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TEXT,
                    unfocusedTextColor = TEXT,
                    focusedBorderColor = BORDER,
                    unfocusedBorderColor = BORDER,
                    cursorColor = TEXT,
                ),
            )
            Text(
                "${uiState.bioDraft.length}/280 · optional",
                color = MUTED,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("Website", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.websiteDraft,
                onValueChange = onWebsiteChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://your.site", color = MUTED) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TEXT,
                    unfocusedTextColor = TEXT,
                    focusedBorderColor = BORDER,
                    unfocusedBorderColor = BORDER,
                    cursorColor = TEXT,
                ),
            )
            Text(
                "Optional · shown when people tap your chat profile",
                color = MUTED,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (profileDetailsChanged) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSaveProfileDetails,
                    enabled = !uiState.isSavingProfileDetails,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isSavingProfileDetails) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Save profile details", color = Color.Black)
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = BORDER)

        ChangePasswordSection(
            uiState = uiState,
            onCurrentPasswordChange = onCurrentPasswordChange,
            onNewPasswordChange = onNewPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onChangePassword = onChangePassword,
        )
    }
}

@Composable
private fun ChangePasswordSection(
    uiState: SettingsUiState,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
) {
    val canSubmit = uiState.currentPassword.isNotBlank() &&
            uiState.newPassword.length >= 8 &&
            uiState.confirmPassword.isNotBlank() &&
            !uiState.isChangingPassword

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Change password", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        SettingsPasswordField(
            value = uiState.currentPassword,
            onValueChange = onCurrentPasswordChange,
            placeholder = "Current password",
        )
        SettingsPasswordField(
            value = uiState.newPassword,
            onValueChange = onNewPasswordChange,
            placeholder = "New password",
        )
        SettingsPasswordField(
            value = uiState.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = "Retype new password",
        )
        Button(
            onClick = onChangePassword,
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isChangingPassword) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Update password", color = Color.Black)
            }
        }
    }
}

@Composable
private fun SettingsPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = MUTED) },
        visualTransformation = PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TEXT,
            unfocusedTextColor = TEXT,
            focusedBorderColor = BORDER,
            unfocusedBorderColor = BORDER,
            cursorColor = Color.White,
        ),
    )
}

@Composable
private fun MobileProfileAccountDetail(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val pickPfp = to.kuudere.anisuge.platform.rememberProfileImagePicker(viewModel::onCustomPfpPicked)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TEXT)
            }
            Text("Account", color = TEXT, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(thickness = 1.dp, color = BORDER)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 156.dp),
        ) {
            if (uiState.userProfile != null) {
                ProfileAccountSection(
                    uiState = uiState,
                    onPickCustomPfp = pickPfp,
                    onUsernameChange = viewModel::setUsernameDraft,
                    onSaveUsername = viewModel::saveUsername,
                    onBioChange = viewModel::setBioDraft,
                    onWebsiteChange = viewModel::setWebsiteDraft,
                    onSaveProfileDetails = viewModel::saveProfileDetails,
                    onCurrentPasswordChange = viewModel::setCurrentPassword,
                    onNewPasswordChange = viewModel::setNewPassword,
                    onConfirmPasswordChange = viewModel::setConfirmPassword,
                    onChangePassword = viewModel::changePassword,
                    onEquipFrame = viewModel::equipShopFrame,
                    onChatProfilePrivacyChange = viewModel::setChatProfilePrivate,
                )
            }
        }
    }
}

// ── Profile Tab ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(
    uiState: SettingsUiState,
    onRetry: () -> Unit = {},
    onPickCustomPfp: (to.kuudere.anisuge.platform.ChatImagePick?) -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onCloseAccount: () -> Unit = {},
    onUsernameChange: (String) -> Unit = {},
    onSaveUsername: () -> Unit = {},
    onBioChange: (String) -> Unit = {},
    onWebsiteChange: (String) -> Unit = {},
    onSaveProfileDetails: () -> Unit = {},
    onCurrentPasswordChange: (String) -> Unit = {},
    onNewPasswordChange: (String) -> Unit = {},
    onConfirmPasswordChange: (String) -> Unit = {},
    onChangePassword: () -> Unit = {},
    onEquipFrame: (to.kuudere.anisuge.data.models.BffShopItem?) -> Unit = {},
    onChatProfilePrivacyChange: (Boolean) -> Unit = {},
    onBuyPremium: () -> Unit = {},
) {
    val pickPfp = to.kuudere.anisuge.platform.rememberProfileImagePicker(onPickCustomPfp)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.showProfileAccount) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                IconButton(onClick = onCloseAccount) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TEXT)
                }
                Text("Account", color = TEXT, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "Profile",
                color = TEXT,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                "Your account information and profile details",
                color = MUTED,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }

        if (uiState.isOffline && uiState.userProfile == null) {
            OfflineState(
                onRetry = onRetry,
                isLoading = uiState.isLoadingProfile,
                modifier = Modifier.fillMaxWidth().height(400.dp),
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
        } else if (uiState.showProfileAccount) {
            ProfileAccountSection(
                uiState = uiState,
                onPickCustomPfp = pickPfp,
                onUsernameChange = onUsernameChange,
                onSaveUsername = onSaveUsername,
                onBioChange = onBioChange,
                onWebsiteChange = onWebsiteChange,
                onSaveProfileDetails = onSaveProfileDetails,
                onCurrentPasswordChange = onCurrentPasswordChange,
                onNewPasswordChange = onNewPasswordChange,
                onConfirmPasswordChange = onConfirmPasswordChange,
                onChangePassword = onChangePassword,
                onEquipFrame = onEquipFrame,
                onChatProfilePrivacyChange = onChatProfilePrivacyChange,
            )
        } else {
            val user = uiState.userProfile
            ProfileSummaryCard(
                user = user,
                onClick = onOpenAccount,
                showChevron = true,
            )

            Spacer(Modifier.height(12.dp))
            PremiumProfileCard(
                uiState = uiState,
                onBuyPremium = onBuyPremium,
            )

            Spacer(Modifier.height(12.dp))
            ChangePfpButton(
                isUploading = uiState.isUploadingPfp,
                onClick = pickPfp,
            )

            Spacer(Modifier.height(20.dp))
            ProfilePfpAndFramesSection(
                uiState = uiState,
                onPickCustomPfp = pickPfp,
                onEquipFrame = onEquipFrame,
                showPfpPicker = false,
            )

            if (!user.bio.isNullOrBlank() || !user.website.isNullOrBlank()) {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BG_CARD)
                        .padding(24.dp),
                ) {
                    Column {
                        Text("About", color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        if (!user.bio.isNullOrBlank()) {
                            Text(user.bio, color = MUTED, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        user.website?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                it.removePrefix("https://").removePrefix("http://"),
                                color = Color(0xFFBF80FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackingSection(
    uiState: SettingsUiState,
    onConnectMal: () -> Unit,
    onDisconnectMal: () -> Unit,
    onConnectAnilist: () -> Unit,
    onDisconnectAnilist: () -> Unit,
) {
    // MAL Row
    TrackingServiceRow(
        icon = "MAL",
        connected = uiState.malConnected,
        username = uiState.malUsername,
        isLoading = uiState.isConnectingMal,
        onConnect = onConnectMal,
        onDisconnect = onDisconnectMal,
    )
    Spacer(modifier = Modifier.height(12.dp))
    // AniList Row
    TrackingServiceRow(
        icon = "AniList",
        connected = uiState.anilistConnected,
        username = uiState.anilistUsername,
        isLoading = uiState.isConnectingAnilist,
        onConnect = onConnectAnilist,
        onDisconnect = onDisconnectAnilist,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackingServiceRow(
    icon: String,
    connected: Boolean,
    username: String?,
    isLoading: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BG_CARD)
            .border(1.dp, BORDER, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { if (!connected && !isLoading) onConnect() },
                onLongClick = { if (connected) onDisconnect() }
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (connected) Color(0xFF1DB954) else BG_HOVER),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon.take(2),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = icon,
                        color = TEXT,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            isLoading -> "Connecting..."
                            connected -> if (username != null) "Connected as @$username" else "Connected"
                            else -> "Tap to connect"
                        },
                        color = MUTED,
                        fontSize = 12.sp
                    )
                    if (connected) {
                        Text(
                            text = "Long press to disconnect",
                            color = Color(0xFFE50914).copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else if (!connected) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Connect",
                    tint = MUTED,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Connected",
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(24.dp)
                )
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
    onRetry: () -> Unit = {},
    onPickCustomPfp: () -> Unit = {},
    onEquipFrame: (to.kuudere.anisuge.data.models.BffShopItem?) -> Unit = {},
    onEditProfile: () -> Unit = {},
    onBuyPremium: () -> Unit = {},
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    to.kuudere.anisuge.ui.ProfileAvatar(
                        url = user.effectiveAvatar,
                        avatarSize = 120.dp,
                        frameUrl = user.equippedFrameUrl,
                        frameCacheKey = user.equippedFrameItemId,
                        showBundledTestFrame = false,
                        contentDescription = user.displayName,
                    )
                    if (uiState.isUploadingPfp) {
                        Box(
                            Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    user.displayName ?: user.username ?: "Anonymous",
                    color = TEXT,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                user.username?.let {
                    Text("@$it", color = MUTED, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                ChangePfpButton(
                    isUploading = uiState.isUploadingPfp,
                    onClick = onPickCustomPfp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onEditProfile) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = TEXT,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Profile", color = TEXT, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            PremiumProfileCard(
                uiState = uiState,
                onBuyPremium = onBuyPremium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BG_CARD)
                    .padding(16.dp),
            ) {
                ProfilePfpAndFramesSection(
                    uiState = uiState,
                    onPickCustomPfp = onPickCustomPfp,
                    onEquipFrame = onEquipFrame,
                    showPfpPicker = false,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bio
            if (!user.bio.isNullOrBlank() || !user.website.isNullOrBlank()) {
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
                        if (!user.bio.isNullOrBlank()) {
                            Text(user.bio, color = MUTED, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        user.website?.takeIf { it.isNotBlank() }?.let {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                it.removePrefix("https://").removePrefix("http://"),
                                color = Color(0xFFBF80FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
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
                    MobileProfileInfoItem("Berries", user.coins.toString())
                    HorizontalDivider(color = BORDER, modifier = Modifier.padding(horizontal = 16.dp))
                    MobileProfileInfoItem(
                        "Joined",
                        user.joinDate?.let { it.split("T").first() } ?: user.ago ?: "Unknown")
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

@Composable
private fun ProBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFD54F))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "PRO",
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ConnectTab(
    uiState: SettingsUiState,
    onConnectReanime: (String, String) -> Unit,
    onDisconnectReanime: () -> Unit,
    onSyncLibrary: () -> Unit,
    onImportReanime: () -> Unit,
    onExportReanime: () -> Unit,
    onConnectLunar: () -> Unit,
    onDisconnectLunar: () -> Unit,
    onImportLunar: () -> Unit,
    onExportLunar: () -> Unit,
) {
    var showConnectReanimeDialog by remember { mutableStateOf(false) }
    var reanimeEmail by remember { mutableStateOf("") }
    var reanimePassword by remember { mutableStateOf("") }

    LaunchedEffect(uiState.userProfile?.reanimeConnected) {
        if (uiState.userProfile?.reanimeConnected == true) {
            showConnectReanimeDialog = false
            reanimeEmail = ""
            reanimePassword = ""
        }
    }

    if (showConnectReanimeDialog) {
        Dialog(
            onDismissRequest = { if (!uiState.isConnectingReanime) showConnectReanimeDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BG_CARD)
                    .border(1.dp, BORDER, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Connect ReAnime",
                        color = TEXT,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        "Enter your ReAnime email/username and password to sync your library.",
                        color = MUTED,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = reanimeEmail,
                        onValueChange = { reanimeEmail = it },
                        placeholder = { Text("Email or Username", color = MUTED) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TEXT,
                            unfocusedTextColor = TEXT,
                            focusedBorderColor = BORDER,
                            unfocusedBorderColor = BORDER,
                            cursorColor = Color.White,
                        )
                    )

                    OutlinedTextField(
                        value = reanimePassword,
                        onValueChange = { reanimePassword = it },
                        placeholder = { Text("Password", color = MUTED) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TEXT,
                            unfocusedTextColor = TEXT,
                            focusedBorderColor = BORDER,
                            unfocusedBorderColor = BORDER,
                            cursorColor = Color.White,
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { showConnectReanimeDialog = false },
                            enabled = !uiState.isConnectingReanime,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = MUTED)
                        }

                        Button(
                            onClick = { onConnectReanime(reanimeEmail, reanimePassword) },
                            enabled = !uiState.isConnectingReanime && reanimeEmail.isNotBlank() && reanimePassword.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C4AB6)),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (uiState.isConnectingReanime) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Connect", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Connect",
            color = TEXT,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Connect your streaming/catalog accounts to enable cross-device progress sync and list tracking.",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // ReAnime Section
        Text(
            "ReAnime",
            color = TEXT,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (uiState.userProfile?.reanimeConnected == true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BG_CARD)
                    .border(1.dp, BORDER, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ServiceLogo(
                                model = "https://reanime.to/logo.png",
                                fallbackText = "RA",
                                backgroundColor = Color(0xFF6C4AB6),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "ReAnime Account",
                                    color = TEXT,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Connected as @${uiState.userProfile.reanimeUsername ?: uiState.userProfile.username}",
                                    color = MUTED,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = onDisconnectReanime,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isDisconnectingReanime
                        ) {
                            if (uiState.isDisconnectingReanime) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Disconnect", color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onImportReanime,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C4AB6)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isLoading && !uiState.isImportingReanime && !uiState.isExportingReanime
                        ) {
                            if (uiState.isImportingReanime) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Import",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = onExportReanime,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C4AB6)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isLoading && !uiState.isImportingReanime && !uiState.isExportingReanime
                        ) {
                            if (uiState.isExportingReanime) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Export",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (uiState.isImportingLunar || uiState.isExportingLunar) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.lunarSyncTotal > 0) {
                            val progress =
                                (uiState.lunarSyncCurrent.toFloat() / uiState.lunarSyncTotal.toFloat()).coerceIn(
                                    0f,
                                    1f
                                )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF2196F3),
                                trackColor = Color.White.copy(alpha = 0.08f),
                            )
                            Text(
                                text = "${uiState.lunarSyncCurrent} / ${uiState.lunarSyncTotal}",
                                color = MUTED,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF2196F3),
                                trackColor = Color.White.copy(alpha = 0.08f),
                            )
                        }
                        uiState.lunarSyncDetail?.trim()?.takeIf { it.isNotEmpty() }?.let { detail ->
                            Text(
                                detail,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BG_CARD)
                    .border(1.dp, BORDER, RoundedCornerShape(14.dp))
                    .clickable { showConnectReanimeDialog = true }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(
                            model = "https://reanime.to/logo.png",
                            fallbackText = "RA",
                            backgroundColor = BG_HOVER,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "ReAnime Account",
                                color = TEXT,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Not connected", color = MUTED, fontSize = 12.sp)
                        }
                    }
                    Text("Connect", color = Color(0xFFBF80FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // LunarAnime Section
        Text(
            "LunarAnime",
            color = TEXT,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (uiState.lunarConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BG_CARD)
                    .border(1.dp, BORDER, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ServiceLogo(
                                model = null,
                                fallbackText = "LA",
                                backgroundColor = Color(0xFF2196F3),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "LunarAnime Account",
                                    color = TEXT,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Connected as @${uiState.lunarUsername ?: "User"}",
                                    color = MUTED,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = onDisconnectLunar,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isConnectingLunar
                        ) {
                            if (uiState.isConnectingLunar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Disconnect", color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onImportLunar,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isImportingLunar && !uiState.isExportingLunar
                        ) {
                            if (uiState.isImportingLunar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Import",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = onExportLunar,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isImportingLunar && !uiState.isExportingLunar
                        ) {
                            if (uiState.isExportingLunar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Export",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BG_CARD)
                    .border(1.dp, BORDER, RoundedCornerShape(14.dp))
                    .clickable { if (!uiState.isConnectingLunar) onConnectLunar() }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(
                            model = null,
                            fallbackText = "LA",
                            backgroundColor = BG_HOVER,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "LunarAnime Account",
                                color = TEXT,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Not connected", color = MUTED, fontSize = 12.sp)
                        }
                    }
                    if (uiState.isConnectingLunar) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Connect",
                            color = Color(0xFF2196F3),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceLogo(
    model: String?,
    fallbackText: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (!model.isNullOrBlank()) {
            var isError by remember { mutableStateOf(false) }
            if (!isError) {
                AsyncImage(
                    model = model,
                    contentDescription = fallbackText,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { isError = true }
                )
            } else {
                Text(fallbackText, color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(fallbackText, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
