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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import to.kuudere.anisuge.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun W2gPlayerScreen(
    inviteCode: String,
    viewModel: W2gViewModel,
    userId: String?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var chatInput by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    LaunchedEffect(inviteCode) {
        viewModel.setCurrentUserId(userId ?: "")
        viewModel.connect(inviteCode)
    }

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.disconnect()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.roomDetail?.roomName ?: "Room",
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(inviteCode))
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                "Copy invite code",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            viewModel.leaveRoom(inviteCode)
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Group, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${state.members.size}", color = Color.Gray, fontSize = 14.sp)
                        Spacer(Modifier.width(12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.background,
                ),
            )
        },
        containerColor = AppColors.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Player placeholder area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val animeId = state.roomDetail?.animeId
                if (animeId != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${animeId} \u2014 Ep ${state.roomDetail?.episodeNumber ?: 1}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${state.roomDetail?.server ?: "suzu"} \u00b7 ${state.roomDetail?.language ?: "sub"} \u00b7 ${state.roomDetail?.quality ?: "auto"}",
                            color = Color.Gray,
                            fontSize = 14.sp,
                        )
                    }
                } else if (state.isHost) {
                    Button(
                        onClick = { /* TODO: open anime search dialog */ },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search Anime to Watch")
                    }
                } else {
                    Text(
                        "Waiting for host to pick an anime...",
                        color = Color.Gray,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (state.isConnected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Connected", color = Color(0xFF4CAF50), fontSize = 12.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFA500))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Connecting...", color = Color(0xFFFFA500), fontSize = 12.sp)
                        }
                    }
            }

            val detail = state.roomDetail
            if (state.isHost && detail?.animeId != null) {
                RoomControls(
                    animeId = detail.animeId ?: "",
                    episodeNumber = detail.episodeNumber ?: 1,
                    server = detail.server ?: "suzu",
                    language = detail.language,
                    quality = detail.quality,
                    onChangeEpisode = { animeId, ep, server, lang, quality ->
                        viewModel.changeEpisode(animeId, ep, server, lang, quality)
                    },
                )
            }

            // Members row
            if (state.members.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Members (${state.members.size})",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.members.forEach { member ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(56.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    (member.username ?: "?").take(2).uppercase(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                )
                            }
                            Text(
                                member.username ?: "User",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Chat section
            Spacer(Modifier.height(12.dp))
            Text(
                "Room Chat",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = chatListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.chatMessages, key = { it.id }) { msg ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .padding(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                msg.username ?: "User",
                                color = AppColors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (msg.userId == state.roomDetail?.hostUserId) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.Verified,
                                    null,
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Text(
                            msg.body,
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Chat input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("Type a message...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = AppColors.accent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (chatInput.isNotBlank()) {
                            viewModel.sendMessage(chatInput.trim())
                            chatInput = ""
                        }
                    },
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        "Send",
                        tint = AppColors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomControls(
    animeId: String,
    episodeNumber: Int,
    server: String,
    language: String?,
    quality: String?,
    onChangeEpisode: (String, Int, String, String?, String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
    ) {
        Text("Host Controls", color = AppColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { /* Change anime - opens a dialog */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f),
            ) {
                Text("Change Anime", fontSize = 12.sp, color = Color.White)
            }
            Button(
                onClick = { onChangeEpisode(animeId, episodeNumber + 1, server, language, quality) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f),
            ) {
                Text("Next Ep", fontSize = 12.sp, color = Color.White)
            }
            Button(
                onClick = { onChangeEpisode(animeId, (episodeNumber - 1).coerceAtLeast(1), server, language, quality) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f),
            ) {
                Text("Prev Ep", fontSize = 12.sp, color = Color.White)
            }
        }
    }
}
