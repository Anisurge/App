package to.kuudere.anisuge.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import to.kuudere.anisuge.data.models.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(
    viewModel: LiveChatViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            val lastIndex = state.messages.lastIndex
            val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?.let { it >= lastIndex - 2 } ?: true
            if (nearBottom) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.roomName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (state.connectionState) {
                                LiveChatConnectionState.Connected ->
                                    "${state.onlineCount} online"
                                LiveChatConnectionState.Connecting -> "Connecting…"
                                LiveChatConnectionState.Disconnected -> "Offline"
                                LiveChatConnectionState.Error -> "Connection issue"
                            },
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                        )
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        when {
            state.needsAuth -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Sign in to join live chat",
                            color = Color.White,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onSignIn) {
                            Text("Sign in")
                        }
                    }
                }
            }
            state.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding(),
                ) {
                    if (state.error != null) {
                        Text(
                            state.error ?: "",
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.messages.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "No messages yet. Say hi!",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        } else {
                            items(state.messages, key = { it.id }) { message ->
                                ChatMessageRow(
                                    message = message,
                                    isMine = message.userId == state.currentUserId,
                                )
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = viewModel::onDraftChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text("Message…", color = Color.White.copy(alpha = 0.4f))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFE50914),
                                focusedBorderColor = Color(0xFFE50914),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            ),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            enabled = !state.isSending,
                        )
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = state.draft.isNotBlank() && !state.isSending,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.draft.isNotBlank()) Color(0xFFE50914)
                                    else Color(0xFF333333),
                                ),
                        ) {
                            if (state.isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isMine: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        if (!isMine) {
            AsyncImage(
                model = message.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.size(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            if (!isMine) {
                Text(
                    message.username,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }
            Box(
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isMine) 12.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 12.dp,
                        ),
                    )
                    .background(if (isMine) Color(0xFF8B1520) else Color(0xFF1E1E1E))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    message.body,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
