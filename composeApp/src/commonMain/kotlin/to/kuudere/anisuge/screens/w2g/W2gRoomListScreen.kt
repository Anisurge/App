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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.annotation.ExperimentalCoilApi
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var showCreateSheet by remember { mutableStateOf(false) }
    var protectedRoom by remember { mutableStateOf<W2gRoomSummary?>(null) }
    var joinPassword by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }
    var isJoiningRoom by remember { mutableStateOf(false) }

    fun joinAndNavigate(room: W2gRoomSummary, password: String? = null) {
        if (isJoiningRoom) return
        isJoiningRoom = true
        joinError = null
        scope.launch {
            val result = viewModel.joinRoom(room.inviteCode, password)
            isJoiningRoom = false
            result
                .onSuccess {
                    protectedRoom = null
                    joinPassword = ""
                    onRoomClick(room.inviteCode, false)
                }
                .onFailure {
                    joinError = it.message ?: "Failed to join room"
                }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startRoomAutoRefresh()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopRoomAutoRefresh() }
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Search rooms...", color = Color.Gray) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, null, tint = AppColors.textMuted)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Outlined.Clear, "Clear", tint = AppColors.textMuted)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppColors.accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = AppColors.accent,
                ),
            )

            when {
                state.isLoadingRooms -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.accent)
                    }
                }
                state.filteredRooms.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (state.searchQuery.isNotBlank()) "No rooms matching \"${state.searchQuery}\""
                                else "No active rooms found",
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
                        items(state.filteredRooms, key = { it.inviteCode }) { room ->
                            RoomCard(
                                room = room,
                                onClick = {
                                    if (room.hasPassword) {
                                        protectedRoom = room
                                        joinPassword = ""
                                        joinError = null
                                    } else {
                                        joinAndNavigate(room)
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }

    protectedRoom?.let { room ->
        AlertDialog(
            onDismissRequest = {
                if (!isJoiningRoom) {
                    protectedRoom = null
                    joinPassword = ""
                    joinError = null
                }
            },
            title = { Text("Enter Room Password", color = Color.White) },
            text = {
                Column {
                    Text(
                        room.roomName,
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = joinPassword,
                        onValueChange = {
                            joinPassword = it
                            joinError = null
                        },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = AppColors.accent,
                            unfocusedLabelColor = Color.Gray,
                            focusedBorderColor = AppColors.accent,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = AppColors.accent,
                        ),
                    )
                    joinError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color.Red, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (joinPassword.isBlank()) {
                            joinError = "Password required"
                        } else {
                            joinAndNavigate(room, joinPassword)
                        }
                    },
                    enabled = !isJoiningRoom,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                ) {
                    Text(if (isJoiningRoom) "Joining..." else "Join")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        protectedRoom = null
                        joinPassword = ""
                        joinError = null
                    },
                    enabled = !isJoiningRoom,
                ) {
                    Text("Cancel", color = AppColors.textMuted)
                }
            },
            containerColor = AppColors.background,
        )
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
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (room.animePoster != null) {
                AsyncImage(
                    model = room.animePoster,
                    contentDescription = room.animeTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.Group,
                    null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = room.roomName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (room.animeTitle != null) {
                Text(
                    text = room.animeTitle,
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }
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
