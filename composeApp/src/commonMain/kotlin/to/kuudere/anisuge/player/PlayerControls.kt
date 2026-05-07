package to.kuudere.anisuge.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.data.models.StreamingData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared cross-platform player controls overlay.
 * Renders on top of VideoPlayerSurface in the Compose layer.
 */
@Composable
fun PlayerControls(
    playerState: VideoPlayerState,
    streamingData: StreamingData? = null,
    title: String = "",
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {},
    onBack: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onPrevEpisode: () -> Unit = {},
    onCaptionsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onEpisodesClick: () -> Unit = {},
    onCommentsClick: () -> Unit = {},
    onWatchlistClick: () -> Unit = {},
    isInWatchlist: Boolean = false,
    currentFolder: String? = null,
    isOffline: Boolean = false,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hideDelayMillis = 1500L
    val pointerMoveThrottleMillis = 250L
    var controlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }
    var expectedPosition by remember { mutableStateOf<Double?>(null) }
    val scope = rememberCoroutineScope()
    var lastInteractionAt by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var lastPointerMoveWakeAt by remember { mutableStateOf(0L) }

    val isLoading = playerState.isBuffering || (!playerState.isPlaying && playerState.duration <= 0.0) || expectedPosition != null
    
    fun shouldKeepControlsVisible(): Boolean = playerState.isLocked || isSeeking || isLoading
    fun recordInteraction(forceShow: Boolean = true) {
        lastInteractionAt = Clock.System.now().toEpochMilliseconds()
        if (forceShow) controlsVisible = true
    }

    // Clear expected position when actual catches up
    LaunchedEffect(playerState.position) {
        val expected = expectedPosition
        if (expected != null && kotlin.math.abs(playerState.position - expected) < 2.0) {
            expectedPosition = null
        }
    }

    // Timeout expected position after 10s (increased from 3s) to prevent infinite freeze
    LaunchedEffect(expectedPosition) {
        if (expectedPosition != null) {
            delay(10000)
            expectedPosition = null
        }
    }

    // Show controls initially, then let idle timer handle auto-hide.
    LaunchedEffect(Unit) { 
        controlsVisible = true
        recordInteraction(forceShow = false)
    }

    // Keep controls visible while loading, then restart idle timer once loading settles.
    LaunchedEffect(isLoading) {
        if (isLoading) {
            controlsVisible = true
        } else {
            recordInteraction(forceShow = false)
        }
    }

    // Restart idle timer after scrubbing ends.
    LaunchedEffect(isSeeking) {
        if (!isSeeking) recordInteraction(forceShow = false)
    }

    // Keep controls visible while locked and reset idle baseline on lock changes.
    LaunchedEffect(playerState.isLocked) {
        if (playerState.isLocked) controlsVisible = true
        recordInteraction(forceShow = false)
    }

    // Single auto-hide owner with final-state guards.
    LaunchedEffect(lastInteractionAt, playerState.isLocked, isSeeking, isLoading) {
        if (shouldKeepControlsVisible()) {
            controlsVisible = true
            return@LaunchedEffect
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val remaining = (hideDelayMillis - (now - lastInteractionAt)).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        if (!shouldKeepControlsVisible()) {
            controlsVisible = false
        }
    }

    // Hook up desktop AWT Canvas clicks to toggle visibility
    LaunchedEffect(playerState.canvasClicked) {
        if (playerState.canvasClicked > 0) {
            if (playerState.isLocked) {
                controlsVisible = true
                recordInteraction(forceShow = false)
            } else {
                controlsVisible = !controlsVisible
                if (controlsVisible) recordInteraction(forceShow = false)
            }
        }
    }

    // Double Tap Seek State
    var doubleTapSide by remember { mutableStateOf<String?>(null) } // "left", "right"
    var doubleTapAmount by remember { mutableStateOf(0) }
    var doubleTapCounter by remember { mutableStateOf(0) } // To trigger re-animation
    var doubleTapResetJob by remember { mutableStateOf<Job?>(null) }

    // Hook up desktop AWT Canvas pointer moves to wake up controls
    LaunchedEffect(playerState.canvasPointerMoved) {
        if (playerState.canvasPointerMoved > 0) {
            recordInteraction()
        }
    }

    val deviceControls = to.kuudere.anisuge.platform.rememberDeviceControls()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput("hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Move) {
                            val now = Clock.System.now().toEpochMilliseconds()
                            if (now - lastPointerMoveWakeAt >= pointerMoveThrottleMillis) {
                                lastPointerMoveWakeAt = now
                                recordInteraction(forceShow = !controlsVisible)
                            }
                        }
                    }
                }
            }
            .pointerInput(playerState.isLocked) {
                detectTapGestures(
                    onTap = {
                        if (playerState.isLocked) {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) recordInteraction(forceShow = false)
                            return@detectTapGestures
                        }

                        // If double tap is "warm", treat tap on same side as additional seek
                        val warmSide = doubleTapSide
                        if (warmSide != null) {
                            // Tapping while animation is active? 
                            // detectTapGestures doesn't easily let us capture individual taps after double tap 
                            // But for now, we'll keep the standard behavior.
                        }

                        if (isLoading) {
                            controlsVisible = true
                            recordInteraction(forceShow = false)
                        } else {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) recordInteraction(forceShow = false)
                        }
                    },
                    onDoubleTap = { offset ->
                        if (playerState.isLocked) return@detectTapGestures
                        val width = size.width
                        val side = if (offset.x < width / 3) "left" 
                                  else if (offset.x > width * 2 / 3) "right"
                                  else "center"

                        if (side == "left") {
                            doubleTapSide = "left"
                            doubleTapAmount += 10
                            doubleTapCounter++
                            val newPos = (playerState.position - 10.0).coerceAtLeast(0.0)
                            playerState.seekTarget = newPos
                            expectedPosition = newPos
                            
                            doubleTapResetJob?.cancel()
                            doubleTapResetJob = scope.launch {
                                delay(650)
                                doubleTapSide = null
                                doubleTapAmount = 0
                            }
                        } else if (side == "right") {
                            doubleTapSide = "right"
                            doubleTapAmount += 10
                            doubleTapCounter++
                            val newPos = (playerState.position + 10.0).coerceAtMost(playerState.duration)
                            playerState.seekTarget = newPos
                            expectedPosition = newPos

                            doubleTapResetJob?.cancel()
                            doubleTapResetJob = scope.launch {
                                delay(650)
                                doubleTapSide = null
                                doubleTapAmount = 0
                            }
                        } else {
                            // Center double tap toggles play/pause
                            playerState.pauseRequested = !playerState.isPaused
                        }
                        
                        controlsVisible = true
                        recordInteraction(forceShow = false)
                    }
                )
            }
            .pointerInput(playerState.isLocked) {
                if (playerState.isLocked) return@pointerInput
                var startVolume = 100.0
                var startBrightness = 0.0
                var isVolumeDrag = false
                
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (to.kuudere.anisuge.platform.isDesktopPlatform) {
                            startVolume = playerState.volume
                            startBrightness = playerState.brightness
                        } else {
                            startVolume = deviceControls.currentVolume.toDouble()
                            startBrightness = deviceControls.currentBrightness.toDouble()
                        }
                        isVolumeDrag = offset.x > size.width / 2f
                    },
                    onDragEnd = {
                        playerState.indicatorText = null
                    },
                    onDragCancel = {
                        playerState.indicatorText = null
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -(dragAmount / size.height)
                        if (isVolumeDrag) {
                            if (to.kuudere.anisuge.platform.isDesktopPlatform) {
                                val deltaVol = delta * 150.0
                                val newVol = (startVolume + deltaVol).coerceIn(0.0, 130.0)
                                startVolume = newVol
                                playerState.volume = newVol
                                playerState.indicatorText = "Volume: ${newVol.toInt()}%"
                            } else {
                                val deltaVol = delta * 1.5
                                val newVol = (startVolume + deltaVol).coerceIn(0.0, 1.0)
                                startVolume = newVol
                                deviceControls.setVolume(newVol.toFloat())
                                playerState.indicatorText = "Volume: ${(newVol * 100).toInt()}%"
                            }
                        } else {
                            if (to.kuudere.anisuge.platform.isDesktopPlatform) {
                                val deltaBri = delta * 150.0
                                val newBri = (startBrightness + deltaBri).coerceIn(-100.0, 100.0)
                                startBrightness = newBri
                                playerState.brightness = newBri
                                playerState.indicatorText = "Brightness: ${((newBri + 100) / 2).toInt()}%"
                            } else {
                                val deltaBri = delta * 1.5
                                val newBri = (startBrightness + deltaBri).coerceIn(0.0, 1.0)
                                startBrightness = newBri
                                deviceControls.setBrightness(newBri.toFloat())
                                playerState.indicatorText = "Brightness: ${(newBri * 100).toInt()}%"
                            }
                        }
                    }
                )
            }
    ) {
        // Desktop-only: hide the OS cursor when controls are hidden.
        to.kuudere.anisuge.platform.SyncCursorHidden(
            hidden = to.kuudere.anisuge.platform.isDesktopPlatform && !controlsVisible
        )

        // Double Tap Seek Animation Overlay
        DoubleTapSeekOverlay(
            side = doubleTapSide,
            amount = doubleTapAmount,
            counter = doubleTapCounter
        )

        AnimatedVisibility(
            visible = playerState.indicatorText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center).padding(bottom = 64.dp)
        ) {
            Box(
                Modifier.background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = playerState.indicatorText ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
        
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top gradient + title bar (Only show if not locked)
                if (!playerState.isLocked) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                            .align(Alignment.TopCenter)
                            .padding(bottom = 24.dp) 
                    ) {
                    to.kuudere.anisuge.platform.DraggableWindowArea(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            if (title.isNotEmpty()) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            IconButton(onClick = onCaptionsClick) {
                                Icon(
                                    getCCIcon(),
                                    contentDescription = "Captions",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            
                            to.kuudere.anisuge.platform.WindowManagementButtons(
                                onClose = onExit
                            )
                        }
                    }
                    }
                }

                // Center Loading Indicator (High-Performance Dual-Circle)
                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.Center).size(60.dp)) {
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
                            modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = rotateCW },
                            color = Color.White,
                            strokeWidth = 2.dp,
                            trackColor = Color.White.copy(alpha = 0.1f),
                            strokeCap = StrokeCap.Round
                        )

                        // Inner Circle
                        CircularProgressIndicator(
                            progress = { 0.6f },
                            modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = rotateCCW },
                            color = Color.White.copy(alpha = 0.6f),
                            strokeWidth = 2.dp,
                            trackColor = Color.White.copy(alpha = 0.05f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                // Bottom Controls Area
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                ) {
                    if (playerState.isLocked) {
                        // Only show Lock button if locked
                        Row(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            IconButton(
                                onClick = { playerState.isLocked = false; recordInteraction(forceShow = false) },
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha=0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(bottom = 0.dp)) {
                            // 1. Progress Bar Row
                            val duration = playerState.duration
                            val activePosition = if (isSeeking) seekValue.toDouble() else expectedPosition ?: playerState.position
                            val progress = if (duration > 0) (activePosition / duration).toFloat().coerceIn(0f, 1f) else 0f

                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration(activePosition),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.width(12.dp))

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .pointerInput(duration) {
                                            detectTapGestures(
                                                onTap = { offset ->
                                                    if (duration <= 0) return@detectTapGestures
                                                    val tapValue = ((offset.x / size.width) * duration).coerceIn(0.0, duration)
                                                    playerState.seekTarget = tapValue
                                                    expectedPosition = tapValue
                                                    recordInteraction(forceShow = false)
                                                }
                                            )
                                        }
                                        .pointerInput(duration) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    if (duration <= 0) return@detectDragGestures
                                                    isSeeking = true
                                                    controlsVisible = true
                                                    recordInteraction(forceShow = false)
                                                    seekValue = ((offset.x / size.width) * duration).toFloat().coerceIn(0f, duration.toFloat())
                                                },
                                                onDrag = { change, _ ->
                                                    if (duration <= 0) return@detectDragGestures
                                                    seekValue = ((change.position.x / size.width) * duration).toFloat().coerceIn(0f, duration.toFloat())
                                                },
                                                onDragEnd = {
                                                    if (duration > 0) {
                                                        playerState.seekTarget = seekValue.toDouble()
                                                        expectedPosition = seekValue.toDouble()
                                                        isSeeking = false
                                                        recordInteraction(forceShow = false)
                                                    }
                                                },
                                                onDragCancel = { isSeeking = false; recordInteraction(forceShow = false) }
                                            )
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    val bufferedProgress = if (duration > 0) (playerState.bufferedPosition / duration).toFloat().coerceIn(0f, 1f) else 0f
                                    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                                        val w = size.width
                                        val h = size.height

                                        // Background track (dark)
                                        drawRoundRect(Color.White.copy(alpha = 0.25f), size = size, cornerRadius = CornerRadius(4.dp.toPx()))
                                        // Buffered portion (light gray) - between background and progress
                                        if (bufferedProgress > progress) {
                                            drawRoundRect(Color.White.copy(alpha = 0.5f), size = Size(w * bufferedProgress, h), cornerRadius = CornerRadius(4.dp.toPx()))
                                        }
                                        // White progress fill
                                        drawRoundRect(Color.White, size = Size(w * progress, h), cornerRadius = CornerRadius(4.dp.toPx()))

                                        // Intro highlight — yellow ON TOP of fill so always visible
                                        val intro = streamingData?.intro
                                        if (intro?.start != null && intro.end != null && duration > 0) {
                                            val x0 = ((intro.start / duration).toFloat() * w).coerceIn(0f, w)
                                            val x1 = ((intro.end / duration).toFloat() * w).coerceIn(0f, w)
                                            if (x1 > x0) drawRect(Color.Yellow, Offset(x0, 0f), Size(x1 - x0, h))
                                        }

                                        // Outro highlight — also yellow
                                        val outro = streamingData?.outro
                                        if (outro?.start != null && outro.end != null && duration > 0) {
                                            val x0 = ((outro.start / duration).toFloat() * w).coerceIn(0f, w)
                                            val x1 = ((outro.end / duration).toFloat() * w).coerceIn(0f, w)
                                            if (x1 > x0) drawRect(Color.Yellow, Offset(x0, 0f), Size(x1 - x0, h))
                                        }

                                        // Chapter dividers
                                        streamingData?.chapters?.forEach { ch ->
                                            if (ch.start_time != null && ch.start_time > 0 && duration > 0) {
                                                val x = (ch.start_time / duration).toFloat() * w
                                                if (x > 1f && x < w - 1f)
                                                    drawRect(Color.Black.copy(alpha = 0.8f), Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), h))
                                            }
                                        }
                                    }
                                    // Thumb
                                    Box(Modifier.fillMaxWidth(progress).wrapContentWidth(Alignment.End)) {
                                        Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text = formatDuration(duration),
                                    color = Color.White.copy(alpha=0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 2. Control Row
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 0.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left actions: Lock, Volume, Watchlist
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { playerState.isLocked = true; recordInteraction(forceShow = false) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.LockOpen, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { playerState.isMuted = !playerState.isMuted; recordInteraction(forceShow = false) }, modifier = Modifier.size(38.dp)) {
                                        Icon(if (playerState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                    IconButton(onClick = { if (!isOffline) onWatchlistClick(); recordInteraction(forceShow = false) }, modifier = Modifier.size(38.dp), enabled = !isOffline) {
                                        Icon(
                                            if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, 
                                            null, 
                                            tint = if (isOffline) Color.Gray else Color.White, 
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                // Main Playback controls
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { 
                                        val nPos = (playerState.position - 10).coerceAtLeast(0.0)
                                        playerState.seekTarget = nPos; expectedPosition = nPos; recordInteraction(forceShow = false) 
                                    }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                    IconButton(
                                        onClick = { onPrevEpisode(); recordInteraction(forceShow = false) }, 
                                        enabled = playerState.hasPrevEpisode,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, null, tint = if (playerState.hasPrevEpisode) Color.White else Color.Gray, modifier = Modifier.size(26.dp))
                                    }
                                    
                                    // BIG PLAY BUTTON
                                    Box(
                                        Modifier.size(54.dp).padding(4.dp).clip(CircleShape).background(Color.White).clickable { 
                                            playerState.pauseRequested = !playerState.isPaused
                                            recordInteraction(forceShow = false)
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (playerState.isPaused || !playerState.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onNextEpisode(); recordInteraction(forceShow = false) }, 
                                        enabled = playerState.hasNextEpisode,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.SkipNext, null, tint = if (playerState.hasNextEpisode) Color.White else Color.Gray, modifier = Modifier.size(26.dp))
                                    }
                                    IconButton(onClick = { 
                                        val nPos = (playerState.position + 10).coerceAtMost(duration)
                                        playerState.seekTarget = nPos; expectedPosition = nPos; recordInteraction(forceShow = false) 
                                    }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                // Right actions: Info, Episodes, Comments, [Fullscreen on Desktop]
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (!isOffline) onInfoClick(); recordInteraction(forceShow = false) }, modifier = Modifier.size(38.dp), enabled = !isOffline) {
                                        Icon(Icons.Default.Info, null, tint = if (isOffline) Color.Gray else Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    IconButton(onClick = { if (!isOffline) onEpisodesClick(); recordInteraction(forceShow = false) }, modifier = Modifier.size(38.dp), enabled = !isOffline) {
                                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, tint = if (isOffline) Color.Gray else Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    IconButton(onClick = { if (!isOffline) onCommentsClick(); recordInteraction(forceShow = false) }, modifier = Modifier.size(40.dp), enabled = !isOffline) {
                                        Icon(Icons.Default.ChatBubbleOutline, null, tint = if (isOffline) Color.Gray else Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    if (to.kuudere.anisuge.platform.isDesktopPlatform) {
                                        IconButton(onClick = { onFullscreenToggle(); recordInteraction(forceShow = false) }, modifier = Modifier.size(40.dp)) {
                                            Icon(
                                                if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, 
                                                null, 
                                                tint = Color.White, 
                                                modifier = Modifier.size(26.dp)
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
    }
}

@Composable
private fun DoubleTapSeekOverlay(
    side: String?,
    amount: Int,
    counter: Int
) {
    if (side == null) return

    val isLeft = side == "left"
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.95f) }
    
    // Icon sequence animation
    val arrow1Alpha = remember { Animatable(0f) }
    val arrow2Alpha = remember { Animatable(0f) }
    val arrow3Alpha = remember { Animatable(0f) }

    LaunchedEffect(counter) {
        // Reset and run animations
        launch { 
            alpha.snapTo(0.4f)
            alpha.animateTo(0f, tween(600, easing = LinearOutSlowInEasing))
        }
        launch {
            scale.snapTo(0.95f)
            scale.animateTo(1.05f, tween(600, easing = LinearOutSlowInEasing))
        }
        
        // Sequenced arrows like YT
        val arrowTween = 150
        launch {
            arrow1Alpha.snapTo(0f)
            arrow1Alpha.animateTo(1f, tween(arrowTween))
            arrow1Alpha.animateTo(0.2f, tween(arrowTween))
        }
        launch {
            delay(100)
            arrow2Alpha.snapTo(0f)
            arrow2Alpha.animateTo(1f, tween(arrowTween))
            arrow2Alpha.animateTo(0.2f, tween(arrowTween))
        }
        launch {
            delay(200)
            arrow3Alpha.snapTo(0f)
            arrow3Alpha.animateTo(1f, tween(arrowTween))
            arrow3Alpha.animateTo(0.2f, tween(arrowTween))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val rippleWidth = w * 0.45f
                if (isLeft) {
                    drawArc(
                        color = Color.White.copy(alpha = alpha.value),
                        startAngle = 90f,
                        sweepAngle = 180f,
                        useCenter = true,
                        size = Size(rippleWidth * 2, h * 1.5f),
                        topLeft = Offset(-rippleWidth, -h * 0.25f)
                    )
                } else {
                    drawArc(
                        color = Color.White.copy(alpha = alpha.value),
                        startAngle = 270f,
                        sweepAngle = 180f,
                        useCenter = true,
                        size = Size(rippleWidth * 2, h * 1.5f),
                        topLeft = Offset(w - rippleWidth, -h * 0.25f)
                    )
                }
            },
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .graphicsLayer {
                    this.alpha = (alpha.value * 2.5f).coerceIn(0f, 1f)
                    this.scaleX = scale.value
                    this.scaleY = scale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                val icon = if (isLeft) Icons.Default.FastRewind else Icons.Default.FastForward
                Icon(icon, null, tint = Color.White.copy(alpha = arrow1Alpha.value), modifier = Modifier.size(24.dp))
                Icon(icon, null, tint = Color.White.copy(alpha = arrow2Alpha.value), modifier = Modifier.size(24.dp))
                Icon(icon, null, tint = Color.White.copy(alpha = arrow3Alpha.value), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${if (isLeft) "-" else "+"}$amount s",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong().coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

private fun getCCIcon(): ImageVector {
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
