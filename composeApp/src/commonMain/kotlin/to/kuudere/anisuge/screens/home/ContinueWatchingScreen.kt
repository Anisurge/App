package to.kuudere.anisuge.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.ContinueWatchingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueWatchingScreen(
    viewModel: HomeViewModel,
    onWatchClick: (String, String, Int, String?) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshContinueWatching()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Continue Watching",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isSmall = maxWidth < 800.dp
            val columns = if (isSmall) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 280.dp)
            val hPadding = if (isSmall) 16.dp else 24.dp
            val spacing = if (isSmall) 14.dp else 18.dp

            when {
                state.isLoading && state.continueWatching.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                state.continueWatching.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nothing to continue yet",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = columns,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = hPadding,
                            end = hPadding,
                            top = 10.dp,
                            bottom = 100.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        items(state.continueWatching) { item ->
                            ContinueWatchingGridCard(
                                item = item,
                                onClick = {
                                    val animeId = item.animeId.ifBlank { item.link.removePrefix("/").split("/").getOrNull(1).orEmpty() }
                                    val lang = item.language ?: parseQueryParam(item.link, "lang") ?: "sub"
                                    val server = item.server ?: parseQueryParam(item.link, "server")
                                    onWatchClick(animeId, lang, item.episode, server)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingGridCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111111))
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(parseProgressFraction(item.progress, item.duration))
                        .height(4.dp)
                        .background(Color(0xFFE50914))
                )
            }

            Text(
                "EP ${item.episode}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Text(
                "${item.progress}/${item.duration}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun parseProgressFraction(progress: String, duration: String): Float {
    fun toSeconds(value: String): Int {
        val parts = value.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }

    val total = toSeconds(duration)
    return if (total > 0) (toSeconds(progress).toFloat() / total).coerceIn(0f, 1f) else 0f
}

private fun parseQueryParam(url: String, key: String): String? {
    val query = url.substringAfter('?', "")
    return query.split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
}
