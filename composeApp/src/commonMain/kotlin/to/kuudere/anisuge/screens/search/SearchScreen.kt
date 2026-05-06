package to.kuudere.anisuge.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.ui.OfflineState
import to.kuudere.anisuge.i18n.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onAnimeClick: (String) -> Unit,
    onBack: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current
    val scrollState = rememberLazyGridState()

    // Infinite scroll listener
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo }.collect { layoutInfo ->
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 5) {
                viewModel.search(loadMore = true)
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF000000)
    ) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isSmall = maxWidth < 800.dp
            val columns = if (isSmall) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 160.dp)
            val hPadding = if (isSmall) 12.dp else 24.dp
            val itemSpacing = if (isSmall) 12.dp else 16.dp
            val showOffline = state.isOffline && state.results.isEmpty()

            if (showOffline) {
                OfflineState(onRetry = { viewModel.search() }, isLoading = state.isLoading)
            } else {
            LazyVerticalGrid(
                columns = columns,
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = hPadding, end = hPadding, top = 8.dp, bottom = if (isSmall) 156.dp else 100.dp),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                   Box(Modifier.fillMaxWidth()) {
                        to.kuudere.anisuge.platform.WindowManagementButtons(
                            onClose = onExit,
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp)
                        )
                   }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterSection(state, viewModel)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = strings.resultsCount(state.results.size),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                if (state.isLoading && state.results.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                } else if (!state.isLoading && state.results.isEmpty()) {
                    // No results found state
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(56.dp),
                                )
                                Text(
                                    text = strings.noResultsFound,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = strings.tryAdjustingFilters,
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        }
                    }
                } else {
                    items(state.results) { anime ->
                        to.kuudere.anisuge.ui.AnimeCard(
                            item     = anime,
                            modifier = Modifier.fillMaxWidth(),
                            onClick  = { onAnimeClick(anime.activeSlug) }
                        )
                    }
                }

                if (state.isLoadingMore && !state.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
            } // else (not offline)
        }
    }
}

@Composable
private fun FilterSection(state: SearchUiState, viewModel: SearchViewModel) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 800.dp) {
            SmallScreenFilterSection(state, viewModel)
        } else {
            LargeScreenFilterSection(state, viewModel)
        }
    }
}

// ─── Shared filter data (1:1 with Kuudere API) ──────────────────────────────
val KUUDERE_GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
    "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports",
    "Supernatural", "Thriller", "Ecchi", "Harem", "Isekai", "Mecha",
    "Music", "Psychological", "School", "Military", "Historical",
    "Demons", "Magic", "Vampire", "Hentai"
)
private val KUUDERE_SORTS    = listOf("Popularity", "Latest", "Score", "Year", "Episodes")
private val KUUDERE_SEASONS  = listOf("Winter", "Spring", "Summer", "Fall")
private val KUUDERE_YEARS    = (2025 downTo 1975).map { it.toString() }
private val KUUDERE_STATUSES = listOf("Finished", "Releasing", "Not Yet Released", "Cancelled")
private val KUUDERE_FORMATS  = listOf("TV", "TV Short", "Movie", "Special", "OVA", "ONA", "Music")
private val KUUDERE_ORIGINS  = listOf("Japan", "South Korea", "China", "Taiwan")

// ─── Large screen layout ─────────────────────────────────────────────────────
@Composable
private fun LargeScreenFilterSection(state: SearchUiState, viewModel: SearchViewModel) {
    val strings = LocalAppStrings.current
    Column(Modifier.fillMaxWidth()) {
        Text(strings.search, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // Row 1: Search | Genres | Sort by | Reset
        Row(
            Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KSearchInput(
                state.keyword, 
                viewModel::onKeywordChange, 
                onSearch = { viewModel.search() },
                modifier = Modifier.weight(1.5f)
            )

            KAdvancedFilterDropdown(
                label = strings.genres,
                hint = strings.anyGenre,
                selected = state.selectedGenres.joinToString(", ").ifBlank { null },
                items = KUUDERE_GENRES,
                icon = Icons.Default.Style,
                modifier = Modifier.weight(1f),
                multiSelect = true
            ) {
                if (it != null) viewModel.onGenreToggle(it) else viewModel.clearGenres()
            }

            KAdvancedFilterDropdown(
                label = strings.sortBy,
                hint = strings.popularity,
                selected = state.selectedSort,
                items = KUUDERE_SORTS,
                icon = Icons.Default.Sort,
                modifier = Modifier.weight(1f)
            ) { 
                viewModel.onSortChange(it) 
            }

            // Reset Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .background(Color.Transparent)
                    .clickable { viewModel.clearFilters() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = strings.clearLabel, tint = Color.Gray)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Row 2: Year | Status | Format | Season | Origin
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KAdvancedFilterDropdown(
                strings.year, strings.anyYear, state.selectedYear, KUUDERE_YEARS, 
                Icons.Default.Event, Modifier.weight(1f)
            ) { viewModel.onYearChange(it) }
            
            KAdvancedFilterDropdown(
                strings.status, strings.anyStatus, state.selectedStatus, KUUDERE_STATUSES, 
                Icons.Default.SignalCellularAlt, Modifier.weight(1f)
            ) { viewModel.onStatusChange(it) }
            
            KAdvancedFilterDropdown(
                strings.format, strings.anyFormat, state.selectedType, KUUDERE_FORMATS, 
                Icons.Default.Tv, Modifier.weight(1f)
            ) { viewModel.onTypeChange(it) }
            
            KAdvancedFilterDropdown(
                strings.season, strings.anySeason, state.selectedSeason, KUUDERE_SEASONS, 
                Icons.Default.WbSunny, Modifier.weight(1f)
            ) { viewModel.onSeasonChange(it) }
            
            KAdvancedFilterDropdown(
                strings.origin, strings.anyOrigin, null, KUUDERE_ORIGINS, 
                Icons.Default.Public, Modifier.weight(1f)
            ) { /* language filter removed */ }
        }
    }
}

// ─── Small screen layout ─────────────────────────────────────────────────────
@Composable
private fun SmallScreenFilterSection(state: SearchUiState, viewModel: SearchViewModel) {
    val strings = LocalAppStrings.current
    var isExpanded by remember { mutableStateOf(false) }

    to.kuudere.anisuge.platform.PlatformBackHandler(enabled = isExpanded || state.keyword.isNotEmpty()) {
        if (isExpanded) {
            isExpanded = false
        } else if (state.keyword.isNotEmpty()) {
            viewModel.onKeywordChange("")
            viewModel.search()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(strings.search, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KSearchInput(state.keyword, viewModel::onKeywordChange, onSearch = { viewModel.search() }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF000000))
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = strings.filters,
                    tint = if (isExpanded) Color.White else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KAdvancedFilterDropdown(strings.genres, strings.anyGenre, state.selectedGenres.joinToString(", ").ifBlank { null }, KUUDERE_GENRES, Icons.Default.Style, Modifier.weight(1f), multiSelect = true) {
                        if (it != null) viewModel.onGenreToggle(it) else viewModel.clearGenres()
                    }
                    KAdvancedFilterDropdown(strings.sortBy, strings.popularity, state.selectedSort, KUUDERE_SORTS, Icons.Default.Sort, Modifier.weight(1f)) { viewModel.onSortChange(it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KAdvancedFilterDropdown(strings.season, strings.anySeason, state.selectedSeason, KUUDERE_SEASONS, Icons.Default.WbSunny, Modifier.weight(1f)) { viewModel.onSeasonChange(it) }
                    KAdvancedFilterDropdown(strings.year, strings.anyYear, state.selectedYear, KUUDERE_YEARS, Icons.Default.Event, Modifier.weight(1f)) { viewModel.onYearChange(it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KAdvancedFilterDropdown(strings.status, strings.anyStatus, state.selectedStatus, KUUDERE_STATUSES, Icons.Default.SignalCellularAlt, Modifier.weight(1f)) { viewModel.onStatusChange(it) }
                    KAdvancedFilterDropdown(strings.format, strings.anyFormat, state.selectedType, KUUDERE_FORMATS, Icons.Default.Tv, Modifier.weight(1f)) { viewModel.onTypeChange(it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KAdvancedFilterDropdown(strings.origin, strings.anyOrigin, null, KUUDERE_ORIGINS, Icons.Default.Public, Modifier.weight(1f)) { /* language filter removed */ }
                    
                    Button(
                        onClick = { viewModel.clearFilters() },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(strings.resetFilters, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─── Shared UI primitives ────────────────────────────────────────────────────

@Composable
private fun KSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Box(
        modifier
            .height(44.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF000000))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Color(0xFF71717A), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(strings.searchPlaceholder, color = Color(0xFF71717A), fontSize = 14.sp)
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                )
            }
        }
    }
}


@Composable
private fun KAdvancedFilterDropdown(
    label: String,
    hint: String,
    selected: String?,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    onItemSelected: (String?) -> Unit
) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    var triggerWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val triggerWidthDp: Dp = with(density) { triggerWidthPx.toDp() }
    val selectedItems = selected?.split(", ")?.filter { it.isNotBlank() } ?: emptyList()

    Box(modifier = modifier) {
        // ── Trigger ────────────────────────────────────────────────────
        Box(
            Modifier
                .height(44.dp)
                .fillMaxWidth()
                .onSizeChanged { triggerWidthPx = it.width }
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF000000))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = selected ?: hint,
                    color = if (selected == null) Color(0xFF71717A) else Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selected != null) {
                    IconButton(
                        onClick = { 
                            onItemSelected(null)
                            expanded = false
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close, strings.clearLabel,
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = Color(0xFF71717A),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(triggerWidthDp.coerceAtLeast(160.dp))
                .heightIn(max = 280.dp),
            offset = DpOffset(0.dp, 6.dp),
            containerColor = Color(0xFF000000),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
        ) {
            val fullItems = if (!multiSelect && !items.contains(hint) && hint.isNotEmpty()) {
                listOf(hint) + items
            } else {
                items
            }

            fullItems.forEach { item ->
                val isHintItem = item == hint && !items.contains(hint)
                val isSelected = if (multiSelect) selectedItems.contains(item) else (item == selected || (selected.isNullOrBlank() && (isHintItem || item == hint)))
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                DropdownMenuItem(
                    text = {
                        Text(
                            item,
                            color = if (isHovered || (!multiSelect && isSelected)) Color(0xFFBF80FF) else if (isSelected) Color.White else Color(0xFFD4D4D8),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected || isHovered) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        if (isHintItem) {
                            onItemSelected(null)
                        } else {
                            onItemSelected(item)
                        }
                        if (!multiSelect) expanded = false
                    },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    interactionSource = interactionSource,
                    trailingIcon = {
                        if (multiSelect) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) Color(0xFFBF80FF)
                                        else Color.White.copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        } else if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = Color(0xFFBF80FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = Color.White,
                        disabledTextColor = Color.Gray,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

