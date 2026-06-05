package to.kuudere.anisuge.screens.watchlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.ui.AnimeCard
import to.kuudere.anisuge.ui.OfflineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onAnimeClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val showFullAnimeTitles by to.kuudere.anisuge.AppComponent.settingsStore.showFullAnimeTitlesFlow.collectAsState(
        initial = false
    )
    val selectedList = normalizeLibraryFolder(state.selectedFolder)
    var searchQuery by remember { mutableStateOf(state.searchQuery) }
    var expandedFilters by remember { mutableStateOf(false) }

    to.kuudere.anisuge.platform.PlatformBackHandler(enabled = expandedFilters || searchQuery.isNotEmpty() || selectedList != "ALL") {
        if (expandedFilters) {
            expandedFilters = false
        } else if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            viewModel.onSearchQueryChange("")
        } else if (selectedList != "ALL") {
            viewModel.onFolderChange("All")
        }
    }

    LaunchedEffect(searchQuery) {
        if (state.searchQuery != searchQuery) {
            viewModel.onSearchQueryChange(searchQuery)
        }
    }

    val currentList = state.items.filter { it.folder == "WATCHING" || it.folder == "Current" }
    val onHoldList = state.items.filter { it.folder == "PAUSED" || it.folder == "On Hold" }
    val planningList = state.items.filter { it.folder == "PLANNING" || it.folder == "Plan To Watch" }
    val droppedList = state.items.filter { it.folder == "DROPPED" }
    val completedList = state.items.filter { it.folder == "COMPLETED" }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isDesktop = maxWidth >= 800.dp
        val isSmall = maxWidth < 600.dp
        val screenHeight = maxHeight

        Column(
            Modifier
                .fillMaxSize()
                .background(AppColors.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val searchOptionsBlock = @Composable { modifier: Modifier ->
                Column(
                    modifier = modifier
                ) {
                    if (isDesktop) {
                        // Desktop layout: search bar and clear icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search field
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .weight(1f)
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                    .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = AppColors.text,
                                            fontSize = 14.sp
                                        ),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.text),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text("Search list", color = Color.Gray, fontSize = 14.sp)
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            // Trash icon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    expandedFilters = false
                                    viewModel.resetAllFilters()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Second row: Advanced filters grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AdvancedFilterDropdown(
                                label = "Genre",
                                value = state.selectedGenres.joinToString(", ").ifBlank { null },
                                hint = "Any genre",
                                options = listOf(
                                    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
                                    "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports",
                                    "Supernatural", "Thriller", "Ecchi", "Harem", "Isekai", "Mecha",
                                    "Music", "Psychological", "School", "Military", "Historical",
                                    "Demons", "Magic", "Vampire", "Hentai"
                                ),
                                onOptionSelected = { viewModel.onGenreToggle(it) },
                                icon = Icons.Default.Style,
                                modifier = Modifier.weight(1f),
                                multiSelect = true,
                                onClear = { viewModel.clearGenres() }
                            )
                            AdvancedFilterDropdown(
                                "Sorting",
                                if (state.sort == "Recently Updated") null else state.sort,
                                "Recently Updated",
                                listOf("Recently Updated", "Score", "Popularity", "Year", "Episodes"),
                                { viewModel.updateFilters(newSort = it) },
                                Icons.AutoMirrored.Filled.Sort,
                                Modifier.weight(1f),
                                onClear = { viewModel.updateFilters(newSort = "Recently Updated") }
                            )
                            AdvancedFilterDropdown(
                                "Format",
                                if (state.format == "All formats") null else state.format,
                                "All formats",
                                listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC"),
                                { viewModel.updateFilters(newFormat = it) },
                                Icons.Default.Tv,
                                Modifier.weight(1f),
                                onClear = { viewModel.updateFilters(newFormat = "All formats") }
                            )
                            AdvancedFilterDropdown(
                                "Status",
                                if (state.status == "All statuses") null else state.status,
                                "All statuses",
                                listOf("FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS"),
                                { viewModel.updateFilters(newStatus = it) },
                                Icons.Default.SignalCellularAlt,
                                Modifier.weight(1f),
                                onClear = { viewModel.updateFilters(newStatus = "All statuses") }
                            )
                        }
                    } else {
                        // Mobile Layout
                        // First row: search, clear, expand button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search field
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .weight(1f)
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                    .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = AppColors.text,
                                            fontSize = 14.sp
                                        ),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.text),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text("Search list", color = Color.Gray, fontSize = 14.sp)
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            // Trash icon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    expandedFilters = false
                                    viewModel.resetAllFilters()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }

                            // Expand Icon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
                                    .background(Color.Transparent)
                                    .clickable { expandedFilters = !expandedFilters }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (expandedFilters) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = Color.Gray
                                )
                            }
                        }

                        AnimatedVisibility(expandedFilters) {
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                // Advanced filters: 2 per row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AdvancedFilterDropdown(
                                        label = "Genre",
                                        value = state.selectedGenres.joinToString(", ").ifBlank { null },
                                        hint = "Any genre",
                                        options = listOf(
                                            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
                                            "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports",
                                            "Supernatural", "Thriller", "Ecchi", "Harem", "Isekai", "Mecha",
                                            "Music", "Psychological", "School", "Military", "Historical",
                                            "Demons", "Magic", "Vampire", "Hentai"
                                        ),
                                        onOptionSelected = { viewModel.onGenreToggle(it) },
                                        icon = Icons.Default.Style,
                                        modifier = Modifier.weight(1f),
                                        multiSelect = true,
                                        onClear = { viewModel.clearGenres() }
                                    )
                                    AdvancedFilterDropdown(
                                        "Sorting",
                                        if (state.sort == "Recently Updated") null else state.sort,
                                        "Recently Updated",
                                        listOf("Recently Updated", "Score", "Popularity", "Year", "Episodes"),
                                        { viewModel.updateFilters(newSort = it) },
                                        Icons.AutoMirrored.Filled.Sort,
                                        Modifier.weight(1f),
                                        onClear = { viewModel.updateFilters(newSort = "Recently Updated") }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AdvancedFilterDropdown(
                                        "Format",
                                        if (state.format == "All formats") null else state.format,
                                        "All formats",
                                        listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC"),
                                        { viewModel.updateFilters(newFormat = it) },
                                        Icons.Default.Tv,
                                        Modifier.weight(1f),
                                        onClear = { viewModel.updateFilters(newFormat = "All formats") }
                                    )
                                    AdvancedFilterDropdown(
                                        "Status",
                                        if (state.status == "All statuses") null else state.status,
                                        "All statuses",
                                        listOf("FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS"),
                                        { viewModel.updateFilters(newStatus = it) },
                                        Icons.Default.SignalCellularAlt,
                                        Modifier.weight(1f),
                                        onClear = { viewModel.updateFilters(newStatus = "All statuses") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val showOffline = state.isOffline && state.items.isEmpty()

            LibraryFolderTabs(
                selectedKey = selectedList,
                isDesktop = isDesktop,
                onFolderSelected = { folder ->
                    expandedFilters = false
                    viewModel.onFolderChange(if (folder == "ALL") "All" else folder)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isDesktop) 18.dp else 8.dp, bottom = 8.dp),
            )

            if (showOffline) {
                OfflineState(onRetry = { viewModel.onFolderChange(state.selectedFolder) }, isLoading = state.isLoading)
            } else {
                if (isDesktop) {
                    searchOptionsBlock(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                // Lists content
                val gridColumns = if (isSmall) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 160.dp)
                val listState = rememberLazyGridState()

                val endReached by remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                        lastVisibleItem?.index != 0 && lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
                    }
                }

                LaunchedEffect(endReached) {
                    if (endReached) {
                        viewModel.loadMore()
                    }
                }

                LazyVerticalGrid(
                    columns = gridColumns,
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = if (isDesktop) 24.dp else 156.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!isDesktop) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            searchOptionsBlock(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                        }
                    }

                    if (state.isLoading && state.items.isEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                                CircularProgressIndicator(color = AppColors.accent, strokeWidth = 3.dp)
                            }
                        }
                    } else {
                        val showAll = selectedList == "ALL"
                        var hasAnyItems = false

                        if (showAll) {
                            items(state.items) {
                                AnimeCard(
                                    item = it,
                                    badgeText = it.folder,
                                    showFullTitle = showFullAnimeTitles,
                                    onClick = { onAnimeClick(it.activeSlug) })
                            }
                            if (state.items.isNotEmpty()) hasAnyItems = true
                        } else {
                            if (selectedList == "WATCHING" && currentList.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Watching", currentList.size)
                                }
                                items(currentList) {
                                    AnimeCard(
                                        item = it,
                                        showFullTitle = showFullAnimeTitles,
                                        onClick = { onAnimeClick(it.activeSlug) },
                                    )
                                }
                                if (currentList.isNotEmpty()) hasAnyItems = true
                            }

                            if (selectedList == "PAUSED" && onHoldList.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("On Hold", onHoldList.size)
                                }
                                items(onHoldList) {
                                    AnimeCard(
                                        item = it,
                                        showFullTitle = showFullAnimeTitles,
                                        onClick = { onAnimeClick(it.activeSlug) },
                                    )
                                }
                                if (onHoldList.isNotEmpty()) hasAnyItems = true
                            }

                            if (selectedList == "PLANNING" && planningList.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Plan To Watch", planningList.size)
                                }
                                items(planningList) {
                                    AnimeCard(
                                        item = it,
                                        showFullTitle = showFullAnimeTitles,
                                        onClick = { onAnimeClick(it.activeSlug) },
                                    )
                                }
                                if (planningList.isNotEmpty()) hasAnyItems = true
                            }

                            if (selectedList == "DROPPED" && droppedList.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Dropped", droppedList.size)
                                }
                                items(droppedList) {
                                    AnimeCard(
                                        item = it,
                                        showFullTitle = showFullAnimeTitles,
                                        onClick = { onAnimeClick(it.activeSlug) },
                                    )
                                }
                                if (droppedList.isNotEmpty()) hasAnyItems = true
                            }

                            if (selectedList == "COMPLETED" && completedList.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Completed", completedList.size)
                                }
                                items(completedList) {
                                    AnimeCard(
                                        item = it,
                                        showFullTitle = showFullAnimeTitles,
                                        onClick = { onAnimeClick(it.activeSlug) },
                                    )
                                }
                                if (completedList.isNotEmpty()) hasAnyItems = true
                            }
                        }

                        if (state.isPaginating || (state.isLoading && state.items.isNotEmpty())) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = AppColors.accent, strokeWidth = 3.dp)
                                }
                            }
                        } else if (!hasAnyItems && !state.isLoading) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Inventory2,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.25f),
                                            modifier = Modifier.size(56.dp),
                                        )
                                        Text(
                                            text = "Nothing here",
                                            color = AppColors.text,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "This list is currently empty.",
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } // else (not offline)
        }
    }
}

private data class LibraryFolderTab(
    val key: String,
    val label: String,
)

private val libraryFolderTabs = listOf(
    LibraryFolderTab("ALL", "All"),
    LibraryFolderTab("WATCHING", "Watching"),
    LibraryFolderTab("PLANNING", "Plan to Watch"),
    LibraryFolderTab("COMPLETED", "Completed"),
    LibraryFolderTab("PAUSED", "On Hold"),
    LibraryFolderTab("DROPPED", "Dropped"),
)

private fun normalizeLibraryFolder(folder: String): String =
    when (folder.trim().uppercase().replace('-', '_').replace(' ', '_')) {
        "", "ALL", "ALL_LISTS" -> "ALL"
        "CURRENT" -> "WATCHING"
        "PLAN_TO_WATCH", "PLAN_TO_WATCHING" -> "PLANNING"
        "ON_HOLD" -> "PAUSED"
        else -> folder.trim().uppercase()
    }

@Composable
private fun LibraryFolderTabs(
    selectedKey: String,
    isDesktop: Boolean,
    onFolderSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(
                    if (isDesktop) {
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    } else {
                        Modifier.horizontalScroll(scrollState).padding(horizontal = 24.dp)
                    }
                )
                .height(48.dp),
            horizontalArrangement = if (isDesktop) Arrangement.SpaceBetween else Arrangement.spacedBy(30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            libraryFolderTabs.forEach { tab ->
                val selected = selectedKey == tab.key
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onFolderSelected(tab.key) }
                        .padding(horizontal = 2.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = tab.label,
                        color = if (selected) AppColors.text else AppColors.textMuted,
                        fontSize = if (isDesktop) 15.sp else 16.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .width(if (selected) 30.dp else 1.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) AppColors.accent else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = AppColors.border.copy(alpha = 0.75f), thickness = 1.dp)
    }
}

@Composable
fun AdvancedFilterDropdown(
    label: String,
    value: String?,
    hint: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var triggerWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val triggerWidthDp = with(density) { triggerWidthPx.toDp() }
    val selectedItems = value?.split(", ")?.filter { it.isNotBlank() } ?: emptyList()

    Box(
        modifier = modifier
            .height(40.dp)
            .onSizeChanged { triggerWidthPx = it.width }
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = value ?: hint,
                color = if (value == null) Color.Gray else AppColors.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (value != null && onClear != null) {
                IconButton(
                    onClick = {
                        onClear()
                        expanded = false // Close if open
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close, "Clear",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(triggerWidthDp.coerceAtLeast(160.dp))
                .heightIn(max = 280.dp),
            offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp),
            containerColor = AppColors.surface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.border),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
        ) {
            val fullOptions = if (!multiSelect && !options.contains(hint) && hint.isNotEmpty()) {
                listOf(hint) + options
            } else {
                options
            }

            fullOptions.forEach { option ->
                val isHintOption = option == hint && !options.contains(hint)
                val isSelected =
                    if (multiSelect) selectedItems.contains(option) else (option == value || (value.isNullOrBlank() && (isHintOption || option == hint)))
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isHovered || (!multiSelect && isSelected)) AppColors.accent else if (isSelected) AppColors.text else AppColors.text,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected || isHovered) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        if (isHintOption) {
                            onOptionSelected(hint)
                        } else {
                            onOptionSelected(option)
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
                                        if (isSelected) AppColors.accent
                                        else AppColors.border
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = AppColors.text,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        } else if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = AppColors.text,
                        disabledTextColor = Color.Gray,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text(count.toString(), color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
