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
import to.kuudere.anisuge.theme.AppColors
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
                    containerColor = AppColors.background
                )
            )
        },
        containerColor = AppColors.background
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LangFilter.values().forEach { filter ->
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = {
                            Text(
                                filter.name,
                                fontSize = 13.sp,
                                fontWeight = if (state.selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.accent.copy(alpha = 0.3f),
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.9f),
                            selectedLabelColor = Color.White,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.15f),
                            selectedBorderColor = AppColors.accent,
                            enabled = true,
                            selected = state.selectedFilter == filter,
                        ),
                    )
                }
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
            ) {
                val isSmall = maxWidth < 800.dp
                val columns = if (isSmall) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 160.dp)
                val hPadding = if (isSmall) 12.dp else 24.dp
                val itemSpacing = if (isSmall) 8.dp else 16.dp
                val showOffline = state.isOffline && state.results.isEmpty()

                if (showOffline) {
                    OfflineState(onRetry = { viewModel.refresh() }, isLoading = state.isLoading)
                } else if (state.error != null && state.results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.refresh() },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
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
                    } else if (state.results.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No episodes to show.",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.refresh() },
                                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
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
                                showLatestLangBadge = true,
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
}
