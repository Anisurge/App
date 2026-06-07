package to.kuudere.anisuge.screens.aichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.data.models.AiChatAnimeCard
import to.kuudere.anisuge.data.models.AiChatUiMessage

// ── Palette ───────────────────────────────────────────────────────────────────

private val AiAccent      = Color(0xFF9B59B6)   // purple
private val AiAccentLight = Color(0xFFBF80FF)
private val UserBubble    = Color(0xFF1E2D3D)
private val AiBubble      = Color(0xFF1A1A2E)
private val AiBubbleBorder = Color(0xFF9B59B6).copy(alpha = 0.5f)
private val TextPrimary   = Color(0xFFF0F0F0)
private val TextSecondary = Color(0xFF8A8A9A)
private val Surface       = Color(0xFF0D0D1A)
private val TopBarBg      = Color(0xFF0D0D1A).copy(alpha = 0.95f)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    onBack: () -> Unit,
    onAnimeClick: (animeId: String) -> Unit = {},
) {
    val state = viewModel.state
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Auto-scroll on new message / streaming update
    val messageCount = state.messages.size
    val isStreaming = state.streaming
    LaunchedEffect(messageCount, state.streamingText) {
        if (state.messages.isNotEmpty() || state.streaming) {
            val totalItems = state.messages.size + if (state.streaming) 1 else 0
            if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Subtle radial glow
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        listOf(AiAccent.copy(alpha = 0.12f), Color.Transparent),
                        radius = 400f,
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            // ── Top Bar ───────────────────────────────────────────────────────
            AiChatTopBar(
                onBack = onBack,
                onClear = { viewModel.clearHistory() },
            )

            // ── Messages List ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Welcome message when empty
                if (state.messages.isEmpty() && !state.streaming) {
                    item {
                        WelcomeCard(onSuggestionClick = { suggestion ->
                            viewModel.sendMessage(suggestion)
                        })
                    }
                }

                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg = msg, onAnimeClick = onAnimeClick)
                }

                // Streaming bubble
                if (state.streaming) {
                    item(key = "streaming") {
                        StreamingBubble(text = state.streamingText)
                    }
                }
            }

            // ── Error Banner ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                state.error?.let { err ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE50914).copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = err,
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // ── Input Bar ─────────────────────────────────────────────────────
            AiChatInput(
                value = inputText,
                onValueChange = {
                    inputText = it
                    if (state.error != null) viewModel.clearError()
                },
                enabled = !state.streaming,
                onSend = {
                    val msg = inputText.trim()
                    if (msg.isNotEmpty()) {
                        viewModel.sendMessage(msg)
                        inputText = ""
                    }
                },
            )
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────

@Composable
private fun AiChatTopBar(
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TopBarBg)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                )
            }
            Spacer(Modifier.width(4.dp))

            // AI icon + title
            Box(
                Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(listOf(AiAccent, AiAccentLight)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Surge AI",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    "Personal anime assistant",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }

            IconButton(onClick = onClear, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Clear chat",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Separator
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AiAccent.copy(alpha = 0.2f))
        )
    }
}


// ── Message Bubbles ───────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: AiChatUiMessage, onAnimeClick: (String) -> Unit) {
    val isUser = msg.role == "user"
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (!isUser) {
                Box(
                    Modifier
                        .padding(top = 4.dp, end = 8.dp)
                        .size(28.dp)
                        .background(
                            Brush.linearGradient(listOf(AiAccent, AiAccentLight)),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f, fill = false)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 18.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp,
                        )
                    )
                    .background(if (isUser) UserBubble else AiBubble)
                    .then(
                        if (!isUser) Modifier.padding(1.dp) else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isUser) {
                    Text(
                        text = msg.content,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                } else {
                    AiChatMarkdownText(
                        text = msg.content,
                        onAnimeClick = onAnimeClick,
                    )
                }
            }
        }

        // Anime card below the bubble
        if (!isUser && msg.anime != null) {
            Spacer(Modifier.height(6.dp))
            AiChatAnimeCardView(
                anime = msg.anime,
                onClick = { onAnimeClick(msg.anime.animeId) },
            )
        }
    }
}

// ── Anime Card ────────────────────────────────────────────────────────────────

@Composable
private fun AiChatAnimeCardView(
    anime: AiChatAnimeCard,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp)
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
            aiChatAnimeMetaLine(anime)?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            val description = aiChatPlainDescription(anime.description)
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

private fun aiChatAnimeMetaLine(anime: AiChatAnimeCard): String? {
    val pieces = listOfNotNull(
        anime.format?.takeIf { it.isNotBlank() },
        anime.year?.toString(),
        anime.episodes?.let { "$it eps" },
        anime.score?.let { "$it%" },
    )
    return pieces.joinToString(" / ").takeIf { it.isNotBlank() }
}

private fun aiChatPlainDescription(raw: String?): String {
    return (raw ?: "")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()
        .take(180)
}

@Composable
private fun StreamingBubble(text: String) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursor_alpha",
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        // AI avatar dot
        Box(
            Modifier
                .padding(top = 4.dp, end = 8.dp)
                .size(28.dp)
                .background(
                    Brush.linearGradient(listOf(AiAccent, AiAccentLight)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }

        Box(
            Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(AiBubble)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (text.isEmpty()) {
                // Typing dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { i ->
                        val dotAlpha by rememberInfiniteTransition(label = "dot$i").animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(400, delayMillis = i * 133),
                                RepeatMode.Reverse,
                            ),
                            label = "dot${i}_alpha",
                        )
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(AiAccentLight.copy(alpha = dotAlpha), CircleShape)
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AiChatStreamingText(
                        text = text,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "▌",
                        color = AiAccentLight.copy(alpha = cursorAlpha),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// ── Welcome Card ──────────────────────────────────────────────────────────────

@Composable
private fun WelcomeCard(onSuggestionClick: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(AiAccent, AiAccentLight)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Hey! I'm Surge AI ✨",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "I know your watchlist and what you're watching.\nAsk me for recommendations, discuss anime,\nor get help navigating Anisurge.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))

            // Suggestion chips
            val suggestions = listOf(
                "Recommend something similar to what I'm watching",
                "What's trending in anime right now?",
                "I finished my current show. What next?",
                "Tell me about a classic 90s anime",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AiAccent.copy(alpha = 0.12f))
                            .clickable { onSuggestionClick(suggestion) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = suggestion,
                            color = AiAccentLight,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── Input Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun AiChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TopBarBg)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AiAccent.copy(alpha = 0.15f))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ask me anything...",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                },
                enabled = enabled,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AiAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    cursorColor = AiAccentLight,
                    disabledBorderColor = Color.White.copy(alpha = 0.06f),
                    disabledTextColor = TextSecondary,
                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 14.sp,
                ),
            )

            // Send button
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled && value.isNotBlank())
                            Brush.linearGradient(listOf(AiAccent, AiAccentLight))
                        else
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { if (enabled && value.isNotBlank()) onSend() },
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!enabled) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (value.isNotBlank()) Color.White else TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
