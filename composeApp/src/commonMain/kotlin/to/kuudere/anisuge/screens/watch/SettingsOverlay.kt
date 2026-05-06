package to.kuudere.anisuge.screens.watch

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SettingsMenuPage {
    MAIN, SERVER, AUDIO, QUALITY, SUBTITLES, SPEED, WATCHLIST, AUTOPLAY
}

@Composable
fun SettingsOverlay(
    uiState: WatchUiState,
    servers: List<String>,
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
    onAutoSkipOutroToggle: (Boolean) -> Unit = {}
) {
    var currentPage by remember { mutableStateOf(uiState.initialSettingsPage ?: SettingsMenuPage.MAIN) }
    var isSubtitleSizeDragging by remember { mutableStateOf(false) }
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
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = panelAlpha))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .clickable(enabled = false, onClick = {}) // block touch propagation
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 12.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 300)) // smooth size change
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
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.DarkGray))
                            }

                            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                                // 1. Server
                                if (servers.isNotEmpty()) {
                                    SettingsMenuItem(
                                        icon = { Icon(getServerIcon(), contentDescription = null, tint = Color.White) },
                                        title = "Server",
                                        subtitle = uiState.currentServer,
                                        onClick = { currentPage = SettingsMenuPage.SERVER }
                                    )
                                }
                                
                                // 2. Quality
                                if (uiState.availableQualities.size > 1 || (servers.isNotEmpty() && uiState.availableQualities.isNotEmpty())) {
                                    SettingsMenuItem(
                                        icon = { Icon(getSignalIcon(), contentDescription = null, tint = Color.White) }, // Icon
                                        title = "Quality",
                                        subtitle = uiState.currentQuality,
                                        onClick = { currentPage = SettingsMenuPage.QUALITY }
                                    )
                                }

                                // 3. Playback settings
                                val isAutoplayOn = uiState.autoPlay || uiState.autoNext || uiState.autoSkipIntro || uiState.autoSkipOutro
                                SettingsMenuItem(
                                    icon = { Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Color.White) },
                                    title = "Playback settings",
                                    subtitle = if (isAutoplayOn) "On" else "Off",
                                    onClick = { currentPage = SettingsMenuPage.AUTOPLAY }
                                )

                                // 4. Audio Track
                                if (audioTracks.isNotEmpty()) {
                                    val currentLabel = audioTracks.firstOrNull { it.first == selectedAudioTrack }?.second ?: "Default"
                                    SettingsMenuItem(
                                        icon = { Icon(getLanguagesIcon(), contentDescription = null, tint = Color.White) },
                                        title = "Audio Track",
                                        subtitle = currentLabel,
                                        onClick = { currentPage = SettingsMenuPage.AUDIO }
                                    )
                                } else {
                                    // Fallback cycle
                                    SettingsMenuItem(
                                        icon = { Icon(getLanguagesIcon(), contentDescription = null, tint = Color.White) },
                                        title = "Audio Track",
                                        subtitle = "Cycle",
                                        onClick = { onCycleAudio(); onDismiss() }
                                    )
                                }
                                
                                // 5. Captions
                                if (uiState.availableSubtitles.isNotEmpty() || subtitleTracks.isNotEmpty()) {
                                    val currentLabel = if (subtitleTracks.isNotEmpty()) {
                                        subtitleTracks.find { it.first == selectedSubtitleTrack }?.second ?: "Off"
                                    } else {
                                        val selectedSub = uiState.availableSubtitles.firstOrNull { it.url == uiState.currentSubtitleUrl }
                                        selectedSub?.title ?: selectedSub?.resolvedLang ?: "Off"
                                    }
                                    SettingsMenuItem(
                                        icon = { Icon(getClosedCaptionIcon(), contentDescription = null, tint = Color.White) },
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

                                // 7. Watchlist
                                if (servers.isNotEmpty()) {
                                    uiState.episodeData?.let { data ->
                                        SettingsMenuItem(
                                            icon = { 
                                                if (uiState.isUpdatingWatchlist) {
                                                     // Micro-Dual-Circle for Watchlist Sync
                                                     Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                                                         val infiniteTransition = rememberInfiniteTransition()
                                                         val rotateCW by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)))
                                                         val rotateCCW by infiniteTransition.animateFloat(360f, 0f, infiniteRepeatable(tween(600, easing = LinearEasing)))

                                                         CircularProgressIndicator(
                                                             progress = { 0.75f },
                                                             modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotateCW },
                                                             color = Color.White,
                                                             strokeWidth = 1.dp,
                                                             trackColor = Color.White.copy(alpha = 0.1f),
                                                             strokeCap = StrokeCap.Round
                                                         )
                                                         CircularProgressIndicator(
                                                             progress = { 0.6f },
                                                             modifier = Modifier.size(10.dp).graphicsLayer { rotationZ = rotateCCW },
                                                             color = Color.White.copy(alpha = 0.6f),
                                                             strokeWidth = 1.dp,
                                                             trackColor = Color.White.copy(alpha = 0.05f),
                                                             strokeCap = StrokeCap.Round
                                                         )
                                                     }
                                                 } else {
                                                    Icon(getBookmarkIcon(data.folder != null), contentDescription = null, tint = Color.White) 
                                                }
                                            },
                                            title = "Watchlist",
                                            subtitle = data.folder ?: "Not in list",
                                            onClick = { if (!uiState.isUpdatingWatchlist) currentPage = SettingsMenuPage.WATCHLIST }
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    SettingsMenuPage.SERVER -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Server") { currentPage = SettingsMenuPage.MAIN }
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                items(servers) { serverName ->
                                    SubMenuItem(
                                        title = serverName,
                                        isSelected = serverName == uiState.currentServer,
                                        onClick = { onServerSelected(serverName); onDismiss() }
                                    )
                                }
                            }
                        }
                    }
                    SettingsMenuPage.QUALITY -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Quality") { currentPage = SettingsMenuPage.MAIN }
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
                    SettingsMenuPage.AUDIO -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Audio Track") { currentPage = SettingsMenuPage.MAIN }
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                items(audioTracks) { (id, label) ->
                                    SubMenuItem(
                                        title = label,
                                        isSelected = id == selectedAudioTrack,
                                        onClick = { onAudioTrackSelected(id); onDismiss() }
                                    )
                                }
                            }
                        }
                    }
                    SettingsMenuPage.SPEED -> {
                        val speeds = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Playback speed") { currentPage = SettingsMenuPage.MAIN }
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
                    SettingsMenuPage.SUBTITLES -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SubMenuHeader("Captions") { currentPage = SettingsMenuPage.MAIN }
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
                                            Text("Size", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                                                activeTrackColor = Color.White,
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
                            SubMenuHeader("Watchlist") { currentPage = SettingsMenuPage.MAIN }
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
                            SubMenuHeader("Playback settings") { currentPage = SettingsMenuPage.MAIN }
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                                item {
                                    ToggleMenuItem(
                                        icon = { Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Color.White) },
                                        title = "Auto Play",
                                        isChecked = uiState.autoPlay,
                                        onToggle = { onAutoPlayToggle(it) }
                                    )
                                }
                                item {
                                    ToggleMenuItem(
                                        icon = { Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White) },
                                        title = "Auto next",
                                        isChecked = uiState.autoNext,
                                        onToggle = { onAutoNextToggle(it) }
                                    )
                                }
                                item {
                                    ToggleMenuItem(
                                        icon = { Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White) },
                                        title = "Skip intro",
                                        isChecked = uiState.autoSkipIntro,
                                        onToggle = { onAutoSkipIntroToggle(it) }
                                    )
                                }
                                item {
                                    ToggleMenuItem(
                                        icon = { Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White) },
                                        title = "Skip outro",
                                        isChecked = uiState.autoSkipOutro,
                                        onToggle = { onAutoSkipOutroToggle(it) }
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
            Text(subtitle, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SubMenuHeader(title: String, onBack: () -> Unit) {
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
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
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
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
                    checkedTrackColor = Color.White.copy(alpha = 0.5f),
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
