package to.kuudere.anisuge.screens.watchlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import to.kuudere.anisuge.ui.AnimeCard
import to.kuudere.anisuge.ui.OfflineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onAnimeClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf("Anime") }
    var selectedList by remember { mutableStateOf("All lists") }
    var searchQuery by remember { mutableStateOf(state.searchQuery) }
    var expandedFilters by remember { mutableStateOf(false) }

    to.kuudere.anisuge.platform.PlatformBackHandler(enabled = expandedFilters || searchQuery.isNotEmpty() || selectedList != "All lists") {
        if (expandedFilters) {
            expandedFilters = false
        } else if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            viewModel.onSearchQueryChange("")
        } else if (selectedList != "All lists") {
            selectedList = "All lists"
            viewModel.onFolderChange("All")
        }
    }

    LaunchedEffect(searchQuery) {
        if (state.searchQuery != searchQuery) {
            viewModel.onSearchQueryChange(searchQuery)
        }
    }

    val currentList = state.items.filter { it.displayFolder == "Watching" }
    val onHoldList = state.items.filter { it.displayFolder == "On Hold" }
    val planningList = state.items.filter { it.displayFolder == "Plan To Watch" }
    val droppedList = state.items.filter { it.displayFolder == "Dropped" }
    val completedList = state.items.filter { it.displayFolder == "Completed" }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isDesktop = maxWidth >= 800.dp
        val isSmall = maxWidth < 600.dp
        val screenHeight = maxHeight

        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF000000)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val searchOptionsBlock = @Composable { modifier: Modifier ->
                var showDesktopDropdown by remember { mutableStateOf(false) }
                var showMobileDropdown by remember { mutableStateOf(false) }
                val folderOptions = listOf("All lists", "Watching", "On Hold", "Plan To Watch", "Dropped", "Completed")
                val density = LocalDensity.current

                Column(
                    modifier = modifier
                ) {
                    if (isDesktop) {
                        // Desktop layout: list selector, search bar, trash icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dropdown for list selector
                            var showFolderDropdown by remember { mutableStateOf(false) }
                            var folderTriggerWidthPx by remember { mutableStateOf(0) }
                            val folderTriggerWidthDp = with(density) { folderTriggerWidthPx.toDp() }
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .weight(0.3f)
                                    .onSizeChanged { folderTriggerWidthPx = it.width }
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .clickable { showFolderDropdown = true },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedList, color = Color.White, fontSize = 14.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                                }

                                DropdownMenu(
                                    expanded = showFolderDropdown,
                                    onDismissRequest = { showFolderDropdown = false },
                                    modifier = Modifier
                                        .width(folderTriggerWidthDp)
                                        .background(Color(0xFF000000))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                    offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)
                                ) {
                                    folderOptions.forEach { folder ->
                                        val isSelected = selectedList == folder
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isHovered by interactionSource.collectIsHoveredAsState()

                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    text = folder, 
                                                    color = if (isHovered || isSelected) Color(0xFFBF80FF) else Color(0xFFD4D4D8),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected || isHovered) FontWeight.SemiBold else FontWeight.Normal
                                                ) 
                                            },
                                            onClick = {
                                                selectedList = folder
                                                viewModel.onFolderChange(if (folder == "All lists") "All" else folder)
                                                showFolderDropdown = false
                                            },
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            interactionSource = interactionSource,
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.CheckCircle, null,
                                                        tint = Color(0xFFBF80FF),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            // Search field
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .weight(0.6f)
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
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
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    selectedList = "All lists"
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
                                "Sorting", if (state.sort == "Recently Updated") null else state.sort, "Recently Updated", listOf("Recently Updated", "Score", "Popularity", "Year", "Episodes"),
                                { viewModel.updateFilters(newSort = it) }, Icons.Default.Sort, Modifier.weight(1f),
                                onClear = { viewModel.updateFilters(newSort = "Recently Updated") }
                            )
                            AdvancedFilterDropdown(
                                "Format", if (state.format == "All formats") null else state.format, "All formats", listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC"),
                                { viewModel.updateFilters(newFormat = it) }, Icons.Default.Tv, Modifier.weight(1f),
                                onClear = { viewModel.updateFilters(newFormat = "All formats") }
                            )
                            AdvancedFilterDropdown(
                                "Status", if (state.status == "All statuses") null else state.status, "All statuses", listOf("FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS"),
                                { viewModel.updateFilters(newStatus = it) }, Icons.Default.SignalCellularAlt, Modifier.weight(1f),
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
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
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
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    selectedList = "All lists"
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
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .background(Color.Transparent)
                                    .clickable { expandedFilters = !expandedFilters }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (expandedFilters) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand", tint = Color.Gray)
                            }
                        }

                        AnimatedVisibility(expandedFilters) {
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                // Next row: list selector
                                var showFolderDropdownMobile by remember { mutableStateOf(false) }
                                var folderTriggerWidthPxMobile by remember { mutableStateOf(0) }
                                val folderTriggerWidthDpMobile = with(density) { folderTriggerWidthPxMobile.toDp() }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .onSizeChanged { folderTriggerWidthPxMobile = it.width }
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable { showFolderDropdownMobile = true },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedList, color = Color.White, fontSize = 14.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                                    }

                                    DropdownMenu(
                                        expanded = showFolderDropdownMobile,
                                        onDismissRequest = { showFolderDropdownMobile = false },
                                        modifier = Modifier
                                            .width(folderTriggerWidthDpMobile)
                                            .background(Color(0xFF000000))
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)
                                    ) {
                                        folderOptions.forEach { folder ->
                                            val isSelected = selectedList == folder
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isHovered by interactionSource.collectIsHoveredAsState()

                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = folder, 
                                                        color = if (isHovered || isSelected) Color(0xFFBF80FF) else Color(0xFFD4D4D8),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected || isHovered) FontWeight.SemiBold else FontWeight.Normal
                                                    ) 
                                                },
                                                onClick = {
                                                    selectedList = folder
                                                    viewModel.onFolderChange(if (folder == "All lists") "All" else folder)
                                                    showFolderDropdownMobile = false
                                                },
                                                modifier = Modifier
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                interactionSource = interactionSource,
                                                trailingIcon = {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.CheckCircle, null,
                                                            tint = Color(0xFFBF80FF),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(8.dp))

                                // Next 2 rows: 2 advanced filters per row
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
                                        "Sorting", if (state.sort == "Recently Updated") null else state.sort, "Recently Updated", listOf("Recently Updated", "Score", "Popularity", "Year", "Episodes"),
                                        { viewModel.updateFilters(newSort = it) }, Icons.Default.Sort, Modifier.weight(1f),
                                        onClear = { viewModel.updateFilters(newSort = "Recently Updated") }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AdvancedFilterDropdown(
                                        "Format", if (state.format == "All formats") null else state.format, "All formats", listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC"),
                                        { viewModel.updateFilters(newFormat = it) }, Icons.Default.Tv, Modifier.weight(1f),
                                        onClear = { viewModel.updateFilters(newFormat = "All formats") }
                                    )
                                    AdvancedFilterDropdown(
                                        "Status", if (state.status == "All statuses") null else state.status, "All statuses", listOf("FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS"),
                                        { viewModel.updateFilters(newStatus = it) }, Icons.Default.SignalCellularAlt, Modifier.weight(1f),
                                        onClear = { viewModel.updateFilters(newStatus = "All statuses") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val showOffline = state.isOffline && state.items.isEmpty()

            if (showOffline) {
                OfflineState(onRetry = { viewModel.onFolderChange(state.selectedFolder) }, isLoading = state.isLoading)
            } else {
                if (isDesktop) {
                    searchOptionsBlock(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp))
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
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = if (isDesktop) 24.dp else 156.dp),
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
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            }
                        }
                    } else {
                    val showAll = selectedList == "All lists"
                    var hasAnyItems = false

                    if (showAll) {
                        items(state.items) { AnimeCard(item = it, badgeText = it.displayFolder, onClick = { onAnimeClick(it.activeId) }) }
                        if (state.items.isNotEmpty()) hasAnyItems = true
                    } else {
                        if (selectedList == "Watching" && currentList.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionHeader("Watching", currentList.size)
                            }
                            items(currentList) { AnimeCard(item = it, onClick = { onAnimeClick(it.activeId) }) }
                            if (currentList.isNotEmpty()) hasAnyItems = true
                        }
                        
                        if (selectedList == "On Hold" && onHoldList.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionHeader("On Hold", onHoldList.size)
                            }
                            items(onHoldList) { AnimeCard(item = it, onClick = { onAnimeClick(it.activeId) }) }
                            if (onHoldList.isNotEmpty()) hasAnyItems = true
                        }

                        if (selectedList == "Plan To Watch" && planningList.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionHeader("Plan To Watch", planningList.size)
                            }
                            items(planningList) { AnimeCard(item = it, onClick = { onAnimeClick(it.activeId) }) }
                            if (planningList.isNotEmpty()) hasAnyItems = true
                        }
                        
                        if (selectedList == "Dropped" && droppedList.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionHeader("Dropped", droppedList.size)
                            }
                            items(droppedList) { AnimeCard(item = it, onClick = { onAnimeClick(it.activeId) }) }
                            if (droppedList.isNotEmpty()) hasAnyItems = true
                        }

                        if (selectedList == "Completed" && completedList.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                SectionHeader("Completed", completedList.size)
                            }
                            items(completedList) { AnimeCard(item = it, onClick = { onAnimeClick(it.activeId) }) }
                            if (completedList.isNotEmpty()) hasAnyItems = true
                        }
                    }

                    if (state.isPaginating || (state.isLoading && state.items.isNotEmpty())) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
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
                                        color = Color.White,
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
            .background(Color(0xFF000000))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
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
                color = if (value == null) Color.Gray else Color.White, 
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
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(triggerWidthDp.coerceAtLeast(160.dp))
                .heightIn(max = 280.dp),
            offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp),
            containerColor = Color(0xFF000000),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
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
                val isSelected = if (multiSelect) selectedItems.contains(option) else (option == value || (value.isNullOrBlank() && (isHintOption || option == hint)))
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                DropdownMenuItem(
                    text = { 
                        Text(
                            text = option, 
                            color = if (isHovered || (!multiSelect && isSelected)) Color(0xFFBF80FF) else if (isSelected) Color.White else Color(0xFFD4D4D8),
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

@Composable
fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text(count.toString(), color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
