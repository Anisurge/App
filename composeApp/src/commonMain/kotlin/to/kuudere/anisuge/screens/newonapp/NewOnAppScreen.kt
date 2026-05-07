package to.kuudere.anisuge.screens.newonapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import to.kuudere.anisuge.screens.search.SearchViewModel
import to.kuudere.anisuge.ui.AnimeCard
import to.kuudere.anisuge.ui.OfflineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOnAppScreen(
    viewModel: SearchViewModel,
    onAnimeClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        // "New on App" is effectively the catalog sorted by Latest in this codebase.
        viewModel.applyPreset(keyword = "", selectedSort = "Latest")
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: 0 }
            .distinctUntilChanged()
            .collectLatest { lastVisibleIndex ->
                val total = scrollState.layoutInfo.totalItemsCount
                if (total > 0 && lastVisibleIndex >= total - 6) {
                    viewModel.search(loadMore = true)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New on App",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF000000)),
            )
        },
        containerColor = Color(0xFF000000),
    ) { paddingValues ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val isSmall = maxWidth < 800.dp
            val columns = if (isSmall) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 160.dp)
            val hPadding = if (isSmall) 12.dp else 24.dp
            val itemSpacing = if (isSmall) 8.dp else 16.dp

            val showOffline = state.isOffline && state.results.isEmpty()
            if (showOffline) {
                OfflineState(onRetry = { viewModel.search() }, isLoading = state.isLoading)
                return@BoxWithConstraints
            }

            LazyVerticalGrid(
                columns = columns,
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = hPadding,
                    end = hPadding,
                    top = 8.dp,
                    bottom = 100.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                if (state.isLoading && state.results.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                } else if (state.results.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Nothing to show yet.", color = Color.White.copy(alpha = 0.7f))
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.search() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBF80FF)),
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                } else {
                    items(state.results) { anime ->
                        AnimeCard(
                            item = anime,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onAnimeClick(anime.activeSlug) },
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

