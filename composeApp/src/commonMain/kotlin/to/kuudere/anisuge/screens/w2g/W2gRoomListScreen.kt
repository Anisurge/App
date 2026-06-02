package to.kuudere.anisuge.screens.w2g

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil3.annotation.ExperimentalCoilApi
import to.kuudere.anisuge.data.models.W2gRoomSummary
import to.kuudere.anisuge.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun W2gRoomListScreen(
    viewModel: W2gViewModel,
    onRoomClick: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadRooms()
    }

    if (showCreateSheet) {
        W2gCreateRoomSheet(
            viewModel = viewModel,
            onRoomCreated = { code ->
                showCreateSheet = false
                onRoomClick(code, false)
            },
            onDismiss = { showCreateSheet = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch2gether") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showCreateSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create Room", fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.background,
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = AppColors.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoadingRooms -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.accent)
                    }
                }
                state.rooms.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No active rooms found",
                                color = AppColors.textMuted,
                                fontSize = 16.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Create one or check back later!",
                                color = Color.Gray,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(state.rooms, key = { it.inviteCode }) { room ->
                            RoomCard(
                                room = room,
                                onClick = { onRoomClick(room.inviteCode, room.hasPassword) },
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: W2gRoomSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = room.animePoster,
            contentDescription = room.animeTitle,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = room.animeTitle ?: "Unknown Anime",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ep ${room.episodeNumber} \u00b7 ${room.server}",
                color = AppColors.textMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Host: ${room.hostUsername ?: "Unknown"}",
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Group,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AppColors.textMuted,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${room.memberCount}",
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                )
            }
            if (room.hasPassword) {
                Spacer(Modifier.height(6.dp))
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Password protected",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFFA500),
                )
            }
        }
    }
}
