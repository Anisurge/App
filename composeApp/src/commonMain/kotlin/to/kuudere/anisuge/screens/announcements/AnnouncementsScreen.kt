package to.kuudere.anisuge.screens.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.Announcement
import to.kuudere.anisuge.data.models.AnnouncementPoll
import to.kuudere.anisuge.platform.rememberChatImagePicker
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.ui.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(
    viewModel: AnnouncementsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val pickImage = rememberChatImagePicker(
        allowVideo = false,
        onResult = viewModel::uploadImage,
    )

    LaunchedEffect(Unit) {
        viewModel.load(force = true)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.refreshStatus() }
    }

    state.toast?.let { toast ->
        LaunchedEffect(toast) {
            kotlinx.coroutines.delay(2400)
            viewModel.clearToast()
        }
    }

    if (state.composer.open) {
        AnnouncementComposerDialog(
            state = state.composer,
            saving = state.saving,
            onDismiss = viewModel::dismissComposer,
            onChange = viewModel::updateComposer,
            onPickImage = pickImage,
            onAddPollOption = viewModel::addPollOption,
            onRemovePollOption = viewModel::removePollOption,
            onPollOptionChange = viewModel::updatePollOption,
            onSave = viewModel::saveAnnouncement,
        )
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Announcements", color = AppColors.text, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.unreadCount > 0) "${state.unreadCount} new" else "Official Anisurge notices",
                            color = AppColors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.text)
                    }
                },
                actions = {
                    if (state.isStaff) {
                        IconButton(onClick = viewModel::openComposer) {
                            Icon(Icons.Default.Add, contentDescription = "New announcement", tint = AppColors.accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.surface.copy(alpha = 0.96f)),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.announcements.isEmpty() -> {
                    CircularProgressIndicator(
                        color = AppColors.accent,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.error != null && state.announcements.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(state.error ?: "Failed to load announcements", color = AppColors.textMuted)
                        Button(onClick = { viewModel.load(force = true) }) { Text("Retry") }
                    }
                }
                state.announcements.isEmpty() -> {
                    Text(
                        "No announcements yet",
                        color = AppColors.textMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.announcements, key = { it.id }) { announcement ->
                            AnnouncementCard(
                                announcement = announcement,
                                onUpvote = { viewModel.toggleUpvote(announcement.id) },
                                onPollVote = { optionId -> viewModel.votePoll(announcement.id, optionId) },
                                onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                            )
                        }
                        if (state.hasMore) {
                            item {
                                OutlinedButton(
                                    onClick = viewModel::loadMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (state.isLoading) "Loading…" else "Load more")
                                }
                            }
                        }
                    }
                }
            }

            state.toast?.let {
                Surface(
                    color = Color.Black.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                ) {
                    Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onUpvote: () -> Unit,
    onPollVote: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Surface(
        color = AppColors.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (announcement.pinned) AppColors.accent.copy(alpha = 0.55f) else AppColors.border,
                RoundedCornerShape(18.dp),
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAvatar(
                    url = announcement.author.avatarUrl,
                    avatarSize = 36.dp,
                    contentDescription = announcement.author.username,
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            announcement.title,
                            color = AppColors.text,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (announcement.pinned) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = AppColors.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        "by ${announcement.author.username} • ${formatAnnouncementDate(announcement.publishedAt ?: announcement.createdAt)}",
                        color = AppColors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (announcement.status != "published") {
                    Text(
                        announcement.status.uppercase(),
                        color = AppColors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AppColors.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            MarkdownText(announcement.body)

            announcement.media.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = announcement.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable { onOpenUrl(url) },
                )
            }

            announcement.media.videoUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
                        .clickable { onOpenUrl(url) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Video", color = AppColors.text, fontWeight = FontWeight.SemiBold)
                        Text(url, color = AppColors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Link, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
                }
            }

            announcement.poll?.takeIf { it.options.isNotEmpty() }?.let { poll ->
                AnnouncementPollView(poll = poll, onVote = onPollVote)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onUpvote) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (announcement.upvoted) Color(0xFFFF5A7D) else AppColors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${announcement.upvoteCount}", color = AppColors.text)
                }
                if (!announcement.read) {
                    Text(
                        "NEW",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFE50914))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementPollView(
    poll: AnnouncementPoll,
    onVote: (String) -> Unit,
) {
    val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(poll.question.ifBlank { "Poll" }, color = AppColors.text, fontWeight = FontWeight.Bold)
        poll.options.forEach { option ->
            val selected = poll.myOptionId == option.id
            val fraction = option.votes.toFloat() / totalVotes.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) AppColors.accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
                    .clickable { onVote(option.id) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(option.text, color = AppColors.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${option.votes}", color = AppColors.textMuted, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    color = if (selected) AppColors.accent else AppColors.textMuted,
                    trackColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
                )
            }
        }
    }
}

@Composable
private fun AnnouncementComposerDialog(
    state: AnnouncementComposerState,
    saving: Boolean,
    onDismiss: () -> Unit,
    onChange: ((AnnouncementComposerState) -> AnnouncementComposerState) -> Unit,
    onPickImage: () -> Unit,
    onAddPollOption: () -> Unit,
    onRemovePollOption: (Int) -> Unit,
    onPollOptionChange: (Int, String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New announcement", color = AppColors.text, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.textMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { value -> onChange { it.copy(title = value.take(140)) } },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.body,
                    onValueChange = { value -> onChange { it.copy(body = value.take(8000)) } },
                    label = { Text("Markdown body") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onPickImage, enabled = !state.uploadingImage) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.uploadingImage) "Uploading…" else "Upload image")
                    }
                    if (state.imageUrl.isNotBlank()) {
                        Text("Image ready", color = AppColors.accent, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = state.imageUrl,
                    onValueChange = { value -> onChange { it.copy(imageUrl = value) } },
                    label = { Text("Image URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.videoUrl,
                    onValueChange = { value -> onChange { it.copy(videoUrl = value) } },
                    label = { Text("Direct video URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = state.pinned, onCheckedChange = { checked -> onChange { it.copy(pinned = checked) } })
                    Text("Pinned", color = AppColors.text)
                    Spacer(Modifier.weight(1f))
                    Text("Publish", color = AppColors.text)
                    Switch(checked = state.publishNow, onCheckedChange = { checked -> onChange { it.copy(publishNow = checked) } })
                }
                Text("Poll", color = AppColors.text, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.pollQuestion,
                    onValueChange = { value -> onChange { it.copy(pollQuestion = value.take(180)) } },
                    label = { Text("Poll question") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.pollOptions.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { value -> onPollOptionChange(index, value.take(120)) },
                            label = { Text("Option ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemovePollOption(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove option")
                        }
                    }
                }
                OutlinedButton(onClick = onAddPollOption, enabled = state.pollOptions.size < 6) {
                    Text("Add poll option")
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.publishNow) "Post" else "Save draft")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = AppColors.surface,
    )
}

@Composable
private fun MarkdownText(raw: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        raw.replace("\r\n", "\n").split("\n").forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> Spacer(Modifier.height(4.dp))
                trimmed.startsWith("### ") -> Text(inlineMarkdown(trimmed.removePrefix("### ")), color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                trimmed.startsWith("## ") -> Text(inlineMarkdown(trimmed.removePrefix("## ")), color = AppColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                trimmed.startsWith("# ") -> Text(inlineMarkdown(trimmed.removePrefix("# ")), color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                trimmed.startsWith("> ") -> Text(
                    inlineMarkdown(trimmed.removePrefix("> ")),
                    color = AppColors.textMuted,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .border(2.dp, AppColors.accent.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text("• ${trimmed.drop(2)}", color = AppColors.text, lineHeight = 20.sp)
                Regex("^\\d+\\.\\s+").containsMatchIn(trimmed) -> Text(trimmed, color = AppColors.text, lineHeight = 20.sp)
                else -> Text(inlineMarkdown(trimmed), color = AppColors.text, lineHeight = 20.sp)
            }
        }
    }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val boldStart = text.indexOf("**", index)
        val italicStart = text.indexOf("*", index).takeIf { it >= 0 && text.getOrNull(it + 1) != '*' } ?: -1
        val codeStart = text.indexOf("`", index)
        val next = listOf(boldStart, italicStart, codeStart).filter { it >= 0 }.minOrNull()
        if (next == null) {
            append(text.substring(index))
            break
        }
        if (next > index) append(text.substring(index, next))
        when (next) {
            boldStart -> {
                val end = text.indexOf("**", next + 2)
                if (end > next) {
                    val start = length
                    append(text.substring(next + 2, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    index = end + 2
                } else {
                    append("**")
                    index = next + 2
                }
            }
            italicStart -> {
                val end = text.indexOf("*", next + 1)
                if (end > next) {
                    val start = length
                    append(text.substring(next + 1, end))
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    index = end + 1
                } else {
                    append("*")
                    index = next + 1
                }
            }
            else -> {
                val end = text.indexOf("`", next + 1)
                if (end > next) {
                    val start = length
                    append(text.substring(next + 1, end))
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.White.copy(alpha = 0.10f),
                        ),
                        start,
                        length,
                    )
                    index = end + 1
                } else {
                    append("`")
                    index = next + 1
                }
            }
        }
    }
}

private fun formatAnnouncementDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.substringBefore("T").ifBlank { raw.take(16) }
}
