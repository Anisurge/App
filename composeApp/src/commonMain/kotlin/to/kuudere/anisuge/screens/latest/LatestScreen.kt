package to.kuudere.anisuge.screens.latest

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
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
import to.kuudere.anisuge.ui.OfflineState
import to.kuudere.anisuge.ui.AnimeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestEpisodesScreen(
    viewModel: LatestViewModel,
    onAnimeClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyGridState()

    // Infinite scroll listener
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo }.collect { layoutInfo ->
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 5) {
                viewModel.loadMore()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Latest Episodes",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000)
                )
            )
        },
        containerColor = Color(0xFF000000)
    ) { paddingValues ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isSmall = maxWidth < 800.dp
            val columns = if (isSmall) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 160.dp)
            val hPadding = if (isSmall) 12.dp else 24.dp
            val itemSpacing = if (isSmall) 8.dp else 16.dp
            val showOffline = state.isOffline && state.results.isEmpty()

            if (showOffline) {
                OfflineState(onRetry = { viewModel.refresh() }, isLoading = state.isLoading)
            } else {
                LazyVerticalGrid(
                    columns = columns,
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = hPadding,
                        end = hPadding,
                        top = 8.dp,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    if (state.isLoading && state.results.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    } else {
                        items(state.results) { anime ->
                            AnimeCard(
                                item = anime,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onAnimeClick(anime.activeSlug) }
                            )
                        }
                    }

                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
