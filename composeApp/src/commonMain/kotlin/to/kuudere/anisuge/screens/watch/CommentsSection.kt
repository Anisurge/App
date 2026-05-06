package to.kuudere.anisuge.screens.watch

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.Comment

// ── Colour palette — mirrors Kuudere's zinc/black dark theme ─────────────────
private val BgBlack      = Color(0xFF000000)
private val BgDark       = Color(0xFF000000)    // zinc-950
private val BgCard       = Color(0xFF000000)    // zinc-900
private val BgInput      = Color(0xFF000000)    // zinc-800 / 50%
private val BorderSub    = Color(0xFF3F3F46)    // zinc-700 / 50%
private val BorderLine   = Color(0xFF000000)    // zinc-800 / 80% — thread lines
private val TextPrimary  = Color(0xFFE4E4E7)    // zinc-200
private val TextSec      = Color(0xFFA1A1AA)    // zinc-400
private val TextMuted    = Color(0xFF71717A)    // zinc-500
private val AccentRed    = Color(0xFFBF80FF)
private val AccentBlue   = Color(0xFF3B82F6)

// ── Internal mutable UI model ─────────────────────────────────────────────────

data class CommentUiModel(
    val data: Comment,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val isLiked: Boolean = false,
    val isUnliked: Boolean = false,
    val showReplies: Boolean = false,
    val isReplying: Boolean = false,
    val replyText: String = "",
    val replyIsSpoiler: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoadingReplies: Boolean = false,
    val replies: List<CommentUiModel> = emptyList(),
    val hasMoreReplies: Boolean = false,
    val repliesPage: Int = 0,
    val showMoreDropdown: Boolean = false,
)

private fun Comment.toUi(): CommentUiModel = CommentUiModel(
    data = this,
    likes = this.likes,
    dislikes = this.dislikes,
    isLiked = this.isLiked,
    isUnliked = this.isDisliked,
    showReplies = this.showReplies,
    replies = this.replies.map { it.toUi() },
    hasMoreReplies = this.hasMoreReplies,
    repliesPage = this.repliesPage,
)

// ── CommentsSection ────────────────────────────────────────────────────────────

@Composable
fun CommentsSection(
    animeId: String,
    episodeNumber: Int,
    userId: String?,
    username: String?,
    userPfp: String?,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val commentService = remember { AppComponent.commentService }
    val isAuthenticated = userId != null

    var comments by remember { mutableStateOf<List<CommentUiModel>>(emptyList()) }
    var totalComments by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("newest") }

    var rootText by remember { mutableStateOf("") }
    var isPostingRoot by remember { mutableStateOf(false) }
    var rootIsSpoiler by remember { mutableStateOf(false) }
    var rootShowPreview by remember { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(false) }
    var rootImageDialog by remember { mutableStateOf(false) }
    var imageUrlInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    fun sortParam() = when (sortBy) { "oldest" -> "oldest"; "best" -> "best"; else -> "new" }

    fun loadComments(page: Int = 1) {
        scope.launch {
            isLoading = true
            val res = commentService.getComments(animeId, episodeNumber, page, sortParam())
            if (res != null) {
                val incoming = res.comments.map { it.toUi() }
                comments = if (page == 1) incoming else {
                    val ids = comments.map { it.data.id }.toSet()
                    comments + incoming.filter { it.data.id !in ids }
                }
                totalComments = res.total_comments
                currentPage = page
                hasMore = res.has_more
            }
            isLoading = false
        }
    }

    LaunchedEffect(animeId, episodeNumber, sortBy) { loadComments(1) }

    // ── Updater helpers ──────────────────────────────────────────────────────
    fun updateCommentInternal(nodes: List<CommentUiModel>, id: String, fn: (CommentUiModel) -> CommentUiModel): List<CommentUiModel> {
        return nodes.map { c ->
            if (c.data.id == id) fn(c) else {
                val newReplies = updateCommentInternal(c.replies, id, fn)
                if (newReplies !== c.replies) c.copy(replies = newReplies) else c
            }
        }
    }
    fun updateComment(id: String, fn: (CommentUiModel) -> CommentUiModel) {
        comments = updateCommentInternal(comments, id, fn)
    }

    fun vote(model: CommentUiModel, type: String) {
        if (!isAuthenticated) return
        val updated = if (type == "like") {
            val was = model.isLiked
            model.copy(
                likes = if (was) model.likes - 1 else model.likes + 1,
                dislikes = if (!was && model.isUnliked) maxOf(0, model.dislikes - 1) else model.dislikes,
                isLiked = !was,
                isUnliked = if (!was) false else model.isUnliked
            )
        } else {
            val was = model.isUnliked
            model.copy(
                dislikes = if (was) maxOf(0, model.dislikes - 1) else model.dislikes + 1,
                likes = if (!was && model.isLiked) maxOf(0, model.likes - 1) else model.likes,
                isUnliked = !was,
                isLiked = if (!was) false else model.isLiked
            )
        }
        updateComment(model.data.id) { updated }
        scope.launch { commentService.toggleLike(model.data.id, type) }
    }

    fun postRoot() {
        println("[CommentsSection] postRoot called: isAuthenticated=$isAuthenticated, rootText.length=${rootText.length}")
        if (!isAuthenticated || rootText.isBlank()) return
        scope.launch {
            isPostingRoot = true
            val res = commentService.postComment(animeId, episodeNumber, rootText)
            println("[CommentsSection] postRoot response: $res")
            if (res?.success == true) {
                val id = res.data?.commentId ?: res.data?.id ?: System.currentTimeMillis().toString()
                println("[CommentsSection] postRoot success! Assigning ID: $id")
                comments = listOf(CommentUiModel(
                    data = Comment(
                        id = id, author = username, authorId = userId, authorPfp = userPfp,
                        content = rootText, isSpoiller = rootIsSpoiler, likes = 0, dislikes = 0
                    )
                )) + comments
                totalComments++
                rootText = ""; rootIsSpoiler = false; rootFocused = false
            } else {
                println("[CommentsSection] postRoot non-success: message=${res?.message}")
            }
            isPostingRoot = false
        }
    }

    fun loadReplies(model: CommentUiModel) {
        updateComment(model.data.id) { it.copy(isLoadingReplies = true) }
        scope.launch {
            val page = model.repliesPage + 1
            val res = commentService.getReplies(animeId, episodeNumber, model.data.id, page)
            if (res != null) {
                val existingIds = model.replies.map { it.data.id }.toSet()
                updateComment(model.data.id) { c ->
                    c.copy(
                        replies = c.replies + res.comments.map { it.toUi() }.filter { it.data.id !in existingIds },
                        repliesPage = page, hasMoreReplies = res.has_more,
                        showReplies = true, isLoadingReplies = false
                    )
                }
            } else {
                updateComment(model.data.id) { it.copy(isLoadingReplies = false) }
            }
        }
    }

    fun postReply(parent: CommentUiModel) {
        if (!isAuthenticated || parent.replyText.isBlank()) return
        updateComment(parent.data.id) { it.copy(isSubmitting = true) }
        scope.launch {
            val res = commentService.postComment(animeId, episodeNumber, parent.replyText, parent.data.id)
            if (res?.success == true) {
                val id = res.data?.commentId ?: res.data?.id ?: System.currentTimeMillis().toString()
                updateComment(parent.data.id) { c ->
                    c.copy(
                        replies = c.replies + CommentUiModel(
                            data = Comment(
                                id = id, author = username, authorId = userId, authorPfp = userPfp,
                                content = parent.replyText, likes = 0, dislikes = 0
                            )
                        ),
                        replyText = "", replyIsSpoiler = false, isReplying = false, showReplies = true, isSubmitting = false
                    )
                }
            } else {
                updateComment(parent.data.id) { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteComment(id: String) {
        // Delete comment endpoint not available in new API
        // Remove locally only
        fun filterRecursive(nodes: List<CommentUiModel>): List<CommentUiModel> {
            return nodes.filter { it.data.id != id }.map {
                val newReps = filterRecursive(it.replies)
                if (newReps !== it.replies) it.copy(replies = newReps) else it
            }
        }
        val oldSize = comments.size
        comments = filterRecursive(comments)
        if (comments.size < oldSize) totalComments = maxOf(0, totalComments - 1)
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    // Image insert dialog (matches Kuudere's AlertDialog)
    if (rootImageDialog) {
        AlertDialog(
            onDismissRequest = { rootImageDialog = false; imageUrlInput = "" },
            title = { Text("Insert Image", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste the URL of the image you want to include in your comment.", color = TextSec, fontSize = 13.sp)
                    BasicTextField(
                        value = imageUrlInput,
                        onValueChange = { imageUrlInput = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(AccentRed),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSub.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .background(BgInput.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .defaultMinSize(minHeight = 44.dp),
                        decorationBox = { inner ->
                            if (imageUrlInput.isEmpty()) Text("https://example.com/image.png", color = TextMuted, fontSize = 13.sp)
                            inner()
                        }
                    )
                    Text("Supports PNG, JPG, GIF, WebP", color = TextMuted, fontSize = 10.sp, letterSpacing = 0.8.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { rootImageDialog = false; imageUrlInput = "" }) {
                    Text("Cancel", color = TextSec, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(AccentRed)
                        .clickable {
                            if (imageUrlInput.isNotBlank()) {
                                rootText += "![image](${imageUrlInput.trim()})"
                            }
                            rootImageDialog = false; imageUrlInput = ""
                        }.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Insert Image", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = BgDark,
            tonalElevation = 0.dp
        )
    }

    Column(Modifier.fillMaxSize().background(BgBlack)) {
        // ── Header with sort tabs ────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(BgBlack)
                .border(0.dp, Color.Transparent) // keeps structure
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COMMENTS", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.8.sp)
            Spacer(Modifier.width(8.dp))
            Text(totalComments.toString(), color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            // Sort Dropdown for compact mobile UI
            var sortMenuExpanded by remember { mutableStateOf(false) }
            val sortLabels = listOf("best" to "Best", "newest" to "Newest", "oldest" to "Oldest")
            val activeLabel = sortLabels.find { it.first == sortBy }?.second ?: "Newest"

            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgCard)
                        .clickable { sortMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(10.dp), color = Color.White, strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(activeLabel, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sort Options", tint = TextMuted, modifier = Modifier.size(14.dp))
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier.background(BgDark).border(1.dp, BorderSub, RoundedCornerShape(8.dp))
                ) {
                    sortLabels.forEach { (key, label) ->
                        val active = sortBy == key
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        label, 
                                        color = if (active) AccentRed else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (active) {
                                        Spacer(Modifier.width(16.dp))
                                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            onClick = {
                                if (sortBy != key) sortBy = key
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
            }
        }
        // Divider
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderLine.copy(alpha = 0.5f)))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Encouraging prompt + comment input ───────────────────────────
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Encouraging message
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(BgCard).padding(12.dp)
                    ) {
                        Text(
                            "If you don't mind, please leave a comment and share your thoughts — it will make the website even more lively! Many people are eager to read your comments! 😊",
                            color = TextSec, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }

                    // Comment count row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$totalComments comments", color = TextMuted, fontSize = 12.sp)
                    }

                    // Input area
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        // Avatar
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(BgCard),
                            contentAlignment = Alignment.Center
                        ) {
                            if (userPfp != null) {
                                AsyncImage(userPfp, null, Modifier.fillMaxSize().clip(CircleShape))
                            } else {
                                Icon(Icons.Default.Person, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }

                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Use InteractionSource to reliably detect focus on the TextField
                            val rootTfInteraction = remember { MutableInteractionSource() }
                            val rootTfFocused by rootTfInteraction.collectIsFocusedAsState()
                            LaunchedEffect(rootTfFocused) { if (rootTfFocused) rootFocused = true }

                            // Textarea
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSub.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .background(BgInput.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                BasicTextField(
                                    value = rootText,
                                    onValueChange = { rootText = it },
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp),
                                    cursorBrush = SolidColor(AccentRed),
                                    interactionSource = rootTfInteraction,
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = if (rootFocused) 80.dp else 38.dp),
                                    decorationBox = { inner ->
                                        if (rootText.isEmpty()) Text("Write your comment...", color = TextMuted, fontSize = 13.sp)
                                        inner()
                                    }
                                )
                            }

                            // Toolbar — only when focused or text is present
                            AnimatedVisibility(
                                visible = rootFocused || rootText.isNotBlank() || isPostingRoot,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // ── Left: formatting icons (B / I / || / Image / Lock / Eye) ──
                                    Row(
                                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FormatIconButton(Icons.Default.FormatBold, false, Color.Transparent) {
                                            rootText = applyMarkdown(rootText, "**")
                                        }
                                        FormatIconButton(Icons.Default.FormatItalic, false, Color.Transparent) {
                                            rootText = applyMarkdown(rootText, "_")
                                        }
                                        FormatIconButton(Icons.Default.VisibilityOff, false, Color.Transparent) {
                                            rootText = applyMarkdown(rootText, "||")
                                        }
                                        FormatIconButton(Icons.Default.Image, false, Color.Transparent) {
                                            rootImageDialog = true
                                        }
                                        FormatIconButton(
                                            icon = if (rootIsSpoiler) Icons.Default.Lock else Icons.Default.LockOpen,
                                            active = rootIsSpoiler, activeColor = AccentRed
                                        ) { rootIsSpoiler = !rootIsSpoiler }
                                        FormatIconButton(
                                            icon = Icons.Default.Visibility,
                                            active = rootShowPreview, activeColor = AccentBlue
                                        ) { rootShowPreview = !rootShowPreview }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // Cancel
                                    Text(
                                        "Cancel", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                                        modifier = Modifier.clickable {
                                            rootFocused = false; rootText = ""; rootIsSpoiler = false; rootShowPreview = false
                                        }.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(if (rootText.isBlank() || isPostingRoot) AccentRed.copy(alpha = 0.4f) else AccentRed)
                                            .clickable(enabled = rootText.isNotBlank() && !isPostingRoot) { postRoot() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPostingRoot) {
                                            CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 1.5.dp)
                                        } else {
                                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // Preview pane (shows rendered text when rootShowPreview)
                            if (rootShowPreview && rootFocused && rootText.isNotBlank()) {
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderSub.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .background(BgInput.copy(alpha = 0.3f))
                                        .padding(12.dp)
                                ) {
                                    CommentContent(content = rootText, isSpoiler = rootIsSpoiler)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(BorderLine.copy(alpha = 0.5f)))
            }

            // ── Loading state ────────────────────────────────────────────────
            if (isLoading && comments.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        // High-Performance Dual-Circle Mini Loader
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                            val infiniteTransition = rememberInfiniteTransition()
                            
                            val rotateCW by infiniteTransition.animateFloat(
                                initialValue = 0f, 
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing)
                                ),
                                label = "OuterRotate"
                            )
                            val rotateCCW by infiniteTransition.animateFloat(
                                initialValue = 360f, 
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing)
                                ),
                                label = "InnerRotate"
                            )

                            // Outer Circle
                            CircularProgressIndicator(
                                progress = { 0.75f },
                                modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = rotateCW },
                                color = Color.White,
                                strokeWidth = 1.5.dp,
                                trackColor = Color.White.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round
                            )

                            // Inner Circle
                            CircularProgressIndicator(
                                progress = { 0.6f },
                                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotateCCW },
                                color = Color.White.copy(alpha = 0.6f),
                                strokeWidth = 1.5.dp,
                                trackColor = Color.White.copy(alpha = 0.05f),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            } else if (comments.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("No comments yet. Be the first to comment!", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                // ── Comment list with space-y-6 ──────────────────────────────
                items(comments, key = { it.data.id }) { model ->
                    Box(Modifier.animateItem().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                        CommentItem(
                            model = model,
                            userId = userId,
                            userPfp = userPfp,
                            depth = 0,
                            onVote = { m, type -> vote(m, type) },
                            onReplyToggle = { replyId ->
                                updateComment(replyId) { c ->
                                    val isOpening = !c.isReplying
                                    val targetAuthor = c.data.author
                                    c.copy(isReplying = isOpening, replyText = if (isOpening && c.data.id != model.data.id) "@$targetAuthor " else "", replyIsSpoiler = false)
                                }
                            },
                            onReplyTextChange = { replyId, text -> updateComment(replyId) { it.copy(replyText = text) } },
                            onReplySpoilerChange = { replyId, isS -> updateComment(replyId) { it.copy(replyIsSpoiler = isS) } },
                            onSubmitReply = { replyModel -> postReply(replyModel) },
                            onToggleReplies = { m ->
                                if (!m.showReplies && m.replies.isEmpty() && m.data.reply_count > 0) loadReplies(m)
                                else updateComment(m.data.id) { it.copy(showReplies = !it.showReplies) }
                            },
                            onLoadMoreReplies = { m -> loadReplies(m) },
                            onDelete = { id -> deleteComment(id) },
                            onDropdownToggle = { id -> updateComment(id) { it.copy(showMoreDropdown = !it.showMoreDropdown) } }
                        )
                    }
                }

                // Load More button
                if (hasMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, BorderSub.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .background(BgCard.copy(alpha = 0.5f))
                                    .clickable(enabled = !isLoading) { loadComments(currentPage + 1) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 1.5.dp)
                                        Text("Loading...", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    }
                                } else {
                                    Text("LOAD MORE", color = TextSec, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                }
                            }
                        }
                    }
                }

                // Skeleton shimmer while paginating
                if (isLoading && comments.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer { alpha = 0.5f },
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(BgCard))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(BgCard))
                                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(BgCard))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Comment Item (recursive) ──────────────────────────────────────────────────

@Composable
private fun ThreadConnectionLayout(
    isLast: Boolean,
    curveOffsetY: androidx.compose.ui.unit.Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(start = 24.dp, top = 8.dp, bottom = 8.dp),
    content: @Composable () -> Unit
) {
    Box(
        Modifier.drawBehind {
            val strokeWidth = 1.dp.toPx()
            val curveY = curveOffsetY.toPx()
            val r = 12.dp.toPx()
            val endY = if (isLast) curveY else size.height + 1f
            
            drawLine(color = BorderLine.copy(alpha = 0.8f), start = Offset(0f, 0f), end = Offset(0f, endY), strokeWidth = strokeWidth)
            
            val path = Path().apply {
                moveTo(0f, curveY - r)
                quadraticBezierTo(0f, curveY, r, curveY)
                lineTo(24.dp.toPx(), curveY)
            }
            drawPath(path, color = BorderLine.copy(alpha = 0.8f), style = Stroke(strokeWidth))
        }
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun CommentItem(
    model: CommentUiModel,
    userId: String?,
    userPfp: String?,
    depth: Int,
    onVote: (CommentUiModel, String) -> Unit,
    onReplyToggle: (String) -> Unit,
    onReplyTextChange: (String, String) -> Unit,
    onReplySpoilerChange: (String, Boolean) -> Unit,
    onSubmitReply: (CommentUiModel) -> Unit,
    onToggleReplies: (CommentUiModel) -> Unit,
    onLoadMoreReplies: (CommentUiModel) -> Unit,
    onDelete: (String) -> Unit,
    onDropdownToggle: (String) -> Unit
) {
    val c = model.data
    val hasThread = model.isReplying || model.replies.isNotEmpty() || c.reply_count > 0
    val isOwnComment = userId != null && (c.authorId == userId)
    val avatarSize = if (depth == 0) 32.dp else 24.dp
    val threadOffset = avatarSize / 2

    Column(Modifier.fillMaxWidth().background(if (c.highlight) Color(0xFF1C1A00) else Color.Transparent)) {
        // ── Parent Comment Content ──
        Row(Modifier.fillMaxWidth().drawBehind {
            if (hasThread) {
                val strokeWidth = 1.dp.toPx()
                val lineX = (avatarSize / 2).toPx()
                val startY = avatarSize.toPx()
                drawLine(color = BorderLine.copy(alpha = 0.8f), start = Offset(lineX, startY), end = Offset(lineX, size.height + 1f), strokeWidth = strokeWidth)
            }
        }.padding(bottom = 4.dp)) {
            // Left column (Avatar + Thread line)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(avatarSize)) {
                Box(Modifier.size(avatarSize).clip(CircleShape).background(BgCard), contentAlignment = Alignment.Center) {
                    if (c.authorPfp != null) AsyncImage(c.authorPfp, null, Modifier.fillMaxSize().clip(CircleShape))
                    else Icon(Icons.Default.Person, null, tint = TextMuted, modifier = Modifier.size(if (depth == 0) 18.dp else 14.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right column (Body)
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.author ?: "Anonymous", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = if (depth == 0) 13.sp else 12.sp, modifier = Modifier.clickable { })
                    
                    if (c.authorVerified) {
                        Icon(Icons.Filled.CheckCircle, null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                    }
                    if (c.authorLabels.contains("admin")) {
                        Icon(AdminCrownIcon, "Admin", tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                    } else if (c.authorLabels.contains("mod")) {
                        Icon(ModShieldIcon, "Moderator", tint = Color(0xFF4CAF50), modifier = Modifier.size(13.dp))
                    }

                    if (c.created_at != null) Text(formatRelTime(c.created_at), color = TextMuted, fontSize = if (depth == 0) 11.sp else 10.sp)
                }

                Spacer(Modifier.height(4.dp))
                CommentContent(c.content, c.isSpoiller)
                Spacer(Modifier.height(6.dp))

                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Like
                    val likeScale by animateFloatAsState(if (model.isLiked) 1.15f else 1f, label = "likeScale")
                    Box(Modifier.scale(likeScale).clip(CircleShape).clickable { onVote(model, "like") }.padding(5.dp)) {
                        Icon(Icons.Outlined.ThumbUp, null, tint = if (model.isLiked) AccentRed else TextMuted, modifier = Modifier.size(13.dp))
                    }
                    if (model.likes > 0) Text(model.likes.toString(), color = if (model.isLiked) AccentRed else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    // Dislike
                    val dislikeScale by animateFloatAsState(if (model.isUnliked) 1.15f else 1f, label = "dislikeScale")
                    Box(Modifier.scale(dislikeScale).clip(CircleShape).clickable { onVote(model, "dislike") }.padding(5.dp)) {
                        Icon(Icons.Outlined.ThumbDown, null, tint = if (model.isUnliked) AccentBlue else TextMuted, modifier = Modifier.size(13.dp))
                    }
                    if (model.dislikes > 0) Text(model.dislikes.toString(), color = if (model.isUnliked) AccentBlue else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Row(Modifier.clickable { onReplyToggle(model.data.id) }.padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Text("Reply", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.weight(1f))

                    Box {
                        Text("••• More", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, modifier = Modifier.clickable { onDropdownToggle(model.data.id) }.padding(4.dp))
                        DropdownMenu(expanded = model.showMoreDropdown, onDismissRequest = { onDropdownToggle(model.data.id) }, modifier = Modifier.background(BgDark).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))) {
                            if (isOwnComment) {
                                DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(13.dp)); Text("Delete Comment", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }, onClick = { onDropdownToggle(model.data.id); onDelete(model.data.id) })
                            } else {
                                DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Flag, null, tint = TextMuted, modifier = Modifier.size(13.dp)); Text("Report", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }, onClick = { onDropdownToggle(model.data.id) })
                            }
                        }
                    }
                }
            }
        }

        if (hasThread) {
            Column(Modifier.fillMaxWidth().padding(start = threadOffset).animateContentSize(animationSpec = tween(300))) {
                
                val hasViewRepliesBtn = c.reply_count > 0 && !model.showReplies
                val hasExpandedReplies = model.showReplies && model.replies.isNotEmpty()
                
                // 1. Reply Editor
                AnimatedVisibility(
                    visible = model.isReplying,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                ) {
                    val isLastForEditor = !hasViewRepliesBtn && !hasExpandedReplies
                    ThreadConnectionLayout(isLast = isLastForEditor) {
                        ReplyEditor(
                            userPfp = userPfp,
                            text = model.replyText,
                            isSubmitting = model.isSubmitting,
                            isSpoiler = model.replyIsSpoiler,
                            onTextChange = { onReplyTextChange(model.data.id, it) },
                            onSpoilerChange = { onReplySpoilerChange(model.data.id, it) },
                            onSubmit = { onSubmitReply(model) },
                            onCancel = { onReplyToggle(model.data.id) }
                        )
                    }
                }

                // 2. Collapsed "View Replies" Block
                AnimatedVisibility(
                    visible = hasViewRepliesBtn,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                ) {
                    ThreadConnectionLayout(
                        isLast = true,
                        curveOffsetY = 14.dp,
                        contentPadding = PaddingValues(start = 24.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Row(Modifier.height(20.dp).clickable { onToggleReplies(model) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(10.dp).height(1.dp).background(TextMuted.copy(alpha = 0.6f)))
                            if (model.isLoadingReplies) CircularProgressIndicator(Modifier.size(10.dp), color = Color.White, strokeWidth = 1.5.dp)
                            else Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSec, modifier = Modifier.size(14.dp))
                            Text("View ${if (c.reply_count == 1) "1 reply" else "${c.reply_count} replies"}", color = TextSec, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 3. Expanded Replies Block
                AnimatedVisibility(
                    visible = hasExpandedReplies,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        model.replies.forEach { reply ->
                            ThreadConnectionLayout(isLast = false) {
                                CommentItem(
                                    model = reply,
                                    userId = userId,
                                    userPfp = userPfp,
                                    depth = depth + 1,
                                    onVote = onVote,
                                    onReplyToggle = onReplyToggle,
                                    onReplyTextChange = onReplyTextChange,
                                    onReplySpoilerChange = onReplySpoilerChange,
                                    onSubmitReply = onSubmitReply,
                                    onToggleReplies = onToggleReplies,
                                    onLoadMoreReplies = onLoadMoreReplies,
                                    onDelete = onDelete,
                                    onDropdownToggle = onDropdownToggle
                                )
                            }
                        }
                        
                        // Hide replies / Load more area
                        // Hide replies / Load more area
                        if (c.reply_count > 0) {
                            ThreadConnectionLayout(
                                isLast = true,
                                curveOffsetY = 14.dp,
                                contentPadding = PaddingValues(start = 24.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(10.dp).height(1.dp).background(TextMuted.copy(alpha = 0.6f)))
                                    Spacer(Modifier.width(6.dp))

                                    if (model.hasMoreReplies) {
                                        Row(Modifier.clickable { onLoadMoreReplies(model) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (model.isLoadingReplies) CircularProgressIndicator(Modifier.size(10.dp), color = Color.White, strokeWidth = 1.5.dp)
                                            else Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSec, modifier = Modifier.size(14.dp))
                                            Text("Show more replies", color = TextSec, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        Row(Modifier.clickable { onToggleReplies(model) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.KeyboardArrowUp, null, tint = TextSec, modifier = Modifier.size(13.dp))
                                            Text("Hide replies", color = TextSec, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Comment Content — spoiler blur ────────────────────────────────────────────

@Composable
private fun CommentContent(content: String, isSpoiler: Boolean) {
    var revealed by remember { mutableStateOf(false) }
    val text = content.replace("\\n", "\n")

    if (isSpoiler && !revealed) {
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BgCard)
                .clickable { revealed = true }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(text, color = Color.Transparent, fontSize = 13.sp, lineHeight = 18.sp)
            Text("Spoiler — tap to reveal", color = TextMuted, fontSize = 12.sp)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val imageRegex = """!\[.*?\]\((.*?)\)""".toRegex()
            val parts = mutableListOf<@Composable () -> Unit>()
            var lastIndex = 0

            for (match in imageRegex.findAll(text)) {
                val textPart = text.substring(lastIndex, match.range.start).trim()
                if (textPart.isNotEmpty()) {
                    parts.add { StyledCommentText(textPart) }
                }
                val url = match.groupValues[1]
                parts.add {
                    AsyncImage(
                        model = url,
                        contentDescription = "User image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BorderSub.copy(alpha = 0.1f))
                    )
                }
                lastIndex = match.range.endInclusive + 1
            }
            val remaining = text.substring(lastIndex).trim()
            if (remaining.isNotEmpty()) {
                parts.add { StyledCommentText(remaining) }
            }

            for (part in parts) {
                part()
            }
        }
    }
}

@Composable
private fun StyledCommentText(text: String) {
    var inlineSpoilersRevealed by remember { mutableStateOf(false) }
    val annotatedTokenRegex = """(\*\*[^*]+\*\*|__[^_]+__|\|\|[^|]+\|\||_[^_]+_)""".toRegex()
    
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        for (match in annotatedTokenRegex.findAll(text)) {
            append(text.substring(lastIndex, match.range.start))
            val token = match.value
            when {
                token.startsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.drop(2).dropLast(2))
                    }
                }
                token.startsWith("__") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.drop(2).dropLast(2))
                    }
                }
                token.startsWith("_") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.drop(1).dropLast(1))
                    }
                }
                token.startsWith("||") -> {
                    val content = token.drop(2).dropLast(2)
                    withStyle(
                        SpanStyle(
                            background = BgCard,
                            // If revealed, show text color, otherwise blend with background
                            color = if (inlineSpoilersRevealed) TextPrimary else Color.Transparent
                        )
                    ) {
                        append(content)
                    }
                }
                else -> append(token)
            }
            lastIndex = match.range.endInclusive + 1
        }
        append(text.substring(lastIndex))
    }

    Text(
        text = annotatedString,
        color = TextPrimary.copy(alpha = 0.9f),
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            inlineSpoilersRevealed = !inlineSpoilersRevealed
        }
    )
}

// ── Reply Editor ──────────────────────────────────────────────────────────────

@Composable
private fun ReplyEditor(
    userPfp: String?,
    text: String,
    isSubmitting: Boolean,
    isSpoiler: Boolean,
    onTextChange: (String) -> Unit,
    onSpoilerChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(BgCard), contentAlignment = Alignment.Center) {
            if (userPfp != null) {
                AsyncImage(userPfp, null, Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Icon(Icons.Default.Person, null, tint = TextMuted, modifier = Modifier.size(12.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var showPreview by remember { mutableStateOf(false) }
            var imageDialog by remember { mutableStateOf(false) }
            var imageUrlInput by remember { mutableStateOf("") }
            
            if (imageDialog) {
                AlertDialog(
                    onDismissRequest = { imageDialog = false; imageUrlInput = "" },
                    title = { Text("Insert Image", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Paste the URL of the image you want to include in your comment.", color = TextSec, fontSize = 13.sp)
                            BasicTextField(
                                value = imageUrlInput,
                                onValueChange = { imageUrlInput = it },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(AccentRed),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSub.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .background(BgInput.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .defaultMinSize(minHeight = 44.dp),
                                decorationBox = { inner ->
                                    if (imageUrlInput.isEmpty()) Text("https://example.com/image.png", color = TextMuted, fontSize = 13.sp)
                                    inner()
                                }
                            )
                            Text("Supports PNG, JPG, GIF, WebP", color = TextMuted, fontSize = 10.sp, letterSpacing = 0.8.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { imageDialog = false; imageUrlInput = "" }) {
                            Text("Cancel", color = TextSec, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    },
                    confirmButton = {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(AccentRed)
                                .clickable {
                                    if (imageUrlInput.isNotBlank()) onTextChange(text + "![image](${imageUrlInput.trim()})")
                                    imageDialog = false; imageUrlInput = ""
                                }.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Insert Image", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = BgDark,
                    tonalElevation = 0.dp
                )
            }

            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, BorderSub.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .background(BgInput.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(AccentRed),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 70.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Write your reply...", color = TextMuted, fontSize = 13.sp)
                        inner()
                    }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FormatIconButton(Icons.Default.FormatBold, false, Color.Transparent) {
                        onTextChange(applyMarkdown(text, "**"))
                    }
                    FormatIconButton(Icons.Default.FormatItalic, false, Color.Transparent) {
                        onTextChange(applyMarkdown(text, "_"))
                    }
                    FormatIconButton(Icons.Default.VisibilityOff, false, Color.Transparent) {
                        onTextChange(applyMarkdown(text, "||"))
                    }
                    FormatIconButton(Icons.Default.Image, false, Color.Transparent) {
                        imageDialog = true
                    }
                    FormatIconButton(
                        icon = if (isSpoiler) Icons.Default.Lock else Icons.Default.LockOpen,
                        active = isSpoiler, activeColor = AccentRed
                    ) { onSpoilerChange(!isSpoiler) }
                    FormatIconButton(
                        icon = Icons.Default.Visibility,
                        active = showPreview, activeColor = AccentBlue
                    ) { showPreview = !showPreview }
                }

                Spacer(Modifier.width(8.dp))
                Text(
                    "Cancel", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    modifier = Modifier.clickable { onCancel() }.padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (text.isBlank() || isSubmitting) AccentRed.copy(alpha = 0.4f) else AccentRed)
                        .clickable(enabled = text.isNotBlank() && !isSubmitting) { onSubmit() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(Modifier.size(13.dp), color = Color.White, strokeWidth = 1.5.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Reply", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
            if (showPreview && text.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BorderSub.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .background(BgInput.copy(alpha = 0.3f))
                        .padding(12.dp)
                ) {
                    CommentContent(content = text, isSpoiler = false)
                }
            }
        }
    }
}

// ── Format toolbar buttons ────────────────────────────────────────────────────

@Composable
private fun FormatButton(label: String, italic: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = TextMuted, fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
        )
    }
}

@Composable
private fun FormatIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(if (active) activeColor.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) activeColor else TextMuted, modifier = Modifier.size(13.dp))
    }
}


// ── Markdown apply helper ─────────────────────────────────────────────────────

/** Wraps the current text with the given marker (appended at end since we don't have cursor pos). */
private fun applyMarkdown(text: String, marker: String): String {
    // If text is empty just insert the markers so user types between them
    if (text.isEmpty()) return "$marker$marker"
    // Otherwise append markers around the whole text or at end
    return "$text$marker$marker"
}

// ── Relative timestamp ────────────────────────────────────────────────────────

fun formatRelTime(isoDate: String): String = try {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    val date = fmt.parse(isoDate.substringBefore(".").replace("Z", "")) ?: return ""
    val s = (System.currentTimeMillis() - date.time) / 1000
    when {
        s < 60      -> "${s}s ago"
        s < 3600    -> "${s / 60}m ago"
        s < 86400   -> "${s / 3600}h ago"
        s < 2592000 -> "${s / 86400}d ago"
        else        -> "${s / 2592000}mo ago"
    }
} catch (e: Exception) { "" }

// ── Custom SVG Badges ────────────────────────────────────────────────────────

val AdminCrownIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "AdminCrown",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes("M 200 800.0v-80h560v80H 200Zm0-140-51-321q-2 0-4.5.5t-4.5.5q-25 0-42.5-17.5T 80 280.0q0-25 17.5-42.5T 140 220.0q25 0 42.5 17.5T 200 280.0q0 7-1.5 13t-3.5 11l125 56 125-171q-11-8-18-21t-7-28q0-25 17.5-42.5T 480 80.0q25 0 42.5 17.5T 540 140.0q0 15-7 28t-18 21l125 171 125-56q-2-5-3.5-11t-1.5-13q0-25 17.5 42.5T 820 220.0q25 0 42.5 17.5T 880 280.0q0 25-17.5 42.5T 820 340.0q-2 0-4.5-.5t-4.5-.5l-51 321H 200Zm68-80h424l26-167-105 46-133-183-133 183-105-46 26 167Zm212 0Z"),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)
        )
    }.build()

val ModShieldIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "ModShield",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes("M 480 880.0q-139-35-229.5-159.5T 160 444.0v-244l320-120 320 120v244q0 152-90.5 276.5T 480 880.0Zm0-84q104-33 172-132t68-220v-189l-240-90-240 90v189q0 121 68 220t172 132Zm0-212q-57 0-97.5-40.5T 342 446.0q0-57 40.5-97.5T 480 308.0q57 0 97.5 40.5T 618 446.0q0 57-40.5 97.5T 480 584.0Z"),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)
        )
    }.build()
