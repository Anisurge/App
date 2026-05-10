package to.kuudere.anisuge.screens.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.ScheduleAnime
import to.kuudere.anisuge.i18n.resolveDisplayTitle
import to.kuudere.anisuge.ui.OfflineState
import to.kuudere.anisuge.ui.tvFocusableClick
import to.kuudere.anisuge.i18n.LocalAppStrings

// ── Colours ── Black & white only ─────────────────────────────────────────────

private val BG     = Color(0xFF000000)
private val BORDER = Color.White.copy(alpha = 0.10f)
private val MUTED  = Color.White.copy(alpha = 0.50f)
private val CARD   = Color.White.copy(alpha = 0.03f)
private val CARD_H = Color.White.copy(alpha = 0.07f)

// ── Date helpers ──────────────────────────────────────────────────────────────

private val DOW_NAMES = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")

private fun dayOfWeek(y: Int, m: Int, d: Int): Int {
    val ay = if (m < 3) y - 1 else y
    val am = if (m < 3) m + 12 else m
    val k = ay % 100; val j = ay / 100
    return ((d + (13 * (am + 1)) / 5 + k + k / 4 + j / 4 - 2 * j) % 7 + 5) % 7
}

private fun todayString(timezone: String = "UTC"): String {
    val tz = runCatching { TimeZone.of(timezone) }.getOrElse { TimeZone.UTC }
    return Clock.System.now().toLocalDateTime(tz).date.toString()
}

private fun formatDateHeader(dateStr: String, today: String): String {
    val (ys, ms, ds) = dateStr.split("-")
    val y = ys.toInt(); val m = ms.toInt(); val d = ds.toInt()
    val dow = DOW_NAMES[dayOfWeek(y, m, d)]
    val fmt = "${ms}/${ds}/${ys}"
    val tomorrowStr = runCatching {
        LocalDate.parse(today).plus(DatePeriod(days = 1)).toString()
    }.getOrNull()
    return when (dateStr) {
        today       -> "Today ($fmt)"
        tomorrowStr -> "Tomorrow ($fmt)"
        else        -> "$dow ($fmt)"
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onAnimeClick: (String) -> Unit,
    onExit: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current

    Box(Modifier.fillMaxSize().background(BG)) {
        when {
            state.isLoading && state.schedule.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
            }

            state.isOffline && state.schedule.isEmpty() -> OfflineState(onRetry = { viewModel.refresh() }, isLoading = state.isLoading)

            state.error != null && state.schedule.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.CalendarToday, null, tint = MUTED, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(strings.failedToLoadSchedule, color = MUTED, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.refresh() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                ) { Text(strings.retry, color = Color.Black) }
            }

            else -> {
                val today = remember(state.timezone) { todayString(state.timezone) }
                val sortedDates = remember(state.schedule) { state.schedule.keys.sorted() }
                val visibleDates = remember(sortedDates, today) {
                    val datesFromToday = sortedDates.filter { it >= today }
                    if (datesFromToday.isNotEmpty()) datesFromToday else sortedDates
                }
                val listState = rememberLazyListState()
                // Keys of items that have already played their enter animation
                val animatedKeys = remember { mutableStateSetOf<String>() }

                // ── Sentinel: trigger loadMore when near the end ───────────
                val nearEnd by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
                        last >= info.totalItemsCount - 4
                    }
                }
                LaunchedEffect(nearEnd) {
                    // Schedule uses date-based navigation, not pagination
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cols = when {
                        maxWidth >= 900.dp -> 3
                        maxWidth >= 580.dp -> 2
                        else               -> 1
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (maxWidth < 800.dp) 156.dp else 20.dp),
                    ) {
                        item {
                           Box(Modifier.fillMaxWidth()) {
                                to.kuudere.anisuge.platform.WindowManagementButtons(
                                    onClose = onExit,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                           }
                        }

                        visibleDates.forEachIndexed { dateIdx, dateStr ->
                            val animeList = state.schedule[dateStr] ?: return@forEachIndexed
                            val isToday = dateStr == today

                            // ── Date header ───────────────────────────────
                            item(key = "hdr-$dateStr") {
                                AnimatedEntry(
                                    itemKey = "hdr-$dateStr",
                                    animatedKeys = animatedKeys,
                                    delayMs = (dateIdx * 60).coerceAtMost(300)
                                ) {
                                    Column {
                                        if (dateIdx > 0) Spacer(Modifier.height(28.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isToday) {
                                                Box(
                                                    Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(strings.today, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(Modifier.width(10.dp))
                                            }
                                            Text(
                                                formatDateHeader(dateStr, today),
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        // thin separator under header
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(BORDER))
                                        Spacer(Modifier.height(12.dp))
                                    }
                                }
                            }

                            // ── Card rows ─────────────────────────────────
                            val rows = animeList.chunked(cols)
                            rows.forEachIndexed { rowIdx, row ->
                                item(key = "${dateStr}-r$rowIdx") {
                                    AnimatedEntry(
                                        itemKey = "${dateStr}-r$rowIdx",
                                        animatedKeys = animatedKeys,
                                        delayMs = (dateIdx * 60 + rowIdx * 40).coerceAtMost(400)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            row.forEach { anime ->
                                                AnimeScheduleCard(
                                                    anime = anime,
                                                    onClick = { onAnimeClick(anime.activeSlug) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Skeleton placeholder while loading more ────────
                        if (state.isLoading) {
                            item(key = "skeleton") {
                                Column {
                                    Spacer(Modifier.height(28.dp))
                                    // Skeleton header
                                    SkeletonBox(Modifier.width(200.dp).height(22.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(BORDER))
                                    Spacer(Modifier.height(12.dp))
                                    // Skeleton cards
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        repeat(3) {
                                            SkeletonBox(Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(10.dp)))
                                        }
                                    }
                                    Spacer(Modifier.height(80.dp))
                                }
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                    // ── Scroll to top FAB ─────────────────────────────────
                    val showScrollTop by remember {
                        derivedStateOf { listState.firstVisibleItemIndex > 0 }
                    }
                    val coroutineScope = rememberCoroutineScope()
                    AnimatedVisibility(
                        visible = showScrollTop,
                        enter = fadeIn(tween(250)),
                        exit  = fadeOut(tween(250)),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    ) {
                        ScrollToTopButton {
                            coroutineScope.launch {
                                // Jump close first so the animated portion is short (visible range only)
                                if (listState.firstVisibleItemIndex > 4) {
                                    listState.scrollToItem(4)
                                }
                                listState.animateScrollToItem(0)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Scroll to top button ──────────────────────────────────────────────────────

@Composable
private fun ScrollToTopButton(onClick: () -> Unit) {
    val strings = LocalAppStrings.current
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()

    val bg by animateColorAsState(
        if (hovered) Color.White else Color.White.copy(alpha = 0.85f),
        tween(200)
    )
    val iconColor by animateColorAsState(
        if (hovered) Color.Black else Color.Black.copy(alpha = 0.9f),
        tween(200)
    )

    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .hoverable(inter)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.KeyboardArrowUp,
            contentDescription = strings.scrollToTop,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Animated entry wrapper ────────────────────────────────────────────────────

@Composable
private fun AnimatedEntry(
    itemKey: String,
    animatedKeys: MutableSet<String>,
    delayMs: Int = 0,
    content: @Composable () -> Unit,
) {
    val alreadySeen = itemKey in animatedKeys
    var visible by remember(itemKey) { mutableStateOf(alreadySeen) }

    LaunchedEffect(itemKey) {
        if (!alreadySeen) {
            delay(delayMs.toLong())
            visible = true
            animatedKeys.add(itemKey)
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(320))
    val offsetY by animateFloatAsState(if (visible) 0f else 20f, tween(320))

    Box(
        Modifier
            .alpha(alpha)
            .offset(y = offsetY.dp)
    ) {
        content()
    }
}

// ── Skeleton shimmer ──────────────────────────────────────────────────────────

@Composable
private fun SkeletonBox(modifier: Modifier = Modifier) {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = !pulse
            delay(700)
        }
    }
    val alpha by animateFloatAsState(if (pulse) 0.08f else 0.04f, tween(700))
    Box(modifier.background(Color.White.copy(alpha = alpha)))
}

// ── Anime card ────────────────────────────────────────────────────────────────

@Composable
private fun AnimeScheduleCard(
    anime: ScheduleAnime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()

    val bgColor by animateColorAsState(if (hovered) CARD_H else CARD, tween(300, easing = FastOutSlowInEasing))
    val borderColor by animateColorAsState(
        if (hovered) Color.White.copy(alpha = 0.20f) else BORDER, tween(300, easing = FastOutSlowInEasing)
    )
    val scale by animateFloatAsState(if (hovered) 1.015f else 1f, tween(300, easing = FastOutSlowInEasing))

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .hoverable(inter)
            .tvFocusableClick(shape = RoundedCornerShape(10.dp), onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────
        Box(
            Modifier
                .width(80.dp)
                .height(108.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (anime.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = anime.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Episode badge — white pill, black text
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(strings.episodeShort(anime.displayEpisodeNumber), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── Info ──────────────────────────────────────────────────────
        Column(Modifier.weight(1f)) {
            // Dot + title
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    anime.resolveDisplayTitle(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(5.dp))

            // Time + type
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (anime.time.isNotBlank()) {
                    Text(anime.time, color = MUTED, fontSize = 12.sp)
                    Text(" • ", color = MUTED, fontSize = 12.sp)
                }
                Text((anime.type ?: anime.format).ifBlank { "TV" }, color = MUTED, fontSize = 12.sp)
            }

            Spacer(Modifier.height(7.dp))

            // Description
            val desc = anime.description.replace(Regex("<[^>]*>"), "").trim()
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
