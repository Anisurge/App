package to.kuudere.anisuge.screens.watch

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.*
import to.kuudere.anisuge.theme.AppUiMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import to.kuudere.anisuge.theme.AppColors
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.player.ColorPreset
import to.kuudere.anisuge.player.PlayerEnhancementOptions
import to.kuudere.anisuge.player.PlayerEnhancementSettings
import to.kuudere.anisuge.player.ShaderCost
import to.kuudere.anisuge.player.ShaderPreset
import to.kuudere.anisuge.player.PlayerUtilitySettings
import to.kuudere.anisuge.platform.isAndroidPlatform
import to.kuudere.anisuge.platform.isDesktopPlatform

enum class SettingsMenuPage {
    MAIN, SERVER, QUALITY, SUBTITLES, SPEED, SCALE, WATCHLIST, AUTOPLAY,
    ENHANCEMENTS, SHADERS, COLOR_PRESETS, VISUAL, ADVANCED,
    UTILITIES, AV_SYNC, SUBTITLE_STYLE, SLEEP_TIMER, SEEK_DURATION
}

private val settingsOverlayAnim = tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)
private val settingsOverlayExitAnim = tween<Float>(durationMillis = 130, easing = FastOutSlowInEasing)

@Composable
fun AnimatedWatchSettingsOverlay(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.9f, animationSpec = settingsOverlayAnim) +
            fadeIn(animationSpec = tween(120)),
        exit = scaleOut(targetScale = 0.9f, animationSpec = settingsOverlayExitAnim) +
            fadeOut(animationSpec = tween(100)),
    ) {
        content()
    }
}

@Composable
fun SettingsOverlay(
    uiState: WatchUiState,
    servers: List<to.kuudere.anisuge.data.models.ServerInfo>,
    onDismiss: () -> Unit,
    onQualitySelected: (String) -> Unit,
    onSubtitleSelected: (String?) -> Unit,
    onServerSelected: (String) -> Unit,
    onSpeedSelected: (Double) -> Unit,
    onCycleAudio: () -> Unit,
    audioTracks: List<Pair<Int, String>> = emptyList(),
    selectedAudioTrack: Int? = null,
    onAudioTrackSelected: (Int) -> Unit = {},
    subtitleTracks: List<Pair<Int, String>> = emptyList(),
    selectedSubtitleTrack: Int? = null,
    onSubtitleTrackSelected: (Int?) -> Unit = {},
    onSubtitleSizeSelected: (Int) -> Unit = {},
    onWatchlistStatusSelected: (String) -> Unit = {},
    onAutoPlayToggle: (Boolean) -> Unit = {},
    onAutoNextToggle: (Boolean) -> Unit = {},
    onAutoSkipIntroToggle: (Boolean) -> Unit = {},
    onAutoSkipOutroToggle: (Boolean) -> Unit = {},
    onVideoScaleModeSelected: (String) -> Unit = {},
    onPlayerEnhancementsChanged: (PlayerEnhancementSettings) -> Unit = {},
    onPlayerUtilitiesChanged: (PlayerUtilitySettings) -> Unit = {},
    onScreenshot: () -> Unit = {},
    onSleepTimerChanged: (Int?) -> Unit = {},
    onSleepAfterEpisodeChanged: (Boolean) -> Unit = {},
) {
    var currentPage by remember { mutableStateOf(uiState.initialSettingsPage ?: SettingsMenuPage.MAIN) }
    var isSubtitleSizeDragging by remember { mutableStateOf(false) }
    var pendingHeavyShader by remember { mutableStateOf<ShaderPreset?>(null) }
    val enhancementsSupported = isAndroidPlatform || isDesktopPlatform
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isSubtitleSizeDragging && currentPage == SettingsMenuPage.SUBTITLES) 0.12f else 0.7f,
        animationSpec = tween(durationMillis = 150)
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (isSubtitleSizeDragging && currentPage == SettingsMenuPage.SUBTITLES) 0.18f else 1f,
        animationSpec = tween(durationMillis = 150)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        pendingHeavyShader?.let { preset ->
            AlertDialog(
                onDismissRequest = { pendingHeavyShader = null },
                title = { Text("Use ${preset.cost.label} shader?") },
                text = {
                    Text("This Anime4K mode can increase heat and battery use, and may drop frames on some devices.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        onPlayerEnhancementsChanged(uiState.playerEnhancements.copy(shaderPreset = preset.id))
                        pendingHeavyShader = null
                    }) { Text("Use shader") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingHeavyShader = null }) { Text("Cancel") }
                },
            )
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = 1f
                    scaleY = 1f
                }
                .clip(RoundedCornerShape(AppUiMetrics.sheetRadius))
                .background(Color.Black.copy(alpha = panelAlpha))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(AppUiMetrics.sheetRadius))
                .clickable(enabled = false, onClick = {}) // block touch propagation
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 12.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 180))
        ) {
            AnimatedContent(
                modifier = Modifier.fillMaxWidth(),
                targetState = currentPage,
                transitionSpec = {
                    if (targetState != SettingsMenuPage.MAIN && initialState == SettingsMenuPage.MAIN) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                    } else if (targetState == SettingsMenuPage.MAIN && initialState != SettingsMenuPage.MAIN) {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                    } else {
                        fadeIn().togetherWith(fadeOut())
                    }
                }
            ) { page ->
                when (page) {
                    SettingsMenuPage.MAIN -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top Drag Handle indicator
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Spacer(Modifier.size(32.dp))
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.DarkGray),
                                )
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close settings",
                                        tint = Color.White.copy(alpha = 0.86f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // 1. Server
                                if (servers.isNotEmpty()) {
                                    val currentServerLabel =
                                        servers.find { it.id == uiState.currentServer }?.label ?: uiState.currentServer
                                    SettingsMenuItem(
                                        icon = { Icon(getServerIcon(), contentDescription = null, tint = Color.White) },
                                        title = "Server",
                                        subtitle = currentServerLabel,
                                        onClick = { currentPage = SettingsMenuPage.SERVER }
                                    )
                                }

                                // 2. Quality
                                if (uiState.availableQualities.size > 1 || (servers.isNotEmpty() && uiState.availableQualities.isNotEmpty())) {
                                    SettingsMenuItem(
                                        icon = { Icon(getSignalIcon(), contentDescription = null, tint = Color.White) },
                                        title = "Quality",
                                        subtitle = uiState.currentQuality,
                                        onClick = { currentPage = SettingsMenuPage.QUALITY }
                                    )
                                }

                                // 3. Audio Track
                                if (audioTracks.isNotEmpty()) {
                                    val currentLabel =
                                        audioTracks.firstOrNull { it.first == selectedAudioTrack }?.second ?: "Default"
                                    SettingsMenuItem(
                                        icon = {
                                            Icon(
                                                getLanguagesIcon(),
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        },
                                        title = "Audio Track",
                                        subtitle = currentLabel,
                                        onClick = onCycleAudio
                                    )
                                }

                                // 4. Playback settings
                                val isAutoplayOn =
                                    uiState.autoPlay || uiState.autoNext || uiState.autoSkipIntro || uiState.autoSkipOutro
                                SettingsMenuItem(
                                    icon = {
                                        Icon(
                                            Icons.Default.PlayCircleFilled,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    title = "Playback settings",
                                    subtitle = if (isAutoplayOn) "On" else "Off",
                                    onClick = { currentPage = SettingsMenuPage.AUTOPLAY }
                                )

                                // 5. Captions
                                if (uiState.availableSubtitles.isNotEmpty() || subtitleTracks.isNotEmpty()) {
                                    val currentLabel = if (subtitleTracks.isNotEmpty()) {
                                        subtitleTracks.find { it.first == selectedSubtitleTrack }?.second ?: "Off"
                                    } else {
                                        val selectedSub =
                                            uiState.availableSubtitles.firstOrNull { it.url == uiState.currentSubtitleUrl }
                                        selectedSub?.title ?: selectedSub?.resolvedLang ?: "Off"
                                    }
                                    SettingsMenuItem(
                                        icon = {
                                            Icon(
                                                getClosedCaptionIcon(),
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        },
                                        title = "Captions",
                                        subtitle = "$currentLabel • ${uiState.subtitleSize}%",
                                        onClick = { currentPage = SettingsMenuPage.SUBTITLES }
                                    )
                                }

                                // 6. Playback speed
                                SettingsMenuItem(
                                    icon = { Icon(getGaugeIcon(), contentDescription = null, tint = Color.White) },
                                    title = "Playback speed",
                                    subtitle = if (uiState.playbackSpeed == 1.0) "Normal" else "${uiState.playbackSpeed}x",
                                    onClick = { currentPage = SettingsMenuPage.SPEED }
                                )

                                // 7. Screen fit/fill
                                SettingsMenuItem(
                                    icon = {
                                        Icon(
                                            Icons.Default.AspectRatio,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    title = "Screen mode",
                                    subtitle = when (uiState.videoScaleMode) {
                                        "Zoom" -> "Screen fill"
                                        "Stretch" -> "Stretch"
                                        else -> "Screen fit"
                                    },
                                    onClick = { currentPage = SettingsMenuPage.SCALE }
                                )

                                if (enhancementsSupported) {
                                    val activeShader = ShaderPreset.fromId(uiState.playerEnhancements.shaderPreset)
                                    SettingsMenuItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.AutoFixHigh,
                                                contentDescription = null,
                                                tint = Color.White,
                                            )
                                        },
                                        title = "Enhancements",
                                        subtitle = activeShader.label,
                                        onClick = { currentPage = SettingsMenuPage.ENHANCEMENTS },
                                    )
                                    SettingsMenuItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.Build,
                                                contentDescription = null,
                                                tint = Color.White,
                                            )
                                        },
                                        title = "Player utilities",
                                        subtitle = "${uiState.playerUtilities.doubleTapSeekSeconds}s seek",
                                        onClick = { currentPage = SettingsMenuPage.UTILITIES },
                                    )
                                }

                                // 8. Watchlist
                                if (servers.isNotEmpty()) {
                                    uiState.episodeData?.let { data ->
                                        SettingsMenuItem(
                                            icon = {
                                                if (uiState.isUpdatingWatchlist) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        val infiniteTransition = rememberInfiniteTransition()
                                                        val rotateCW by infiniteTransition.animateFloat(
                                                            0f,
                                                            360f,
                                                            infiniteRepeatable(tween(800, easing = LinearEasing))
                                                        )
                                                        val rotateCCW by infiniteTransition.animateFloat(
                                                            360f,
                                                            0f,
                                                            infiniteRepeatable(tween(600, easing = LinearEasing))
                                                        )

                                                        CircularProgressIndicator(
                                                            progress = { 0.75f },
                                                            modifier = Modifier.size(18.dp)
                                                                .graphicsLayer { rotationZ = rotateCW },
                                                            color = Color.White,
                                                            strokeWidth = 1.dp,
                                                            trackColor = Color.White.copy(alpha = 0.1f),
                                                            strokeCap = StrokeCap.Round
                                                        )
                                                        CircularProgressIndicator(
                                                            progress = { 0.6f },
                                                            modifier = Modifier.size(10.dp)
                                                                .graphicsLayer { rotationZ = rotateCCW },
                                                            color = Color.White.copy(alpha = 0.6f),
                                                            strokeWidth = 1.dp,
                                                            trackColor = Color.White.copy(alpha = 0.05f),
                                                            strokeCap = StrokeCap.Round
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        getBookmarkIcon(data.folder != null),
                                                        contentDescription = null,
                                                        tint = Color.White
                                                    )
                                                }
                                            },
                                            title = "Watchlist",
                                            subtitle = data.folder ?: "Not in list",
                                            onClick = {
                                                if (!uiState.isUpdatingWatchlist) currentPage =
                                                    SettingsMenuPage.WATCHLIST
                                            }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    SettingsMenuPage.SERVER -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader(
                                "Server",
                                onBack = { currentPage = SettingsMenuPage.MAIN },
                                onClose = onDismiss
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                items(servers) { server ->
                                    SubMenuItem(
                                        title = server.label,
                                        isSelected = server.id == uiState.currentServer,
                                        onClick = { onServerSelected(server.id); onDismiss() }
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.QUALITY -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader(
                                "Quality",
                                onBack = { currentPage = SettingsMenuPage.MAIN },
                                onClose = onDismiss
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                items(uiState.availableQualities) { (quality, _) ->
                                    SubMenuItem(
                                        title = quality,
                                        isSelected = quality == uiState.currentQuality,
                                        onClick = { onQualitySelected(quality); onDismiss() }
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.SPEED -> {
                        val speeds = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader(
                                "Playback speed",
                                onBack = { currentPage = SettingsMenuPage.MAIN },
                                onClose = onDismiss
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                items(speeds) { speed ->
                                    val titleStr = if (speed == 1.0) "Normal" else "${speed}x"
                                    SubMenuItem(
                                        title = titleStr,
                                        isSelected = speed == uiState.playbackSpeed,
                                        onClick = { onSpeedSelected(speed); onDismiss() }
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.SCALE -> {
                        val modes = listOf("Fit" to "Screen fit", "Zoom" to "Screen fill", "Stretch" to "Stretch")
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader(
                                "Screen mode",
                                onBack = { currentPage = SettingsMenuPage.MAIN },
                                onClose = onDismiss
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 220.dp).fillMaxWidth()) {
                                items(modes) { (mode, label) ->
                                    SubMenuItem(
                                        title = label,
                                        isSelected = mode == uiState.videoScaleMode,
                                        onClick = { onVideoScaleModeSelected(mode); onDismiss() }
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.SUBTITLES -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader(
                                "Captions",
                                onBack = { currentPage = SettingsMenuPage.MAIN },
                                onClose = onDismiss
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 340.dp).fillMaxWidth()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Size",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text("${uiState.subtitleSize}%", color = Color.Gray, fontSize = 14.sp)
                                        }
                                        val subtitleSizeInteractionSource = remember { MutableInteractionSource() }
                                        val isDraggingSubtitleSize by subtitleSizeInteractionSource.collectIsDraggedAsState()
                                        LaunchedEffect(isDraggingSubtitleSize) {
                                            isSubtitleSizeDragging = isDraggingSubtitleSize
                                        }

                                        Slider(
                                            value = uiState.subtitleSize.toFloat(),
                                            onValueChange = { onSubtitleSizeSelected(it.toInt()) },
                                            valueRange = 60f..200f,
                                            steps = 13,
                                            interactionSource = subtitleSizeInteractionSource,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color.White,
                                                activeTrackColor = AppColors.accent,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                item {
                                    SubMenuItem(
                                        title = "Off",
                                        isSelected = if (subtitleTracks.isNotEmpty()) selectedSubtitleTrack == null else uiState.currentSubtitleUrl == null,
                                        onClick = {
                                            if (subtitleTracks.isNotEmpty()) onSubtitleTrackSelected(null)
                                            else onSubtitleSelected(null)
                                            onDismiss()
                                        }
                                    )
                                }
                                if (subtitleTracks.isNotEmpty()) {
                                    items(subtitleTracks) { (id, label) ->
                                        SubMenuItem(
                                            title = label,
                                            isSelected = id == selectedSubtitleTrack,
                                            onClick = { onSubtitleTrackSelected(id); onDismiss() }
                                        )
                                    }
                                } else {
                                    items(uiState.availableSubtitles) { subData ->
                                        val url = subData.url
                                        val label = subData.title ?: subData.resolvedLang ?: "Unknown"
                                        SubMenuItem(
                                            title = label,
                                            isSelected = url == uiState.currentSubtitleUrl,
                                            onClick = { onSubtitleSelected(url); onDismiss() }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsMenuPage.WATCHLIST -> {
                        val folders = listOf("Watching", "On Hold", "Plan To Watch", "Dropped", "Completed", "Remove")
                        val currentFolder = uiState.episodeData?.folder
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader(
                                "Watchlist",
                                onBack = { currentPage = SettingsMenuPage.MAIN },
                                onClose = onDismiss
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                items(folders) { folder ->
                                    SubMenuItem(
                                        title = folder,
                                        isSelected = if (folder == "Remove") uiState.episodeData?.folder == null else folder == currentFolder,
                                        onClick = { onWatchlistStatusSelected(folder); onDismiss() }
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.AUTOPLAY -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader(
                                "Playback settings",
                                onBack = { currentPage = SettingsMenuPage.MAIN },
                                onClose = onDismiss
                            )
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                item {
                                    ToggleMenuItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.PlayCircleFilled,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        },
                                        title = "Auto Play",
                                        isChecked = uiState.autoPlay,
                                        onToggle = { onAutoPlayToggle(it) }
                                    )
                                }
                                item {
                                    ToggleMenuItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.SkipNext,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        },
                                        title = "Auto next",
                                        isChecked = uiState.autoNext,
                                        onToggle = { onAutoNextToggle(it) }
                                    )
                                }
                                item {
                                    ToggleMenuItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.FastForward,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        },
                                        title = "Skip intro",
                                        isChecked = uiState.autoSkipIntro,
                                        onToggle = { onAutoSkipIntroToggle(it) }
                                    )
                                }
                                item {
                                    ToggleMenuItem(
                                        icon = {
                                            Icon(
                                                Icons.Default.FastForward,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        },
                                        title = "Skip outro",
                                        isChecked = uiState.autoSkipOutro,
                                        onToggle = { onAutoSkipOutroToggle(it) }
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.ENHANCEMENTS -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Enhancements", { currentPage = SettingsMenuPage.MAIN }, onDismiss)
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.Visibility, null, tint = Color.White) },
                                    title = "Shaders",
                                    subtitle = ShaderPreset.fromId(uiState.playerEnhancements.shaderPreset).cost.label,
                                    onClick = { currentPage = SettingsMenuPage.SHADERS },
                                )
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.Palette, null, tint = Color.White) },
                                    title = "Color presets",
                                    subtitle = ColorPreset.fromId(uiState.playerEnhancements.colorPreset).label,
                                    onClick = { currentPage = SettingsMenuPage.COLOR_PRESETS },
                                )
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.Tune, null, tint = Color.White) },
                                    title = "Visual",
                                    subtitle = "Color controls",
                                    onClick = { currentPage = SettingsMenuPage.VISUAL },
                                )
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.SettingsSuggest, null, tint = Color.White) },
                                    title = "Advanced",
                                    subtitle = "mpv rendering",
                                    onClick = { currentPage = SettingsMenuPage.ADVANCED },
                                )
                                TextButton(
                                    onClick = { onPlayerEnhancementsChanged(PlayerEnhancementSettings.DEFAULT) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Icon(Icons.Default.RestartAlt, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reset session")
                                }
                            }
                        }
                    }

                    SettingsMenuPage.SHADERS -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Anime4K shaders", { currentPage = SettingsMenuPage.ENHANCEMENTS }, onDismiss)
                            LazyColumn(modifier = Modifier.heightIn(max = 360.dp).fillMaxWidth()) {
                                items(ShaderPreset.entries) { preset ->
                                    SubMenuItem(
                                        title = "${preset.label}  •  ${preset.cost.label}",
                                        isSelected = preset.id == uiState.playerEnhancements.shaderPreset,
                                        onClick = {
                                            if (preset.cost == ShaderCost.HEAVY || preset.cost == ShaderCost.VERY_HEAVY) {
                                                pendingHeavyShader = preset
                                            } else {
                                                onPlayerEnhancementsChanged(
                                                    uiState.playerEnhancements.copy(shaderPreset = preset.id),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.COLOR_PRESETS -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Color presets", { currentPage = SettingsMenuPage.ENHANCEMENTS }, onDismiss)
                            LazyColumn(modifier = Modifier.heightIn(max = 340.dp).fillMaxWidth()) {
                                items(ColorPreset.entries.filter { it != ColorPreset.CUSTOM }) { preset ->
                                    SubMenuItem(
                                        title = preset.label,
                                        isSelected = preset.id == uiState.playerEnhancements.colorPreset,
                                        onClick = {
                                            onPlayerEnhancementsChanged(uiState.playerEnhancements.withColorPreset(preset))
                                        },
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.VISUAL -> {
                        val value = uiState.playerEnhancements
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Visual", { currentPage = SettingsMenuPage.ENHANCEMENTS }, onDismiss)
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 18.dp, vertical = 4.dp),
                            ) {
                                EnhancementSlider("Brightness", value.brightness) {
                                    onPlayerEnhancementsChanged(value.copy(brightness = it, colorPreset = ColorPreset.CUSTOM.id))
                                }
                                EnhancementSlider("Contrast", value.contrast) {
                                    onPlayerEnhancementsChanged(value.copy(contrast = it, colorPreset = ColorPreset.CUSTOM.id))
                                }
                                EnhancementSlider("Saturation", value.saturation) {
                                    onPlayerEnhancementsChanged(value.copy(saturation = it, colorPreset = ColorPreset.CUSTOM.id))
                                }
                                EnhancementSlider("Gamma", value.gamma) {
                                    onPlayerEnhancementsChanged(value.copy(gamma = it, colorPreset = ColorPreset.CUSTOM.id))
                                }
                                EnhancementSlider("Hue", value.hue) {
                                    onPlayerEnhancementsChanged(value.copy(hue = it, colorPreset = ColorPreset.CUSTOM.id))
                                }
                            }
                        }
                    }

                    SettingsMenuPage.ADVANCED -> {
                        val value = uiState.playerEnhancements
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Advanced", { currentPage = SettingsMenuPage.ENHANCEMENTS }, onDismiss)
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                EnhancementToggle("Debanding", value.deband) {
                                    onPlayerEnhancementsChanged(value.copy(deband = it))
                                }
                                EnhancementToggle("Frame interpolation", value.interpolation) {
                                    onPlayerEnhancementsChanged(value.copy(interpolation = it))
                                }
                                EnhancementToggle("Temporal dithering", value.temporalDither) {
                                    onPlayerEnhancementsChanged(value.copy(temporalDither = it))
                                }
                                EnhancementChoice("Upscaling", value.scale, PlayerEnhancementOptions.scalers) {
                                    onPlayerEnhancementsChanged(value.copy(scale = it))
                                }
                                EnhancementChoice("Chroma scaling", value.cscale, PlayerEnhancementOptions.scalers) {
                                    onPlayerEnhancementsChanged(value.copy(cscale = it))
                                }
                                EnhancementChoice("Downscaling", value.dscale, PlayerEnhancementOptions.downscalers) {
                                    onPlayerEnhancementsChanged(value.copy(dscale = it))
                                }
                                EnhancementChoice("Dither depth", value.ditherDepth, PlayerEnhancementOptions.ditherDepths) {
                                    onPlayerEnhancementsChanged(value.copy(ditherDepth = it))
                                }
                                EnhancementChoice("Tone mapping", value.toneMapping, PlayerEnhancementOptions.toneMappings) {
                                    onPlayerEnhancementsChanged(value.copy(toneMapping = it))
                                }
                                EnhancementChoice("Video sync", value.videoSync, PlayerEnhancementOptions.videoSyncModes) {
                                    onPlayerEnhancementsChanged(value.copy(videoSync = it))
                                }
                                EnhancementChoice("Decoder", value.decoder, PlayerEnhancementOptions.decoders) {
                                    onPlayerEnhancementsChanged(value.copy(decoder = it))
                                }
                            }
                        }
                    }

                    SettingsMenuPage.UTILITIES -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Player utilities", { currentPage = SettingsMenuPage.MAIN }, onDismiss)
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.Sync, null, tint = Color.White) },
                                    title = "Audio & subtitle sync",
                                    subtitle = "${signedDelay(uiState.playerUtilities.subtitleDelaySeconds)}s",
                                    onClick = { currentPage = SettingsMenuPage.AV_SYNC },
                                )
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.Subtitles, null, tint = Color.White) },
                                    title = "Subtitle style",
                                    subtitle = uiState.playerUtilities.subtitleFont,
                                    onClick = { currentPage = SettingsMenuPage.SUBTITLE_STYLE },
                                )
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.TouchApp, null, tint = Color.White) },
                                    title = "Double-tap seek",
                                    subtitle = "${uiState.playerUtilities.doubleTapSeekSeconds} seconds",
                                    onClick = { currentPage = SettingsMenuPage.SEEK_DURATION },
                                )
                                EnhancementChoice(
                                    label = "Playback buffer",
                                    selected = PlayerUtilitySettings.bufferLabel(
                                        uiState.playerUtilities.playbackBufferMb,
                                    ),
                                    values = PlayerUtilitySettings.bufferSizesMb.map(
                                        PlayerUtilitySettings::bufferLabel,
                                    ),
                                ) { label ->
                                    val size = PlayerUtilitySettings.bufferSizesMb.first {
                                        PlayerUtilitySettings.bufferLabel(it) == label
                                    }
                                    onPlayerUtilitiesChanged(
                                        uiState.playerUtilities.copy(playbackBufferMb = size),
                                    )
                                }
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.Bedtime, null, tint = Color.White) },
                                    title = "Sleep timer",
                                    subtitle = when {
                                        uiState.sleepAfterEpisode -> "End of episode"
                                        uiState.sleepTimerMinutes != null -> "${uiState.sleepTimerMinutes} minutes"
                                        else -> "Off"
                                    },
                                    onClick = { currentPage = SettingsMenuPage.SLEEP_TIMER },
                                )
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.PhotoCamera, null, tint = Color.White) },
                                    title = "Screenshot",
                                    subtitle = "Video + subtitles",
                                    onClick = {
                                        onScreenshot()
                                        onDismiss()
                                    },
                                )
                                TextButton(
                                    onClick = { onPlayerUtilitiesChanged(PlayerUtilitySettings.DEFAULT) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Icon(Icons.Default.RestartAlt, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reset session utilities")
                                }
                            }
                        }
                    }

                    SettingsMenuPage.AV_SYNC -> {
                        val value = uiState.playerUtilities
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Audio & subtitle sync", { currentPage = SettingsMenuPage.UTILITIES }, onDismiss)
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 18.dp),
                            ) {
                                UtilityDelaySlider("Subtitle delay", value.subtitleDelaySeconds) {
                                    onPlayerUtilitiesChanged(value.copy(subtitleDelaySeconds = it))
                                }
                                UtilityDelaySlider("Audio delay", value.audioDelaySeconds) {
                                    onPlayerUtilitiesChanged(value.copy(audioDelaySeconds = it))
                                }
                                Text(
                                    "Positive values delay the track; negative values play it earlier.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }

                    SettingsMenuPage.SUBTITLE_STYLE -> {
                        val value = uiState.playerUtilities
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Subtitle style", { currentPage = SettingsMenuPage.UTILITIES }, onDismiss)
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                EnhancementChoice("Font", value.subtitleFont, PlayerUtilitySettings.fonts) {
                                    onPlayerUtilitiesChanged(value.copy(subtitleFont = it))
                                }
                                EnhancementChoice(
                                    "Text color",
                                    PlayerUtilitySettings.colors.firstOrNull { it.first == value.subtitleColor }?.second
                                        ?: value.subtitleColor,
                                    PlayerUtilitySettings.colors.map { it.first },
                                ) { onPlayerUtilitiesChanged(value.copy(subtitleColor = it)) }
                                EnhancementChoice(
                                    "Outline color",
                                    PlayerUtilitySettings.colors.firstOrNull { it.first == value.subtitleOutlineColor }?.second
                                        ?: value.subtitleOutlineColor,
                                    PlayerUtilitySettings.colors.map { it.first },
                                ) { onPlayerUtilitiesChanged(value.copy(subtitleOutlineColor = it)) }
                                EnhancementChoice(
                                    "Background color",
                                    PlayerUtilitySettings.colors.firstOrNull { it.first == value.subtitleBackgroundColor }?.second
                                        ?: value.subtitleBackgroundColor,
                                    PlayerUtilitySettings.colors.map { it.first },
                                ) { onPlayerUtilitiesChanged(value.copy(subtitleBackgroundColor = it)) }
                                UtilityIntSlider("Outline width", value.subtitleOutlineWidth, 0..10) {
                                    onPlayerUtilitiesChanged(value.copy(subtitleOutlineWidth = it))
                                }
                                UtilityIntSlider("Subtitle opacity", value.subtitleOpacity, 10..100) {
                                    onPlayerUtilitiesChanged(value.copy(subtitleOpacity = it))
                                }
                                UtilityIntSlider("Background opacity", value.subtitleBackgroundOpacity, 0..100) {
                                    onPlayerUtilitiesChanged(value.copy(subtitleBackgroundOpacity = it))
                                }
                                UtilityIntSlider("Bottom margin", value.subtitleBottomMargin, 0..25) {
                                    onPlayerUtilitiesChanged(value.copy(subtitleBottomMargin = it))
                                }
                            }
                        }
                    }

                    SettingsMenuPage.SLEEP_TIMER -> {
                        val timers = listOf<Int?>(null, 15, 30, 45, 60, 90)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Sleep timer", { currentPage = SettingsMenuPage.UTILITIES }, onDismiss)
                            LazyColumn(modifier = Modifier.heightIn(max = 340.dp).fillMaxWidth()) {
                                items(timers) { minutes ->
                                    SubMenuItem(
                                        title = minutes?.let { "$it minutes" } ?: "Off",
                                        isSelected = !uiState.sleepAfterEpisode && uiState.sleepTimerMinutes == minutes,
                                        onClick = {
                                            onSleepTimerChanged(minutes)
                                            onDismiss()
                                        },
                                    )
                                }
                                item {
                                    SubMenuItem(
                                        title = "End of episode",
                                        isSelected = uiState.sleepAfterEpisode,
                                        onClick = {
                                            onSleepAfterEpisodeChanged(true)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    SettingsMenuPage.SEEK_DURATION -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Double-tap seek", { currentPage = SettingsMenuPage.UTILITIES }, onDismiss)
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp).fillMaxWidth()) {
                                items(PlayerUtilitySettings.seekDurations) { seconds ->
                                    SubMenuItem(
                                        title = "$seconds seconds",
                                        isSelected = seconds == uiState.playerUtilities.doubleTapSeekSeconds,
                                        onClick = {
                                            onPlayerUtilitiesChanged(
                                                uiState.playerUtilities.copy(doubleTapSeekSeconds = seconds),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun signedDelay(value: Double): String =
    if (value > 0) "+${value.formatOneDecimal()}" else value.formatOneDecimal()

private fun Double.formatOneDecimal(): String =
    ((this * 10).toInt() / 10.0).toString()

@Composable
private fun UtilityDelaySlider(label: String, value: Double, onValueChange: (Double) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text("${signedDelay(value)}s", color = Color.Gray, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange((it * 10).toInt() / 10.0) },
            valueRange = -10f..10f,
            steps = 199,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AppColors.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun UtilityIntSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(value.toString(), color = Color.Gray, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AppColors.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun EnhancementSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(value.toString(), color = Color.Gray, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AppColors.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun EnhancementToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ToggleMenuItem(
        icon = { Icon(Icons.Default.Tune, null, tint = Color.White) },
        title = label,
        isChecked = checked,
        onToggle = onCheckedChange,
    )
}

@Composable
private fun EnhancementChoice(
    label: String,
    selected: String,
    values: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsMenuItem(
            icon = { Icon(Icons.Default.Tune, null, tint = Color.White) },
            title = label,
            subtitle = selected,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    leadingIcon = if (value == selected) {
                        { Icon(Icons.Default.Check, null) }
                    } else null,
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isHovered) Color.White.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                subtitle,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SubMenuHeader(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close settings",
                tint = Color.White.copy(alpha = 0.86f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SubMenuItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isHovered) Color.White.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ToggleMenuItem(
    icon: @Composable () -> Unit,
    title: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isHovered) Color.White.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = { onToggle(!isChecked) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Switch(
                checked = isChecked,
                onCheckedChange = { onToggle(it) },
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = Color(0xFF999999),
                    uncheckedTrackColor = Color(0xFF222222),
                    uncheckedBorderColor = Color(0xFF444444),
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.accent.copy(alpha = 0.7f),
                    checkedBorderColor = Color.Transparent
                )
            )
        }
    }
}

private fun getLanguagesIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Languages",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 8f); lineToRelative(6f, 6f)
            moveTo(4f, 14f); lineToRelative(6f, -6f); lineToRelative(2f, -3f)
            moveTo(2f, 5f); horizontalLineToRelative(12f)
            moveTo(7f, 2f); horizontalLineToRelative(1f)
            moveTo(22f, 22f); lineToRelative(-5f, -10f); lineToRelative(-5f, 10f)
            moveTo(14f, 18f); horizontalLineToRelative(6f)
        }
    }.build()
}

private fun getClosedCaptionIcon(): ImageVector {
    return ImageVector.Builder(
        name = "ClosedCaption",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(10f, 9.17f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, 5.66f)
            moveTo(17f, 9.17f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, 5.66f)

            moveTo(4f, 5f)
            horizontalLineToRelative(16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
            verticalLineToRelative(10f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
            horizontalLineToRelative(-16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
            verticalLineToRelative(-10f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
            close()
        }
    }.build()
}

private fun getGaugeIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Gauge",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 14f)
            lineToRelative(4f, -4f)
            moveTo(3.34f, 19f)
            arcToRelative(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.32f, 0f)
        }
    }.build()
}

private fun getSignalIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Signal",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2f, 20f); horizontalLineToRelative(0.01f)
            moveTo(7f, 20f); verticalLineToRelative(-4f)
            moveTo(12f, 20f); verticalLineToRelative(-8f)
            moveTo(17f, 20f); verticalLineTo(8f)
            moveTo(22f, 4f); verticalLineToRelative(16f)
        }
    }.build()
}

private fun getServerIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Server",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 2f)
            horizontalLineToRelative(16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
            verticalLineToRelative(4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
            horizontalLineToRelative(-16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
            verticalLineToRelative(-4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
            close()

            moveTo(4f, 14f)
            horizontalLineToRelative(16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
            verticalLineToRelative(4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
            horizontalLineToRelative(-16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
            verticalLineToRelative(-4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
            close()

            moveTo(6f, 6f); lineToRelative(0.01f, 0f)
            moveTo(6f, 18f); lineToRelative(0.01f, 0f)
        }
    }.build()
}

private fun getBookmarkIcon(isFilled: Boolean): ImageVector {
    return if (isFilled) Icons.Default.Bookmark else Icons.Default.BookmarkBorder
}
