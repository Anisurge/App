package to.kuudere.anisuge.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.i18n.resolveDisplayTitle
import to.kuudere.anisuge.screens.home.HomeUiState
import to.kuudere.anisuge.screens.home.HomeViewModel
import to.kuudere.anisuge.screens.search.SearchViewModel
import to.kuudere.anisuge.screens.watchlist.WatchlistViewModel
import to.kuudere.anisuge.ui.AnimeCard
import to.kuudere.anisuge.ui.tvFocusableClick

private enum class TvTab {
    Home,
    Search,
    Watchlist,
}

@Composable
fun TvAppShell(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    watchlistViewModel: WatchlistViewModel,
    onAnimeClick: (String) -> Unit,
    onWatchClick: (String, String, Int, String?, Double?) -> Unit,
    onLogout: () -> Unit,
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()
    val watchlistState by watchlistViewModel.uiState.collectAsState()

    var currentTab by remember { mutableStateOf(TvTab.Home) }
    var query by remember { mutableStateOf(searchState.keyword) }

    val homeRailFocus = remember { FocusRequester() }
    val searchRailFocus = remember { FocusRequester() }
    val watchlistRailFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        homeRailFocus.requestFocus()
        homeViewModel.refresh(force = true)
        homeViewModel.refreshContinueWatching()
        watchlistViewModel.refresh()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        TvRail(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            onLogout = onLogout,
            homeFocus = homeRailFocus,
            searchFocus = searchRailFocus,
            watchlistFocus = watchlistRailFocus,
            contentFocus = contentFocus,
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.08f)),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            when (currentTab) {
                TvTab.Home -> TvHomeTab(
                    state = homeState,
                    contentFocus = contentFocus,
                    railFocus = homeRailFocus,
                    onAnimeClick = onAnimeClick,
                    onWatchClick = onWatchClick,
                )

                TvTab.Search -> TvSearchTab(
                    query = query,
                    onQueryChange = { value ->
                        query = value
                        searchViewModel.onKeywordChange(value)
                    },
                    state = searchState,
                    contentFocus = contentFocus,
                    railFocus = searchRailFocus,
                    onSearch = { searchViewModel.search() },
                    onAnimeClick = onAnimeClick,
                )

                TvTab.Watchlist -> TvWatchlistTab(
                    state = watchlistState,
                    contentFocus = contentFocus,
                    railFocus = watchlistRailFocus,
                    onFolderSelected = { watchlistViewModel.onFolderChange(it) },
                    onAnimeClick = onAnimeClick,
                )
            }
        }
    }
}

@Composable
private fun TvRail(
    currentTab: TvTab,
    onTabSelected: (TvTab) -> Unit,
    onLogout: () -> Unit,
    homeFocus: FocusRequester,
    searchFocus: FocusRequester,
    watchlistFocus: FocusRequester,
    contentFocus: FocusRequester,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .fillMaxHeight()
            .background(Color(0xFF050505))
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "AniSuge",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            TvRailButton(
                label = "Home",
                icon = Icons.Outlined.Home,
                selected = currentTab == TvTab.Home,
                modifier = Modifier
                    .focusRequester(homeFocus)
                    .focusProperties {
                        down = searchFocus
                        right = contentFocus
                    },
                onClick = { onTabSelected(TvTab.Home) },
            )

            TvRailButton(
                label = "Search",
                icon = Icons.Default.Search,
                selected = currentTab == TvTab.Search,
                modifier = Modifier
                    .focusRequester(searchFocus)
                    .focusProperties {
                        up = homeFocus
                        down = watchlistFocus
                        right = contentFocus
                    },
                onClick = { onTabSelected(TvTab.Search) },
            )

            TvRailButton(
                label = "Watchlist",
                icon = Icons.Outlined.Bookmarks,
                selected = currentTab == TvTab.Watchlist,
                modifier = Modifier
                    .focusRequester(watchlistFocus)
                    .focusProperties {
                        up = searchFocus
                        right = contentFocus
                    },
                onClick = { onTabSelected(TvTab.Watchlist) },
            )
        }

        TvRailButton(
            label = "Logout",
            icon = Icons.AutoMirrored.Outlined.Logout,
            selected = false,
            onClick = onLogout,
        )
    }
}

@Composable
private fun TvRailButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent
    val borderColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .tvFocusableClick(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun TvHomeTab(
    state: HomeUiState,
    contentFocus: FocusRequester,
    railFocus: FocusRequester,
    onAnimeClick: (String) -> Unit,
    onWatchClick: (String, String, Int, String?, Double?) -> Unit,
) {
    if (state.isLoading && state.latestAired.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        val heroItems = state.latestAired.take(8)
        val heroItem = heroItems.firstOrNull()
        if (heroItems.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    items(heroItems.size) { index ->
                        val item = heroItems[index]
                        TvHero(
                            item = item,
                            focusRequester = if (index == 0) contentFocus else FocusRequester(),
                            railFocus = railFocus,
                            modifier = Modifier
                                .width(980.dp)
                                .focusProperties { left = railFocus },
                            onClick = { onAnimeClick(item.activeSlug) },
                        )
                    }
                }
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item {
                Text("Continue Watching", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(state.continueWatching.size) { index ->
                        val item = state.continueWatching[index]
                        TvContinueWatchingCard(
                            item = item,
                            modifier = Modifier
                                .then(
                                    if (index == 0 && heroItem == null) {
                                        Modifier
                                            .focusRequester(contentFocus)
                                            .focusProperties { left = railFocus }
                                    } else {
                                        Modifier
                                    }
                                )
                                .focusProperties { left = railFocus },
                            onClick = {
                                onWatchClick(
                                    item.animeId,
                                    item.language ?: "sub",
                                    item.displayEpisode,
                                    item.server,
                                    item.progress,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (state.latestAired.isNotEmpty()) {
            item {
                TvAnimeShelf(
                    title = "Latest",
                    items = state.latestAired,
                    focusRequester = if (heroItem == null && state.continueWatching.isEmpty()) contentFocus else null,
                    railFocus = railFocus,
                    onAnimeClick = onAnimeClick,
                )
            }
        }
    }
}

@Composable
private fun TvHero(
    item: AnimeItem,
    focusRequester: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bannerUrl = item.bannerUrl ?: item.imageUrl
    val title = item.displayTitle.ifBlank { item.english.ifBlank { item.romaji.ifBlank { "Anime" } } }

    Box(
        modifier = modifier
            .height(360.dp)
            .clip(RoundedCornerShape(22.dp))
            .tvFocusableClick(shape = RoundedCornerShape(22.dp), onClick = onClick)
            .focusRequester(focusRequester)
            .focusProperties { left = railFocus },
    ) {
        AsyncImage(
            model = bannerUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(28.dp)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp,
                maxLines = 2,
            )
            val desc = item.description
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]*>"), "")
                .trim()
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(999.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Text("Open", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "DPAD OK to open",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun TvContinueWatchingCard(
    item: to.kuudere.anisuge.data.models.ContinueWatchingItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val title = item.resolveDisplayTitle()
    val progressFrac = if (item.duration > 0) (item.progress / item.duration).toFloat().coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .width(420.dp)
            .tvFocusableClick(shape = RoundedCornerShape(16.dp), onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101010)),
        ) {
            AsyncImage(
                model = item.banner ?: item.cover,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)))

            // progress bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.20f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFrac)
                        .height(5.dp)
                        .background(Color(0xFFBF80FF)),
                )
            }

            Text(
                text = "EP ${item.displayEpisode}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TvAnimeShelf(
    title: String,
    items: List<AnimeItem>,
    railFocus: FocusRequester,
    focusRequester: FocusRequester? = null,
    onAnimeClick: (String) -> Unit,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items.size) { index ->
                val anime = items[index]
                AnimeCard(
                    item = anime,
                    modifier = Modifier
                        .width(190.dp)
                        .then(
                            if (focusRequester != null && index == 0) {
                                Modifier
                                    .focusRequester(focusRequester)
                                    .focusProperties { left = railFocus }
                            } else {
                                Modifier
                            }
                        ),
                    onClick = { onAnimeClick(anime.activeSlug) },
                )
            }
        }
    }
}

@Composable
private fun TvSearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    state: to.kuudere.anisuge.screens.search.SearchUiState,
    contentFocus: FocusRequester,
    railFocus: FocusRequester,
    onAnimeClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Search", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("Anime title") },
                modifier = Modifier
                    .weight(1f)
                    .focusProperties { left = railFocus },
            )
            TextButton(onClick = onSearch, modifier = Modifier.height(56.dp)) {
                Text("Search")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoading && state.results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(state.results.size) { index ->
                    val anime = state.results[index]
                    AnimeCard(
                        item = anime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == 0) {
                                    Modifier
                                        .focusRequester(contentFocus)
                                        .focusProperties { left = railFocus }
                                } else {
                                    Modifier
                                }
                            ),
                        onClick = { onAnimeClick(anime.activeSlug) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvWatchlistTab(
    state: to.kuudere.anisuge.screens.watchlist.WatchlistState,
    contentFocus: FocusRequester,
    railFocus: FocusRequester,
    onFolderSelected: (String) -> Unit,
    onAnimeClick: (String) -> Unit,
) {
    val folders = listOf(
        "All lists" to "All",
        "WATCHING" to "WATCHING",
        "PLANNING" to "PLANNING",
        "COMPLETED" to "COMPLETED",
        "PAUSED" to "PAUSED",
        "DROPPED" to "DROPPED",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Watchlist", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders.size) { index ->
                val (label, apiFolder) = folders[index]
                val selected = state.selectedFolder.equals(label, ignoreCase = true)
                Text(
                    text = label,
                    color = if (selected) Color.Black else Color.White,
                    modifier = Modifier
                        .background(
                            if (selected) Color.White else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(999.dp),
                        )
                        .tvFocusableClick(shape = RoundedCornerShape(999.dp)) {
                            onFolderSelected(apiFolder)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoading && state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            return
        }

        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No anime in this list", color = Color.White.copy(alpha = 0.7f))
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            items(state.items.size) { index ->
                val anime = state.items[index]
                AnimeCard(
                    item = anime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (index == 0) {
                                Modifier
                                    .focusRequester(contentFocus)
                                    .focusProperties { left = railFocus }
                            } else {
                                Modifier
                            }
                        ),
                    onClick = { onAnimeClick(anime.activeSlug) },
                )
            }
        }
    }
}
