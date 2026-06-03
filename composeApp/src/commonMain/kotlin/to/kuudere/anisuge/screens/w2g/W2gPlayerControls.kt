package to.kuudere.anisuge.screens.w2g

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
import to.kuudere.anisuge.data.models.SkipData
import to.kuudere.anisuge.data.models.StreamingData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import to.kuudere.anisuge.player.VideoPlayerState
import to.kuudere.anisuge.platform.PipManager

/**
 * Shared cross-platform player controls overlay.
 * Renders on top of VideoPlayerSurface in the Compose layer.
 */
@Composable
fun W2gPlayerControls(
    playerState: VideoPlayerState,
    streamingData: StreamingData? = null,
    /** Persists across quality/server reload when [streamingData] is temporarily null. */
    introMarker: SkipData? = null,
    outroMarker: SkipData? = null,
    title: String = "",
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {},
    onBack: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onPrevEpisode: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onBoostSpeedChange: (Boolean) -> Unit = {},
    onPlayPause: ((shouldPlay: Boolean) -> Unit)? = null,
    onSeek: ((position: Double) -> Unit)? = null,
    isOffline: Boolean = false,
    onExit: () -> Unit = {},
    // Sync button callbacks
    onSyncMALClick: (() -> Unit)? = null,
    onSyncAniListClick: (() -> Unit)? = null,
    isSyncingMAL: Boolean = false,
    isSyncingAniList: Boolean = false,
    pipManager: PipManager? = null,
    showLibraryActions: Boolean = true,
    showFullscreenButton: Boolean = to.kuudere.anisuge.platform.isDesktopPlatform,
    compactControls: Boolean = false,
    showMediaControls: Boolean = true,
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

    val isLoading =
        playerState.isBuffering || (!playerState.isPlaying && playerState.duration <= 0.0) || expectedPosition != null

    fun shouldKeepControlsVisible(): Boolean = isSeeking || isLoading
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
                    onPress = {
                        if (!playerState.isLocked && !isLoading) {
                            val pressStart = Clock.System.now().toEpochMilliseconds()
                            tryAwaitRelease()
                            val heldForMs = Clock.System.now().toEpochMilliseconds() - pressStart
                            if (heldForMs >= 350L) onBoostSpeedChange(false)
                        }
                    },
                    onLongPress = {
                        if (!playerState.isLocked && !isLoading) {
                            onBoostSpeedChange(true)
                            recordInteraction(forceShow = false)
                        }
                    },
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
                            onSeek?.invoke(newPos)

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
                            onSeek?.invoke(newPos)

                            doubleTapResetJob?.cancel()
                            doubleTapResetJob = scope.launch {
                                delay(650)
                                doubleTapSide = null
                                doubleTapAmount = 0
                            }
                        } else {
                            // Center double tap toggles play/pause
                            val wasPaused = playerState.isPaused
                            playerState.pauseRequested = !wasPaused
                            val shouldPlay = wasPaused
                            onPlayPause?.invoke(shouldPlay)
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
                Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
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
                    Column(Modifier.fillMaxWidth().padding(bottom = 0.dp)) {
                            // 1. Progress Bar Row
                            val duration = playerState.duration
                            val activePosition =
                                if (isSeeking) seekValue.toDouble() else expectedPosition ?: playerState.position
                            val progress =
                                if (duration > 0) (activePosition / duration).toFloat().coerceIn(0f, 1f) else 0f
                            val introRange = introMarker ?: streamingData?.intro
                            val outroRange = outroMarker ?: streamingData?.outro

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
                                        .then(
                                            if (showMediaControls) Modifier.pointerInput(duration) {
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        if (duration <= 0) return@detectTapGestures
                                                        val tapValue =
                                                            ((offset.x / size.width) * duration).coerceIn(0.0, duration)
                                                        playerState.seekTarget = tapValue
                                                        onSeek?.invoke(tapValue)
                                                        expectedPosition = tapValue
                                                        recordInteraction(forceShow = false)
                                                    }
                                                )
                                            } else Modifier
                                        )
                                        .then(
                                            if (showMediaControls) Modifier.pointerInput(duration) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        if (duration <= 0) return@detectDragGestures
                                                        isSeeking = true
                                                        controlsVisible = true
                                                        recordInteraction(forceShow = false)
                                                        seekValue = ((offset.x / size.width) * duration).toFloat()
                                                            .coerceIn(0f, duration.toFloat())
                                                    },
                                                    onDrag = { change, _ ->
                                                        if (duration <= 0) return@detectDragGestures
                                                        seekValue = ((change.position.x / size.width) * duration).toFloat()
                                                            .coerceIn(0f, duration.toFloat())
                                                    },
                                                onDragEnd = {
                                                    if (duration > 0) {
                                                        val target = seekValue.toDouble()
                                                        playerState.seekTarget = target
                                                        onSeek?.invoke(target)
                                                        expectedPosition = target
                                                        isSeeking = false
                                                        recordInteraction(forceShow = false)
                                                    }
                                                },
                                                    onDragCancel = {
                                                        isSeeking = false; recordInteraction(forceShow = false)
                                                    }
                                                )
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    val bufferedProgress =
                                        if (duration > 0) (playerState.bufferedPosition / duration).toFloat()
                                            .coerceIn(0f, 1f) else 0f
                                    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        val corner = CornerRadius(4.dp.toPx())

                                        drawRoundRect(
                                            Color.White.copy(alpha = 0.25f),
                                            size = size,
                                            cornerRadius = corner
                                        )
                                        if (bufferedProgress > progress) {
                                            drawRoundRect(
                                                Color.White.copy(alpha = 0.5f),
                                                size = Size(w * bufferedProgress, h),
                                                cornerRadius = corner
                                            )
                                        }

                                        fun drawSkipSpan(startSec: Double, endSec: Double, color: Color) {
                                            if (duration <= 0) return
                                            val endClamped = endSec.coerceAtMost(duration)
                                            val startClamped = startSec.coerceIn(0.0, endClamped)
                                            val x0 = ((startClamped / duration).toFloat() * w).coerceIn(0f, w)
                                            val x1 = ((endClamped / duration).toFloat() * w).coerceIn(0f, w)
                                            if (x1 > x0) drawRect(color, Offset(x0, 0f), Size(x1 - x0, h))
                                        }

                                        drawRoundRect(Color.White, size = Size(w * progress, h), cornerRadius = corner)

                                        // Draw skip regions on top so outro at end of timeline stays visible
                                        if (introRange?.start != null && introRange.end != null) {
                                            drawSkipSpan(
                                                introRange.start,
                                                introRange.end,
                                                Color.Yellow.copy(alpha = 0.9f)
                                            )
                                        }
                                        if (outroRange?.start != null && outroRange.end != null) {
                                            drawSkipSpan(
                                                outroRange.start,
                                                outroRange.end,
                                                Color(0xFFFF8A80).copy(alpha = 0.9f)
                                            )
                                        }

                                        streamingData?.chapters?.forEach { ch ->
                                            if (ch.start_time != null && ch.start_time > 0 && duration > 0) {
                                                val x = (ch.start_time / duration).toFloat() * w
                                                if (x > 1f && x < w - 1f) {
                                                    drawRect(
                                                        Color.Black.copy(alpha = 0.8f),
                                                        Offset(x - 1.dp.toPx(), 0f),
                                                        Size(2.dp.toPx(), h)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Box(Modifier.fillMaxWidth(progress).wrapContentWidth(Alignment.End)) {
                                        Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text = formatDuration(duration),
                                    color = Color.White.copy(alpha = 0.8f),
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
                                // Left actions: Volume
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        playerState.isMuted = !playerState.isMuted; recordInteraction(forceShow = false)
                                    }, modifier = Modifier.size(38.dp)) {
                                        Icon(
                                            if (playerState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    if (pipManager?.isAvailable == true && !pipManager.isActive) {
                                        IconButton(
                                            onClick = {
                                                pipManager.requestPip(16, 9)
                                                recordInteraction(forceShow = false)
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                getPictureInPictureIcon(),
                                                contentDescription = "Picture in picture",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                if (showMediaControls) {
                                    // Main Playback controls
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            val nPos = (playerState.position - 10).coerceAtLeast(0.0)
                                            playerState.seekTarget = nPos
                                            onSeek?.invoke(nPos)
                                            expectedPosition = nPos
                                            recordInteraction(forceShow = false)
                                        }, modifier = Modifier.size(40.dp)) {
                                            Icon(
                                                Icons.Default.Replay10,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        if (!compactControls) {
                                            IconButton(
                                                onClick = { onPrevEpisode(); recordInteraction(forceShow = false) },
                                                enabled = playerState.hasPrevEpisode,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.SkipPrevious,
                                                    null,
                                                    tint = if (playerState.hasPrevEpisode) Color.White else Color.Gray,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }

                                        // BIG PLAY BUTTON
                                        Box(
                                            Modifier.size(54.dp).padding(4.dp).clip(CircleShape).background(Color.White)
                                                .clickable {
                                                    val wasPaused = playerState.isPaused
                                                    playerState.pauseRequested = !wasPaused
                                                    val shouldPlay = wasPaused
                                                    onPlayPause?.invoke(shouldPlay)
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

                                        if (!compactControls) {
                                            IconButton(
                                                onClick = { onNextEpisode(); recordInteraction(forceShow = false) },
                                                enabled = playerState.hasNextEpisode,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.SkipNext,
                                                    null,
                                                    tint = if (playerState.hasNextEpisode) Color.White else Color.Gray,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = {
                                            val nPos = (playerState.position + 10).coerceAtMost(duration)
                                            playerState.seekTarget = nPos
                                            onSeek?.invoke(nPos)
                                            expectedPosition = nPos
                                            recordInteraction(forceShow = false)
                                        }, modifier = Modifier.size(40.dp)) {
                                            Icon(
                                                Icons.Default.Forward10,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                // Right actions: Chat, Fullscreen/PiP.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        onChatClick(); recordInteraction(forceShow = false)
                                    }, modifier = Modifier.size(40.dp)) {
                                        Icon(
                                            Icons.Default.Chat,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    // MAL Sync button
                                    if (onSyncMALClick != null) {
                                        IconButton(
                                            onClick = { onSyncMALClick?.invoke(); recordInteraction(forceShow = false) },
                                            modifier = Modifier.size(38.dp),
                                            enabled = !isOffline && !isSyncingMAL
                                        ) {
                                            if (isSyncingMAL) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp,
                                                    progress = { 0.7f }
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Sync,
                                                    "Sync to MAL",
                                                    tint = if (isOffline) Color.Gray else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    // AniList Sync button
                                    if (onSyncAniListClick != null) {
                                        IconButton(
                                            onClick = { onSyncAniListClick?.invoke(); recordInteraction(forceShow = false) },
                                            modifier = Modifier.size(38.dp),
                                            enabled = !isOffline && !isSyncingAniList
                                        ) {
                                            if (isSyncingAniList) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp,
                                                    progress = { 0.7f }
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Sync,
                                                    "Sync to AniList",
                                                    tint = if (isOffline) Color.Gray else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (showFullscreenButton) {
                                        IconButton(
                                            onClick = { onFullscreenToggle(); recordInteraction(forceShow = false) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
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

private fun formatDuration(seconds: Double): String = to.kuudere.anisuge.utils.formatDuration(seconds)

private fun getPictureInPictureIcon(): ImageVector {
    return ImageVector.Builder(
        name = "PictureInPictureCustom",
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
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
            verticalLineTo(16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
            horizontalLineTo(4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
            verticalLineTo(8f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
            close()
            moveTo(13f, 12f)
            horizontalLineTo(19f)
            verticalLineTo(16f)
            horizontalLineTo(13f)
            close()
        }
    }.build()
}

