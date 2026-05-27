package to.kuudere.anisuge.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import anisurge.composeapp.generated.resources.Res
import anisurge.composeapp.generated.resources.chat_bg
import org.jetbrains.compose.resources.painterResource
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.ChatAction
import to.kuudere.anisuge.data.models.ChatAnimeCard
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.ui.ChatUsernameLabel
import to.kuudere.anisuge.ui.ChatDecoratedAvatar
import to.kuudere.anisuge.ui.chatAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(
    viewModel: LiveChatViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onAction: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var didInitialScroll by remember { mutableStateOf(false) }
    var messageCountBeforeOlder by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        didInitialScroll = false
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
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
            listState.scrollToItem(lastIndex)
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
            onAnimeClick = { animeId ->
                viewModel.dismissMemberProfile()
                onAction("anisurge://anime/$animeId")
            },
        )
    }

    if (state.animePickerOpen) {
        AnimePickerDialog(
            query = state.animePickerQuery,
            results = state.animePickerResults,
            isLoading = state.animePickerLoading,
            error = state.animePickerError,
            onQueryChange = viewModel::onAnimePickerQueryChange,
            onSelect = viewModel::sendAnimeCard,
            onDismiss = viewModel::dismissAnimePicker,
        )
    }

    Box(Modifier.fillMaxSize()) {
        LiveChatBackground()

        Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    scrolledContainerColor = Color.Black.copy(alpha = 0.72f),
                ),
            )
        },
        containerColor = Color.Transparent,
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
                            "Sign in to join community chat",
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
                            items(
                                state.messages,
                                key = { it.id },
                                contentType = { "chat-message" },
                            ) { message ->
                                ChatMessageRow(
                                    message = message,
                                    isMine = message.userId == state.currentUserId,
                                    onProfileClick = { viewModel.showMemberProfile(message) },
                                    onActionClick = onAction,
                                )
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (shouldShowSurgeSuggestion(state.draft)) {
                                SurgeMentionSuggestion(onClick = viewModel::insertSurgeMention)
                                Spacer(Modifier.height(6.dp))
                            }
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = viewModel::onDraftChange,
                                modifier = Modifier.fillMaxWidth(),
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
                        }
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
private fun LiveChatBackground() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.chat_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isMine: Boolean,
    onProfileClick: () -> Unit,
    onActionClick: (String) -> Unit,
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
            ChatDecoratedAvatar(
                avatarUrl = message.effectiveAvatarUrl,
                frameUrl = message.avatarFrameUrl,
                outerFrameUrl = message.avatarOuterUrl,
                userId = message.userId,
                modifier = profileClick,
                avatarSize = 36.dp,
                contentDescription = message.username,
                playVideo = false,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.body.isNotBlank()) {
                        Text(
                            markdownText(message.body),
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    message.metadata.anime?.let { anime ->
                        ChatAnimeCardView(
                            anime = anime,
                            onClick = { onActionClick("anisurge://anime/${anime.animeId}") },
                        )
                    }
                    if (message.metadata.actions.isNotEmpty()) {
                        ChatActionButtons(
                            actions = message.metadata.actions,
                            onActionClick = onActionClick,
                        )
                    }
                }
            }
        }

        if (isMine) {
            Spacer(Modifier.size(8.dp))
            ChatDecoratedAvatar(
                avatarUrl = message.effectiveAvatarUrl,
                frameUrl = message.avatarFrameUrl,
                outerFrameUrl = message.avatarOuterUrl,
                userId = message.userId,
                modifier = profileClick,
                avatarSize = 36.dp,
                contentDescription = message.username,
                playVideo = false,
            )
        }
    }
}

@Composable
private fun SurgeMentionSuggestion(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF241035))
            .border(1.dp, Color(0xFFBF80FF).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "@surge",
            color = Color(0xFFBF80FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Anisurge AI",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
    }
}

private fun shouldShowSurgeSuggestion(draft: String): Boolean {
    val lastAt = draft.lastIndexOf('@')
    if (lastAt < 0) return false
    val token = draft.substring(lastAt)
    if (token.any { it.isWhitespace() }) return false
    return "@surge".startsWith(token.lowercase()) && token.lowercase() != "@surge"
}

@Composable
private fun ChatActionButtons(
    actions: List<ChatAction>,
    onActionClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.filter { it.label.isNotBlank() && it.deeplink.isNotBlank() }.forEach { action ->
            OutlinedButton(
                onClick = { onActionClick(action.deeplink) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(action.label, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private fun markdownText(raw: String): AnnotatedString {
    val normalized = raw
        .replace("\r\n", "\n")
        .lines()
        .joinToString("\n") { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("- ") -> "• ${trimmed.drop(2)}"
                trimmed.startsWith("* ") -> "• ${trimmed.drop(2)}"
                else -> line
            }
        }

    return buildAnnotatedString {
        appendMarkdownInline(normalized)
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", startIndex = i + 2)
                if (end > i + 2) {
                    val start = length
                    append(text.substring(i + 2, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', startIndex = i + 1)
                if (end > i + 1) {
                    val start = length
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(color = Color(0xFFFFD166)), start, length)
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', startIndex = i + 1)
                if (end > i + 1 && (i + 1 >= text.length || text[i + 1] != '*')) {
                    val start = length
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
private fun ChatAnimeCardView(
    anime: ChatAnimeCard,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = anime.imageUrl,
            contentDescription = anime.title,
            modifier = Modifier
                .width(58.dp)
                .height(82.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                anime.title.ifBlank { "Anime" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            chatAnimeMetaLine(anime)?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            val description = plainChatDescription(anime.description)
            if (description.isNotBlank()) {
                Text(
                    description,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun AnimePickerDialog(
    query: String,
    results: List<AnimeItem>,
    isLoading: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (AnimeItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Share anime") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search anime", color = Color.White.copy(alpha = 0.45f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFE50914),
                        focusedBorderColor = Color(0xFFE50914),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    ),
                )

                if (error != null) {
                    Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }

                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 390.dp)) {
                    when {
                        isLoading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                        )
                        results.isEmpty() -> Text(
                            "No results yet",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(results, key = { it.animeId }) { anime ->
                                AnimePickerResultRow(
                                    anime = anime,
                                    onClick = { onSelect(anime) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun AnimePickerResultRow(
    anime: AnimeItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = anime.imageUrl,
            contentDescription = anime.displayTitle,
            modifier = Modifier
                .width(48.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                anime.displayTitle.ifBlank { anime.animeId },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            val meta = listOfNotNull(
                anime.format.takeIf { it.isNotBlank() },
                anime.seasonYear?.toString(),
                anime.epCount?.let { "$it eps" },
            ).joinToString(" / ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            val description = plainChatDescription(anime.description)
            if (description.isNotBlank()) {
                Text(
                    description,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun chatAnimeMetaLine(anime: ChatAnimeCard): String? {
    val pieces = listOfNotNull(
        anime.format?.takeIf { it.isNotBlank() },
        anime.year?.toString(),
        anime.episodes?.let { "$it eps" },
        anime.score?.let { "$it%" },
    )
    return pieces.joinToString(" / ").takeIf { it.isNotBlank() }
}

private fun plainChatDescription(raw: String): String {
    return raw
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()
        .take(180)
}
