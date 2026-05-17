package to.kuudere.anisuge.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.ui.ChatUsernameLabel
import to.kuudere.anisuge.ui.ProfileAvatar
import to.kuudere.anisuge.ui.chatAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(
    viewModel: LiveChatViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var didInitialScroll by remember { mutableStateOf(false) }
    var messageCountBeforeOlder by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    LaunchedEffect(state.isLoading, state.messages.size) {
        if (!state.isLoading && state.messages.isNotEmpty() && !didInitialScroll) {
            listState.scrollToItem(state.messages.lastIndex)
            didInitialScroll = true
        }
    }

    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (!didInitialScroll || state.messages.isEmpty()) return@LaunchedEffect
        val lastIndex = state.messages.lastIndex
        val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            ?.let { it >= lastIndex - 1 } ?: true
        if (nearBottom) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    LaunchedEffect(state.isLoadingOlder) {
        if (state.isLoadingOlder) {
            messageCountBeforeOlder = state.messages.size
        }
    }

    LaunchedEffect(state.isLoadingOlder, state.messages.size) {
        if (!state.isLoadingOlder && messageCountBeforeOlder > 0) {
            val added = state.messages.size - messageCountBeforeOlder
            if (added > 0) {
                listState.scrollToItem(
                    listState.firstVisibleItemIndex + added,
                    listState.firstVisibleItemScrollOffset,
                )
            }
            messageCountBeforeOlder = 0
        }
    }

    LaunchedEffect(didInitialScroll, state.hasMoreOlder, state.isLoadingOlder) {
        if (!didInitialScroll) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .filter { index ->
                !state.isLoadingOlder &&
                    state.hasMoreOlder &&
                    index <= 1 &&
                    state.messages.isNotEmpty()
            }
            .collect {
                viewModel.loadOlderMessages()
            }
    }

    state.selectedMember?.let { member ->
        ChatMemberSheet(
            member = member,
            onDismiss = viewModel::dismissMemberProfile,
        )
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Text(
                            "Sign in to join live chat",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Use your Anisurge account. If you were already signed in, sign out and sign in again to refresh chat access.",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
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
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding(),
                ) {
                    val chatWidth = if (maxWidth > 720.dp) 720.dp else maxWidth
                    Column(
                        Modifier
                            .width(chatWidth)
                            .fillMaxHeight()
                            .align(Alignment.Center),
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
                        if (state.isLoadingOlder || state.hasMoreOlder) {
                            item(key = "load-older-header") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.isLoadingOlder) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = Color.White.copy(alpha = 0.6f),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text(
                                            "Scroll up for older messages",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }

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
                                    onProfileClick = { viewModel.showMemberProfile(message) },
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
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isMine: Boolean,
    onProfileClick: () -> Unit,
) {
    val bubbleColor = if (isMine) Color(0xFF8B1520) else Color(0xFF1E1E1E)
    val accent = chatAccentColor(message.userId, isMine)
    val profileClick = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onProfileClick,
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine) {
            ProfileAvatar(
                url = message.avatarUrl,
                modifier = Modifier.size(36.dp).then(profileClick),
                contentDescription = message.username,
            )
            Spacer(Modifier.size(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            ChatUsernameLabel(
                message = message,
                isMine = isMine,
                modifier = profileClick,
                fontSize = 11.sp,
                fontWeight = if (isMine) FontWeight.Normal else FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
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
                    .background(bubbleColor)
                    .then(
                        if (!isMine) {
                            Modifier.border(
                                1.dp,
                                accent.copy(alpha = 0.55f),
                                RoundedCornerShape(12.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
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

        if (isMine) {
            Spacer(Modifier.size(8.dp))
            ProfileAvatar(
                url = message.avatarUrl,
                modifier = Modifier.size(36.dp).then(profileClick),
                contentDescription = message.username,
            )
        }
    }
}
