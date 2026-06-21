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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import to.kuudere.anisuge.theme.AppColors
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import to.kuudere.anisuge.data.models.ScheduleAnime
import to.kuudere.anisuge.i18n.LocalAppStrings
import to.kuudere.anisuge.i18n.resolveDisplayTitle
import to.kuudere.anisuge.ui.OfflineState
import to.kuudere.anisuge.ui.WatchlistBottomSheet
import to.kuudere.anisuge.ui.tvFocusableClick

private val BG: Color get() = AppColors.background
private val CARD: Color get() = AppColors.surface
private val CARD_HOVER: Color get() = AppColors.surfaceVariant
private val BORDER: Color get() = AppColors.border
private val MUTED: Color get() = AppColors.textMuted
private val DIM: Color get() = AppColors.textDim
private val ACCENT: Color get() = AppColors.accent

private val DOW_NAMES = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val DOW_SHORT = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

private fun dayOfWeek(y: Int, m: Int, d: Int): Int {
    val ay = if (m < 3) y - 1 else y
    val am = if (m < 3) m + 12 else m
    val k = ay % 100
    val j = ay / 100
    return ((d + (13 * (am + 1)) / 5 + k + k / 4 + j / 4 - 2 * j) % 7 + 5) % 7
}

private fun todayString(timezone: String = "UTC"): String {
    val tz = runCatching { TimeZone.of(timezone) }.getOrElse { TimeZone.UTC }
    return Clock.System.now().toLocalDateTime(tz).date.toString()
}

private fun tomorrowString(today: String): String? = runCatching {
    LocalDate.parse(today).plus(DatePeriod(days = 1)).toString()
}.getOrNull()

private fun dateParts(dateStr: String): Triple<Int, Int, Int>? = runCatching {
    val (ys, ms, ds) = dateStr.split("-")
    Triple(ys.toInt(), ms.toInt(), ds.toInt())
}.getOrNull()

private fun formatDateHeader(dateStr: String, today: String): String {
    val (y, m, d) = dateParts(dateStr) ?: return dateStr
    val dow = DOW_NAMES[dayOfWeek(y, m, d)]
    val fmt = "${m.toString().padStart(2, '0')}/${d.toString().padStart(2, '0')}/$y"
    return when (dateStr) {
        today -> "Today • $fmt"
        tomorrowString(today) -> "Tomorrow • $fmt"
        else -> "$dow • $fmt"
    }
}

private fun formatDayChip(dateStr: String, today: String): Pair<String, String> {
    val (y, m, d) = dateParts(dateStr) ?: return dateStr to ""
    val label = when (dateStr) {
        today -> "TODAY"
        tomorrowString(today) -> "TMRW"
        else -> DOW_SHORT[dayOfWeek(y, m, d)]
    }
    return label to d.toString().padStart(2, '0')
}

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onAnimeClick: (String) -> Unit,
    onExit: () -> Unit = {},
    useLegacyUi: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current
    var watchlistAnime by remember { mutableStateOf<ScheduleAnime?>(null) }
    val visibleSchedule = remember(
        state.schedule,
        state.myListOnly,
        state.watchlistAnimeIds,
        state.watchlistAnilistIds,
        state.watchlistMalIds,
    ) { viewModel.visibleSchedule() }

    Box(Modifier.fillMaxSize().background(BG)) {
        watchlistAnime?.let { anime ->
            WatchlistBottomSheet(
                currentFolder = null,
                onSelect = { folder ->
                    val selected = watchlistAnime ?: return@WatchlistBottomSheet
                    watchlistAnime = null
                    viewModel.updateWatchlist(selected, folder)
                },
                onDismiss = { watchlistAnime = null },
            )
        }

        when {
            state.isLoading && state.schedule.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
                }
            }

            state.isOffline && state.schedule.isEmpty() -> {
                OfflineState(onRetry = { viewModel.refresh() }, isLoading = state.isLoading)
            }

            state.error != null && state.schedule.isEmpty() -> {
                EmptyScheduleError(onRetry = { viewModel.refresh() })
            }

            else -> {
                if (useLegacyUi) {
                    LegacyScheduleContent(
                        schedule = visibleSchedule,
                        timezone = state.timezone,
                        isRefreshing = state.isLoading,
                        myListOnly = state.myListOnly,
                        isLoadingWatchlist = state.isLoadingWatchlist,
                        onFilterChange = viewModel::setMyListOnly,
                        onAnimeClick = onAnimeClick,
                        onWatchlistClick = { watchlistAnime = it },
                        onExit = onExit,
                    )
                    return@Box
                }

                val today = remember(state.timezone) { todayString(state.timezone) }
                val sortedDates = remember(visibleSchedule) { visibleSchedule.keys.sorted() }
                val visibleDates = remember(sortedDates, today) {
                    sortedDates.filter { it >= today }.ifEmpty { sortedDates }
                }
                var selectedDate by remember(visibleDates) { mutableStateOf(visibleDates.firstOrNull() ?: today) }
                val selectedEpisodes = visibleSchedule[selectedDate].orEmpty()
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val isCompact = maxWidth < 700.dp
                    val columns = when {
                        maxWidth >= 1280.dp -> 3
                        maxWidth >= 760.dp -> 2
                        else -> 1
                    }
                    val horizontalPadding = if (isCompact) 16.dp else 28.dp
                    val bottomPadding = if (isCompact) 156.dp else 28.dp

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = 20.dp,
                            bottom = bottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        item(key = "header") {
                            ScheduleHeader(
                                selectedDate = selectedDate,
                                today = today,
                                totalDays = visibleDates.size,
                                totalEpisodes = visibleDates.sumOf { visibleSchedule[it].orEmpty().size },
                                timezone = state.timezone,
                                onExit = onExit,
                            )
                        }

                        item(key = "filter") {
                            ScheduleFilter(
                                myListOnly = state.myListOnly,
                                loading = state.isLoadingWatchlist,
                                onChange = viewModel::setMyListOnly,
                            )
                        }

                        item(key = "day-strip") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                items(visibleDates, key = { it }) { date ->
                                    DayChip(
                                        date = date,
                                        today = today,
                                        count = visibleSchedule[date].orEmpty().size,
                                        selected = date == selectedDate,
                                        onClick = {
                                            selectedDate = date
                                            coroutineScope.launch { listState.animateScrollToItem(0) }
                                        },
                                    )
                                }
                            }
                        }

                        item(key = "section-title") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        formatDateHeader(selectedDate, today),
                                        color = Color.White,
                                        fontSize = if (isCompact) 24.sp else 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                    Text(
                                        "${selectedEpisodes.size} scheduled release${if (selectedEpisodes.size == 1) "" else "s"}",
                                        color = MUTED,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (selectedDate == today) {
                                    Pill("LIVE DAY", selected = true)
                                }
                            }
                        }

                        if (selectedEpisodes.isEmpty()) {
                            item(key = "empty-day") {
                                EmptyDayCard(myListOnly = state.myListOnly)
                            }
                        } else {
                            selectedEpisodes.chunked(columns).forEachIndexed { rowIndex, row ->
                                item(key = "row-$selectedDate-$rowIndex") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        row.forEach { anime ->
                                            ScheduleReleaseCard(
                                                anime = anime,
                                                onClick = { onAnimeClick(anime.activeSlug) },
                                                onWatchlistClick = { watchlistAnime = anime },
                                                modifier = Modifier.weight(1f),
                                                compact = isCompact,
                                            )
                                        }
                                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }

                        if (state.isLoading) {
                            item(key = "refreshing") {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Refreshing schedule…", color = MUTED, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }
                    AnimatedVisibility(
                        visible = showScrollTop,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    ) {
                        ScrollToTopButton {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyScheduleContent(
    schedule: Map<String, List<ScheduleAnime>>,
    timezone: String,
    isRefreshing: Boolean,
    myListOnly: Boolean,
    isLoadingWatchlist: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onAnimeClick: (String) -> Unit,
    onWatchlistClick: (ScheduleAnime) -> Unit,
    onExit: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val today = remember(timezone) { todayString(timezone) }
    val dates = remember(schedule, today) {
        val sorted = schedule.keys.sorted()
        sorted.filter { it >= today }.ifEmpty { sorted }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "legacy-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Schedule", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text("Classic release list", color = MUTED, fontSize = 13.sp)
                    }
                    to.kuudere.anisuge.platform.WindowManagementButtons(onClose = onExit)
                }
            }

            item(key = "legacy-filter") {
                ScheduleFilter(
                    myListOnly = myListOnly,
                    loading = isLoadingWatchlist,
                    onChange = onFilterChange,
                )
            }

            if (isRefreshing) {
                item(key = "legacy-refreshing") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Refreshing schedule…", color = MUTED, fontSize = 13.sp)
                    }
                }
            }

            dates.forEach { date ->
                val episodes = schedule[date].orEmpty()
                item(key = "legacy-date-$date") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            formatDateHeader(date, today),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${episodes.size} ${if (episodes.size == 1) "release" else "releases"}",
                            color = MUTED,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (episodes.isEmpty()) {
                    item(key = "legacy-empty-$date") { EmptyDayCard(myListOnly = myListOnly) }
                } else {
                    itemsIndexed(
                        episodes,
                        key = { index, anime -> "$date-${anime.activeSlug}-${anime.displayEpisodeNumber}-$index" },
                    ) { index, anime ->
                        LegacyScheduleRow(
                            anime = anime,
                            onClick = { onAnimeClick(anime.activeSlug) },
                            onWatchlistClick = { onWatchlistClick(anime) },
                        )
                    }
                }
            }

            if (dates.isEmpty()) {
                item(key = "legacy-empty-all") {
                    EmptyDayCard(myListOnly = myListOnly)
                }
            }
        }
    }
}

@Composable
private fun LegacyScheduleRow(
    anime: ScheduleAnime,
    onClick: () -> Unit,
    onWatchlistClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CARD)
            .border(1.dp, BORDER, RoundedCornerShape(18.dp))
            .tvFocusableClick(shape = RoundedCornerShape(18.dp), onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(82.dp)
                .height(112.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            anime.imageUrl.takeIf { it.isNotBlank() }?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = anime.resolveDisplayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                anime.resolveDisplayTitle(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip(icon = false, text = LocalAppStrings.current.episodeShort(anime.displayEpisodeNumber))
                MetaChip(icon = true, text = anime.time.ifBlank { anime.airingStatus.ifBlank { "TBA" } })
            }
            val format = (anime.type ?: anime.format).ifBlank { "TV" }
            if (format.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(format, color = DIM, fontSize = 12.sp, maxLines = 1)
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onWatchlistClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.BookmarkBorder, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ScheduleHeader(
    selectedDate: String,
    today: String,
    totalDays: Int,
    totalEpisodes: Int,
    timezone: String,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1A0D2A),
                        Color(0xFF080808),
                        Color.Black,
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(end = 64.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CalendarToday, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Release Schedule", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Track upcoming episodes by day", color = MUTED, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("$totalEpisodes", "Episodes")
                StatPill("$totalDays", "Days")
                StatPill(if (selectedDate == today) "Today" else "Upcoming", "Viewing")
                StatPill(timezone, "Timezone")
            }
        }

        to.kuudere.anisuge.platform.WindowManagementButtons(
            onClose = onExit,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = MUTED, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun DayChip(
    date: String,
    today: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            selected -> Color.White
            hovered -> Color.White.copy(alpha = 0.12f)
            else -> CARD
        },
        animationSpec = tween(180),
    )
    val border by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = if (hovered) 0.22f else 0.10f),
        animationSpec = tween(180),
    )
    val (label, day) = formatDayChip(date, today)

    Column(
        modifier = Modifier
            .widthIn(min = 82.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .hoverable(inter)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (selected) Color.Black else MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(
            day,
            color = if (selected) Color.Black else Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        Text("$count shows", color = if (selected) Color.Black.copy(alpha = 0.62f) else DIM, fontSize = 10.sp)
    }
}

@Composable
private fun ScheduleReleaseCard(
    anime: ScheduleAnime,
    onClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val strings = LocalAppStrings.current
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) CARD_HOVER else CARD, tween(220, easing = FastOutSlowInEasing))
    val scale by animateFloatAsState(if (hovered) 1.012f else 1f, tween(220, easing = FastOutSlowInEasing))

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = if (hovered) 0.22f else 0.10f), RoundedCornerShape(22.dp))
            .hoverable(inter)
            .tvFocusableClick(shape = RoundedCornerShape(22.dp), onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (compact) 16f / 8.8f else 16f / 7.4f)
                .background(Color.White.copy(alpha = 0.04f)),
        ) {
            val hero = anime.bannerUrl ?: anime.imageUrl
            if (!hero.isNullOrBlank()) {
                AsyncImage(
                    model = hero,
                    contentDescription = anime.resolveDisplayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.12f),
                        0.55f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    )
                )
            )

            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(strings.episodeShort(anime.displayEpisodeNumber), selected = true)
                anime.airType.takeIf { it.isNotBlank() }?.let { Pill(it.uppercase(), selected = false) }
            }

            if (anime.averageScore != null && anime.averageScore > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Star, null, tint = Color(0xFFFFD166), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${anime.averageScore}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .clickable(onClick = onWatchlistClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.BookmarkBorder, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Text(
                anime.resolveDisplayTitle(),
                color = Color.White,
                fontSize = if (compact) 18.sp else 21.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (compact) 22.sp else 25.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).padding(end = 46.dp),
            )
        }

        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaChip(icon = true, text = anime.time.ifBlank { anime.airingStatus.ifBlank { "TBA" } })
                MetaChip(icon = false, text = (anime.type ?: anime.format).ifBlank { "TV" })
                if (anime.duration.isNotBlank()) MetaChip(icon = false, text = anime.duration)
            }

            val desc = anime.description.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    desc,
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (anime.genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    anime.genres.take(3).forEach { genre ->
                        Text(
                            genre,
                            color = MUTED,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean) {
    Text(
        text,
        color = if (selected) Color.Black else Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun MetaChip(icon: Boolean, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Outlined.Schedule, null, tint = MUTED, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun EmptyScheduleError(onRetry: () -> Unit) {
    val strings = LocalAppStrings.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.CalendarToday, null, tint = MUTED, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(strings.failedToLoadSchedule, color = MUTED, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
            Text(strings.retry, color = Color.Black)
        }
    }
}

@Composable
private fun EmptyDayCard(myListOnly: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CARD)
            .border(1.dp, BORDER, RoundedCornerShape(24.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CalendarToday, null, tint = MUTED, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                if (myListOnly) "Nothing from My List airs today" else "No releases scheduled",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (myListOnly) "Switch to All to see every scheduled release." else "Pick another day from the timeline above.",
                color = MUTED,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ScheduleFilter(
    myListOnly: Boolean,
    loading: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false to "All", true to "My List").forEach { (value, label) ->
            Text(
                label,
                color = if (myListOnly == value) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (myListOnly == value) Color.White else Color.Transparent)
                    .clickable(enabled = !loading || !value) { onChange(value) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(horizontal = 8.dp).size(14.dp),
            )
        }
    }
}

@Composable
private fun ScrollToTopButton(onClick: () -> Unit) {
    val inter = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Color.White else Color.White.copy(alpha = 0.86f), tween(180))

    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .hoverable(inter)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.KeyboardArrowUp, null, tint = Color.Black, modifier = Modifier.size(24.dp))
    }
}
