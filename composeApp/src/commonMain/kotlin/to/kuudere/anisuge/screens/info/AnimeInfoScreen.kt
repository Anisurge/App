package to.kuudere.anisuge.screens.info

import to.kuudere.anisuge.utils.formatFloat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.AnimeThemeItem
import to.kuudere.anisuge.data.models.FranchiseEntry
import to.kuudere.anisuge.player.VideoPlayerSurface
import to.kuudere.anisuge.player.rememberVideoPlayerState
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.i18n.resolveDisplayTitle
import to.kuudere.anisuge.ui.WatchlistBottomSheet
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.platform.isAndroidTvPlatform
import to.kuudere.anisuge.ui.tvFocusableClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeInfoScreen(
    animeId: String,
    viewModel: AnimeInfoViewModel,
    onBack: () -> Unit,
    onWatchEpisode: (String, String, Int) -> Unit,
    onDownloadsClick: () -> Unit = {},
    isPremiumUser: Boolean = false,
    onGenreClick: (String) -> Unit = {},
    onAnimeClick: (String) -> Unit = {},
    onExit: () -> Unit = {}
) {
    LaunchedEffect(animeId) {
        viewModel.loadAnimeInfo(animeId)
    }

    val state by viewModel.uiState.collectAsState()
    val preferRomajiAnimeTitles by to.kuudere.anisuge.AppComponent.settingsStore.preferRomajiAnimeTitlesFlow.collectAsState(
        initial = false
    )
    val showFullAnimeTitles by to.kuudere.anisuge.AppComponent.settingsStore.showFullAnimeTitlesFlow.collectAsState(
        initial = false
    )
    var showEpisodes by remember { mutableStateOf(true) }
    var selectedEpisodeForDownload by remember { mutableStateOf<to.kuudere.anisuge.data.models.EpisodeItem?>(null) }
    var batchPickerEpisodes by remember { mutableStateOf<List<to.kuudere.anisuge.data.models.EpisodeItem>>(emptyList()) }
    var batchPreflightMessage by remember { mutableStateOf<String?>(null) }
    var batchPreflightError by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val batchScope = rememberCoroutineScope()

    // Auto-load episodes since it's the default tab
    LaunchedEffect(state.details) {
        if (state.details != null && state.episodes.isEmpty() && !state.isLoadingEpisodes) {
            viewModel.loadEpisodes()
        }
    }

    if (selectedEpisodeForDownload != null && state.details != null) {
        val ep = selectedEpisodeForDownload!!
        val anilistId = state.details!!.anilistId ?: 0
        DownloadEpisodeDialog(
            animeId = state.details!!.id,
            episodeId = ep.id,
            episodeNumber = ep.number,
            anilistId = anilistId,
            estimatedDurationSeconds = estimateDownloadDurationSeconds(ep, state.details!!.duration),
            episodeDisplayTitle = state.details!!.title.displayTitle(preferRomajiAnimeTitles),
            coverImage = state.details!!.image.takeIf { it.isNotBlank() }
                ?: state.details!!.poster.takeIf { it.isNotBlank() }
                ?: state.details!!.cover.takeIf { it.isNotBlank() },
            infoService = to.kuudere.anisuge.AppComponent.infoService,
            serverRepository = to.kuudere.anisuge.AppComponent.serverRepository,
            onDismiss = { selectedEpisodeForDownload = null },
            onStartDownload = { server, subtitleLabels, audioLang, downloadFonts, headers, m3u8Url, preferBatchDub ->
                val title = state.details!!.title.displayTitle(preferRomajiAnimeTitles)
                to.kuudere.anisuge.utils.DownloadManager.startDownload(
                    animeId = state.details!!.id,
                    anilistId = anilistId,
                    episodeNumber = ep.number,
                    title = title,
                    coverImage = state.details!!.bestCoverImage(),
                    server = server,
                    subtitleLabels = subtitleLabels,
                    audioLang = audioLang,
                    downloadFonts = downloadFonts,
                    headers = headers,
                    m3u8Url = m3u8Url,
                    preferBatchDub = preferBatchDub,
                    useParallelSegments = isPremiumUser,
                )
            }
        )
    }

    if (batchPickerEpisodes.isNotEmpty()) {
        SeasonBatchPickerDialog(
            episodes = batchPickerEpisodes,
            onDismiss = { batchPickerEpisodes = emptyList() },
            onConfirm = { episodes, chosenServer, preferDub ->
                batchPickerEpisodes = emptyList()
                val details = state.details ?: return@SeasonBatchPickerDialog
                val anilistId = details.anilistId ?: 0
                val title = details.title.displayTitle(preferRomajiAnimeTitles)
                val cover = details.bestCoverImage()
                batchScope.launch {
                    val batch = episodes
                        .sortedBy { it.number }
                        .take(to.kuudere.anisuge.utils.DownloadManager.MAX_SEASON_BATCH_EPISODES)
                    val audioLabel = if (preferDub) "Dub" else "Sub"
                    val server = preflightBatchDownloadServer(
                        anilistId = anilistId,
                        episodes = batch,
                        preferredServer = chosenServer,
                        preferDub = preferDub,
                        onStatus = { batchPreflightMessage = it },
                        onFailure = { failedServer, failedEpisodes ->
                            batchPreflightMessage =
                                "$failedServer ($audioLabel) failed for ${failedEpisodes.size} episode${if (failedEpisodes.size == 1) "" else "s"}: ${
                                    failedEpisodes.take(8).joinToString(", ") { "Ep ${it.number}" }
                                }. Trying a backup server..."
                        },
                    )
                    if (server == null) {
                        batchPreflightError =
                            "Batch check failed for $audioLabel on $chosenServer and backups. Some selected episodes did not return a usable stream. Try the other audio, fewer episodes, or single download for the failing ones."
                        batchPreflightMessage = null
                        return@launch
                    }
                    to.kuudere.anisuge.utils.DownloadManager.startSeasonBatchDownload(
                        episodes = batch.map { ep ->
                            to.kuudere.anisuge.utils.BatchEpisodeDownload(
                                animeId = details.id,
                                anilistId = anilistId,
                                episodeNumber = ep.number,
                                title = title,
                                coverImage = cover,
                            )
                        },
                        server = server,
                        subtitleLabels = if (preferDub) emptyList() else listOf("English"),
                        audioLang = if (preferDub) "dub" else "sub",
                        downloadFonts = true,
                        headers = null,
                        preferBatchDub = preferDub,
                    )
                    batchPreflightMessage = null
                    onDownloadsClick()
                }
            },
        )
    }

    if (batchPreflightMessage != null || batchPreflightError != null) {
        AlertDialog(
            onDismissRequest = { if (batchPreflightError != null) batchPreflightError = null },
            containerColor = AppColors.surface,
            title = {
                Text(
                    if (batchPreflightError == null) "Checking episodes" else "Batch check failed",
                    color = AppColors.text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (batchPreflightError == null) {
                        CircularProgressIndicator(
                            color = AppColors.text,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        batchPreflightError ?: batchPreflightMessage.orEmpty(),
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                if (batchPreflightError != null) {
                    Button(
                        onClick = { batchPreflightError = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.accent,
                            contentColor = AppColors.onAccent
                        ),
                    ) {
                        Text("OK")
                    }
                }
            },
        )
    }

    fun openBatchPicker(episodes: List<to.kuudere.anisuge.data.models.EpisodeItem>) {
        val sorted = episodes.sortedBy { it.number }
        if (sorted.isEmpty()) return
        batchPickerEpisodes = sorted
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.background)) {
        to.kuudere.anisuge.platform.DraggableWindowArea(
            modifier = Modifier.fillMaxWidth().height(84.dp).align(Alignment.TopStart)
        ) { }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.accent, strokeWidth = 3.dp)
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Unknown error", color = AppColors.text)
            }
        } else if (state.details != null) {
            val anime = state.details!!
            val shareAnime = {
                to.kuudere.anisuge.platform.shareText(
                    text = buildAnimeShareText(anime, animeId, preferRomajiAnimeTitles),
                    title = anime.title.displayTitle(preferRomajiAnimeTitles),
                )
                Unit
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isDesktop = maxWidth >= 1024.dp
                if (isAndroidTvPlatform) {
                    TvLayout(
                        anime = anime,
                        state = state,
                        onBack = onBack,
                        onWatchlistUpdate = { viewModel.updateWatchlist(it) },
                        onWatchNow = { onWatchEpisode(anime.activeSlug, "sub", 1) },
                        onWatchEpisode = { epNum -> onWatchEpisode(anime.activeSlug, "sub", epNum) },
                        onPosterClick = { previewImageUrl = it },
                        showFullAnimeTitles = showFullAnimeTitles,
                    )
                } else if (isDesktop) {
                    DesktopLayout(
                        anime = anime,
                        state = state,
                        onWatchlistUpdate = { viewModel.updateWatchlist(it) },
                        onWatchNow = { onWatchEpisode(anime.activeSlug, "sub", 1) },
                        onWatchEpisode = { epNum -> onWatchEpisode(anime.activeSlug, "sub", epNum) },
                        onGenreClick = onGenreClick,
                        onDownloadEpisode = { selectedEpisodeForDownload = it },
                        onDownloadSeason = { episodes -> openBatchPicker(episodes) },
                        isPremiumUser = isPremiumUser,
                        onShareAnime = shareAnime,
                        onDownloadsClick = onDownloadsClick,
                        onAnimeClick = onAnimeClick,
                        onExit = onExit,
                        onBack = onBack,
                        onPosterClick = { previewImageUrl = it },
                        showFullAnimeTitles = showFullAnimeTitles,
                    )
                } else {
                    MobileLayout(
                        anime = anime,
                        state = state,
                        showEpisodes = showEpisodes,
                        onToggleEpisodes = {
                            showEpisodes = it
                            if (it && state.episodes.isEmpty()) {
                                viewModel.loadEpisodes()
                            }
                        },
                        onBack = onBack,
                        onWatchlistUpdate = { viewModel.updateWatchlist(it) },
                        onWatchNow = { onWatchEpisode(anime.activeSlug, "sub", 1) },
                        onWatchEpisode = { epNum -> onWatchEpisode(anime.activeSlug, "sub", epNum) },
                        onGenreClick = onGenreClick,
                        onDownloadEpisode = { selectedEpisodeForDownload = it },
                        onDownloadSeason = { episodes -> openBatchPicker(episodes) },
                        isPremiumUser = isPremiumUser,
                        onShareAnime = shareAnime,
                        onDownloadsClick = onDownloadsClick,
                        onAnimeClick = onAnimeClick,
                        onPosterClick = { previewImageUrl = it },
                        showFullAnimeTitles = showFullAnimeTitles,
                    )
                }

                // Fullscreen image preview overlay
                previewImageUrl?.let { url ->
                    FullScreenImagePreview(
                        imageUrl = url,
                        animeTitle = anime.resolveDisplayTitle(),
                        onDismiss = { previewImageUrl = null },
                    )
                }
            }
        }
    }
}

private fun stripHtmlTags(htmlContent: String): String {
    return htmlContent.replace(Regex("<.*?>"), "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
        .replace("<br>", "\n").replace("<br/>", "\n")
}

private fun buildAnimeShareText(
    anime: AnimeDetails,
    routeAnimeId: String,
    preferRomajiAnimeTitles: Boolean,
): String {
    val id = anime.animeId.ifBlank { routeAnimeId }
    val title = anime.title.displayTitle(preferRomajiAnimeTitles).ifBlank { "this anime" }
    return "$title\nWatch this on Anisurge:\nhttps://www.anisurge.lol/anime/$id"
}

private fun AnimeDetails.bestCoverImage(): String =
    image.takeIf { it.isNotBlank() }
        ?: poster.takeIf { it.isNotBlank() }
        ?: cover

private data class RelatedSeasonItem(
    val animeId: String,
    val title: String,
    val coverUrl: String?,
    val relationType: String?,
    val format: String?,
    val seasonYear: Int?,
    val isCurrent: Boolean,
)

private fun FranchiseEntry.toRelatedSeasonItem(): RelatedSeasonItem = RelatedSeasonItem(
    animeId = animeId,
    title = title,
    coverUrl = coverUrl,
    relationType = relationType,
    format = format,
    seasonYear = startDate?.year?.takeIf { it > 0 },
    isCurrent = isCurrent,
)

private fun AnimeDetails.relatedSeasonItems(): List<RelatedSeasonItem> {
    val currentId = animeId.ifBlank { id }
    return relations.orEmpty()
        .mapNotNull { relation -> relation.toRelatedSeasonItem(currentId) }
        .distinctBy { it.animeId }
        .sortedWith(
            compareBy<RelatedSeasonItem> { it.seasonYear ?: Int.MAX_VALUE }
                .thenBy { relationOrderRank(it.relationType, it.isCurrent) }
                .thenBy { it.title.lowercase() }
        )
}

private fun relationOrderRank(relationType: String?, isCurrent: Boolean): Int {
    if (isCurrent) return 10
    return when (relationType?.uppercase()) {
        "PREQUEL" -> 0
        "PARENT", "SOURCE" -> 1
        "SEQUEL" -> 20
        else -> 15
    }
}

private fun JsonObject.toRelatedSeasonItem(currentAnimeId: String): RelatedSeasonItem? {
    val node = this["node"] as? JsonObject ?: this
    val animeId = node.stringValue("anime_id")
        ?: node.stringValue("id")
        ?: this.stringValue("anime_id")
        ?: this.stringValue("id")
        ?: return null
    val titleObj = node["title"] as? JsonObject
    val title = titleObj?.stringValue("english")
        ?: titleObj?.stringValue("romaji")
        ?: titleObj?.stringValue("user_preferred")
        ?: titleObj?.stringValue("native")
        ?: node.stringValue("title")
        ?: animeId
    val coverObj = node["cover_image"] as? JsonObject
    val coverUrl = coverObj?.stringValue("extra_large")
        ?: coverObj?.stringValue("large")
        ?: coverObj?.stringValue("medium")
    val relationType = this.stringValue("relation_type")
        ?: this.stringValue("relationType")
        ?: this.stringValue("type")
        ?: node.stringValue("relation_type")
        ?: node.stringValue("relationType")
    return RelatedSeasonItem(
        animeId = animeId,
        title = title,
        coverUrl = coverUrl,
        relationType = relationType?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() },
        format = node.stringValue("format"),
        seasonYear = node.intValue("season_year") ?: this.intValue("season_year"),
        isCurrent = animeId == currentAnimeId,
    )
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.intValue(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

@Composable
private fun FranchiseOrderSection(
    state: AnimeInfoUiState,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.franchiseOrder.map { it.toRelatedSeasonItem() }
    if (items.isEmpty() && !state.isLoadingFranchise) return

    Column(modifier) {
        Text(
            text = "Franchise Order",
            color = AppColors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        if (state.isLoadingFranchise && items.size <= 1) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.accent,
                trackColor = AppColors.surface,
            )
            return@Column
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { it.animeId }) { item ->
                RelatedSeasonCard(
                    item = item,
                    onClick = { if (!item.isCurrent) onAnimeClick(item.animeId) },
                )
            }
        }
    }
}

@Composable
private fun AnimeThemesSection(
    state: AnimeInfoUiState,
    modifier: Modifier = Modifier,
) {
    var selectedTheme by remember { mutableStateOf<AnimeThemeItem?>(null) }
    val grouped = state.themes.groupBy { it.type.uppercase() }
    if (grouped.isEmpty() && !state.isLoadingThemes) return

    Column(modifier) {
        Text(
            text = "Openings & Endings",
            color = AppColors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        if (state.isLoadingThemes) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.accent,
                trackColor = AppColors.surface,
            )
        } else {
            listOf("OP", "ED", "IN").forEach { type ->
                val themes = grouped[type].orEmpty()
                if (themes.isEmpty()) return@forEach
                Text(
                    text = themes.first().displayType + "s",
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(themes, key = { it.id }) { theme ->
                        ThemeCard(theme = theme, onClick = { selectedTheme = theme })
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    selectedTheme?.let { theme ->
        ThemePreviewDialog(theme = theme, onDismiss = { selectedTheme = null })
    }
}

@Composable
private fun ThemeCard(theme: AnimeThemeItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppColors.accent)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${theme.displayType} ${theme.sequence ?: ""}".trim(),
                color = AppColors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.songTitle,
            color = AppColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (theme.artists.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = theme.artists.joinToString(),
                color = AppColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (theme.spoiler || theme.nsfw) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = listOfNotNull(
                    "Spoiler".takeIf { theme.spoiler },
                    "Sensitive".takeIf { theme.nsfw },
                ).joinToString(" • "),
                color = Color(0xFFFFC857),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ThemePreviewDialog(theme: AnimeThemeItem, onDismiss: () -> Unit) {
    val url = theme.playableUrl ?: return
    val playerState = rememberVideoPlayerState(
        url = url,
        mediaKey = theme.id,
        showControls = true,
        enableSubs = false,
        autoPlay = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.background)
                .border(1.dp, AppColors.border, RoundedCornerShape(8.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                VideoPlayerSurface(state = playerState, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text(theme.songTitle, color = AppColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (theme.artists.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(theme.artists.joinToString(), color = AppColors.textMuted, fontSize = 13.sp)
                }
                val warning = listOfNotNull(
                    "May contain spoilers".takeIf { theme.spoiler },
                    "Sensitive content".takeIf { theme.nsfw },
                    theme.resolution?.let { "${it}p" },
                ).joinToString(" • ")
                if (warning.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(warning, color = Color(0xFFFFC857), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RelatedSeasonCard(
    item: RelatedSeasonItem,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(238.dp)
            .height(112.dp)
            .clip(shape)
            .background(AppColors.surface)
            .border(
                width = if (item.isCurrent) 1.5.dp else 1.dp,
                color = if (item.isCurrent) AppColors.accent else AppColors.border,
                shape = shape,
            )
            .then(if (item.isCurrent) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        AsyncImage(
            model = item.coverUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.05f),
                        0.45f to Color.Black.copy(alpha = 0.18f),
                        1.0f to Color.Black.copy(alpha = 0.82f),
                    )
                )
        )

        val meta = listOfNotNull(item.relationType, item.seasonYear?.toString(), item.format)
            .distinct()
            .joinToString(" • ")

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .blur(0.5.dp)
                .background(Color.Black.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (item.isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.accent)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "Current",
                    color = AppColors.onAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Recommended batch-download servers in priority order: Anikage, Anitaku, AniDB, Miruro.
 * The user's pick is always tried first; these are the fallbacks if it fails.
 */
private val BATCH_RECOMMENDED_SERVERS = listOf("anikage", "anitaku-1", "anitaku", "anidb", "miruro")

private suspend fun preflightBatchDownloadServer(
    anilistId: Int,
    episodes: List<to.kuudere.anisuge.data.models.EpisodeItem>,
    preferredServer: String,
    preferDub: Boolean,
    onStatus: (String) -> Unit,
    onFailure: (server: String, failedEpisodes: List<to.kuudere.anisuge.data.models.EpisodeItem>) -> Unit,
): String? {
    // User's choice first, then the recommended fallbacks in order, de-duplicated.
    val candidates = (listOf(preferredServer) + BATCH_RECOMMENDED_SERVERS)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
    val audioLabel = if (preferDub) "Dub" else "Sub"
    for (server in candidates) {
        onStatus("Checking ${episodes.size} episode${if (episodes.size == 1) "" else "s"} ($audioLabel) on $server...")
        val failed = coroutineScope {
            episodes.map { episode ->
                async {
                    val usable = runCatching {
                        val response = to.kuudere.anisuge.AppComponent.infoService
                            .getVideoStream(anilistId, episode.number, server)
                        val subOk = response?.sub?.streams?.any { !it.url.isNullOrBlank() } == true
                        val dubOk = response?.dub?.streams?.any { !it.url.isNullOrBlank() } == true
                        // Try to use the requested audio; fall back to the other if needed
                        if (preferDub) dubOk || subOk else subOk || dubOk
                    }.getOrDefault(false)
                    if (usable) null else episode
                }
            }.awaitAll().filterNotNull()
        }
        if (failed.isEmpty()) {
            onStatus("$server ($audioLabel) is ready. Queueing batch...")
            return server
        }
        onFailure(server, failed)
    }
    return null
}

@Composable
private fun TvLayout(
    anime: AnimeDetails,
    state: AnimeInfoUiState,
    onBack: () -> Unit,
    onWatchlistUpdate: (String) -> Unit,
    onWatchNow: () -> Unit,
    onWatchEpisode: (Int) -> Unit,
    onPosterClick: (String) -> Unit = {},
    showFullAnimeTitles: Boolean = false,
) {
    val episodesState = rememberLazyListState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(horizontal = 34.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        // Left: anime card + info (static)
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.surface)
                        .tvFocusableClick(shape = RoundedCornerShape(14.dp), onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.text)
                        Text("Back", color = AppColors.text, fontWeight = FontWeight.SemiBold)
                    }
                }

                Box {
                    var showSheet by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.surface)
                            .tvFocusableClick(shape = RoundedCornerShape(14.dp), onClick = { showSheet = true })
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (state.inWatchlist) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = "Watchlist",
                                tint = AppColors.text,
                            )
                            Text(
                                if (state.inWatchlist) "In Watchlist" else "Add Watchlist",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (showSheet) {
                        WatchlistBottomSheet(
                            currentFolder = state.folder,
                            onSelect = { option ->
                                showSheet = false
                                onWatchlistUpdate(option)
                            },
                            onDismiss = { showSheet = false },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                val coverImage = anime.bestCoverImage()
                AsyncImage(
                    model = coverImage,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(240.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, AppColors.border, RoundedCornerShape(18.dp))
                        .clickable { onPosterClick(coverImage) },
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    var titleExpanded by remember { mutableStateOf(false) }
                    val showExpandedTitle = showFullAnimeTitles || titleExpanded
                    Text(
                        text = anime.resolveDisplayTitle(),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 38.sp,
                        maxLines = if (showExpandedTitle) Int.MAX_VALUE else 2,
                        overflow = if (showExpandedTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { titleExpanded = !titleExpanded }
                    )

                    val seasonStr = anime.season?.lowercase()?.replaceFirstChar { it.uppercase() }
                    val seasonLabel = if (seasonStr != null && anime.seasonYear != null) {
                        "$seasonStr ${anime.seasonYear}"
                    } else null
                    val formatLabel = anime.format.takeIf { it.isNotBlank() }
                    val meta = buildList {
                        seasonLabel?.let { add(it) }
                        formatLabel?.let { add(it) }
                        anime.status.takeIf { it.isNotBlank() }?.let { add(it) }
                        anime.type?.takeIf { it.isNotBlank() }?.let { add(it) }
                        anime.duration?.takeIf { it > 0 }?.let { add("${it}m") }
                    }.joinToString(" • ")

                    if (meta.isNotBlank()) {
                        Text(meta, color = AppColors.textMuted, fontSize = 15.sp)
                    }

                    val genres = anime.genres.take(3).joinToString(" • ")
                    if (genres.isNotBlank()) {
                        Text(
                            genres,
                            color = AppColors.textMuted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val desc = stripHtmlTags(anime.description).trim()
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            color = AppColors.text,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val watchText =
                        if (anime.watchProgress?.episode != null) "Continue • EP ${anime.watchProgress.episode}" else "Watch Now"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(AppColors.accent)
                            .tvFocusableClick(
                                shape = RoundedCornerShape(18.dp),
                                onClick = {
                                    val ep = anime.watchProgress?.episode
                                    if (ep != null) onWatchEpisode(ep) else onWatchNow()
                                },
                            )
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppColors.onAccent)
                            Text(watchText, color = AppColors.onAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    watchedProgressSummary(anime)?.let { summary ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = summary,
                            color = AppColors.textMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }

        // Right: episode list (only scrollable section)
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.surface)
                .border(1.dp, AppColors.border, RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Episodes",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            if (state.isLoadingEpisodes) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.accent, strokeWidth = 3.dp)
                }
            } else if (state.episodes.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text("No episodes", color = AppColors.textMuted)
                }
            } else {
                LazyColumn(
                    state = episodesState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 10.dp),
                ) {
                    items(state.episodes.sortedBy { it.number }) { ep ->
                        val title = ep.title ?: ep.titles?.filterNotNull()?.firstOrNull()
                        val watchedEp = anime.watchProgress?.episode
                        val progressSecs = anime.watchProgress?.currentTime
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppColors.surface)
                                .tvFocusableClick(
                                    shape = RoundedCornerShape(14.dp),
                                    onClick = { onWatchEpisode(ep.number) })
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "EP ${ep.number}",
                                            color = AppColors.text,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        )
                                        when {
                                            watchedEp != null && ep.number < watchedEp -> {
                                                ProgressBadge("WATCHED")
                                            }

                                            watchedEp != null && ep.number == watchedEp -> {
                                                ProgressBadge(
                                                    if ((progressSecs ?: 0.0) > 0.0) "IN PROGRESS" else "LAST WATCHED",
                                                )
                                            }
                                        }
                                    }
                                    if (!title.isNullOrBlank()) {
                                        Text(
                                            text = title,
                                            color = AppColors.textMuted,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f)
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
private fun MobileLayout(
    anime: AnimeDetails,
    state: AnimeInfoUiState,
    showEpisodes: Boolean,
    onToggleEpisodes: (Boolean) -> Unit,
    onBack: () -> Unit,
    onWatchlistUpdate: (String) -> Unit,
    onWatchNow: () -> Unit,
    onWatchEpisode: (Int) -> Unit,
    onGenreClick: (String) -> Unit,
    onDownloadEpisode: (to.kuudere.anisuge.data.models.EpisodeItem) -> Unit,
    onDownloadSeason: (List<to.kuudere.anisuge.data.models.EpisodeItem>) -> Unit,
    isPremiumUser: Boolean,
    onShareAnime: () -> Unit,
    onDownloadsClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onPosterClick: (String) -> Unit = {},
    showFullAnimeTitles: Boolean = false,
) {
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize().background(AppColors.background)) {
        Box(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // Header Background (Image + Gradient) — stays under but is not in the flow
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                // Blur Background — use cover as fallback with stronger blur
                val bannerUrl = anime.banner?.takeIf {
                    it.isNotBlank() && it != "null" && !it.contains("placeholder") && it.startsWith("http")
                }
                val bgImage = bannerUrl ?: anime.bestCoverImage()
                val hasBanner = bannerUrl != null
                AsyncImage(
                    model = bgImage,
                    contentDescription = "Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .blur(if (hasBanner) 16.dp else 48.dp)
                        .alpha(if (hasBanner) 0.6f else 0.75f)
                )
                // Background Gradient
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.0f),
                                0.4f to Color.Black.copy(alpha = 0.4f),
                                1.0f to Color.Black
                            )
                        )
                )
            }

            // Foreground content (starts partially overlapping the header)
            Column(Modifier.fillMaxWidth()) {
                // Top Bar for Mobile positioned over the image
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.text)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShareAnime) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                        IconButton(onClick = onDownloadsClick) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                        }
                        WatchlistDropdownIcon(
                            state = state,
                            onUpdate = onWatchlistUpdate
                        )
                    }
                }

                Spacer(Modifier.height(50.dp)) // Added space so the details start at ~100dp from top (150dp overlap)

                Column {

                    // Top details block
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                        // Poster
                        val coverImage = anime.bestCoverImage()
                        AsyncImage(
                            model = coverImage,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(130.dp)
                                .height(190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPosterClick(coverImage) }
                        )
                        Spacer(Modifier.width(16.dp))
                        // Right Details
                        Column(Modifier.weight(1f)) {
                            // Title
                            var titleExpanded by remember { mutableStateOf(false) }
                            val showExpandedTitle = showFullAnimeTitles || titleExpanded
                            Text(
                                text = anime.resolveDisplayTitle(),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = if (showExpandedTitle) Int.MAX_VALUE else 2,
                                overflow = if (showExpandedTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { titleExpanded = !titleExpanded }
                            )
                            Spacer(Modifier.height(8.dp))
                            // Stars
                            val ratingValue = ((anime.score ?: 0)) / 20.0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { i ->
                                    if (i < ratingValue.toInt()) {
                                        Icon(
                                            Icons.Default.Star,
                                            null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.StarBorder,
                                            null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (ratingValue > 0.0) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        formatFloat(ratingValue * 2.0, 1),
                                        color = AppColors.text,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // Season & Format info
                            val seasonStr = anime.season?.lowercase()?.replaceFirstChar { it.uppercase() }
                            val seasonLabel = if (seasonStr != null && anime.seasonYear != null) {
                                "$seasonStr ${anime.seasonYear}"
                            } else null
                            val formatLabel = anime.format.takeIf { it.isNotBlank() }
                            val infoParts = listOfNotNull(seasonLabel, formatLabel)
                            if (infoParts.isNotEmpty()) {
                                Text(
                                    text = infoParts.joinToString(" • "),
                                    color = AppColors.textMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            // Status - Genres
                            Text(
                                text = "${anime.status} • ${anime.genres.take(2).joinToString(" / ")}",
                                color = AppColors.textMuted,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(16.dp))

                            // Play Movie button
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppColors.surface)
                                    .clickable {
                                        if (anime.watchProgress != null && anime.watchProgress.episode != null) {
                                            onWatchEpisode(anime.watchProgress.episode)
                                        } else {
                                            onWatchNow()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        null,
                                        tint = AppColors.text,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val watchText =
                                        if (anime.watchProgress != null && anime.watchProgress.episode != null) {
                                            "Continue - EP ${anime.watchProgress.episode}"
                                        } else {
                                            "Watch Now"
                                        }
                                    Text(
                                        watchText,
                                        color = AppColors.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            watchedProgressSummary(anime)?.let { summary ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = summary,
                                    color = AppColors.textMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Storyline
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        var isExpanded by remember { mutableStateOf(false) }

                        Text("Storyline", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Normal)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stripHtmlTags(anime.description),
                            color = AppColors.textMuted,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { isExpanded = !isExpanded }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    FranchiseOrderSection(
                        state = state,
                        onAnimeClick = onAnimeClick,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(18.dp))
                    AnimeThemesSection(state = state, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(18.dp))

                    // Custom Tabs
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            "Episodes",
                            color = if (showEpisodes) AppColors.text else AppColors.textMuted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.clickable { onToggleEpisodes(true) }
                        )
                        Text(
                            "Details",
                            color = if (!showEpisodes) AppColors.text else AppColors.textMuted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.clickable { onToggleEpisodes(false) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (showEpisodes) {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            EpisodeListSection(
                                state = state,
                                onWatchEpisode = onWatchEpisode,
                                onDownloadEpisode = onDownloadEpisode,
                                onDownloadSeason = onDownloadSeason,
                                isPremiumUser = isPremiumUser,
                                watchedEpisode = anime.watchProgress?.episode,
                                currentProgressSeconds = anime.watchProgress?.currentTime,
                            )
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            if (!anime.genres.isNullOrEmpty()) {
                                Text("Genres", color = AppColors.text, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    anime.genres.forEach { genre ->
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AppColors.surface)
                                                .clickable { onGenreClick(genre) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(genre, color = AppColors.text, fontSize = 13.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            if (!anime.studios.isNullOrEmpty()) {
                                Text("Studios", color = AppColors.text, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    anime.studios.forEach { studioObj ->
                                        val studioName = studioObj["name"]?.toString()?.trim('"') ?: ""
                                        if (studioName.isNotBlank()) {
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(AppColors.surface)
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    studioName,
                                                    color = AppColors.text,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            if (!anime.tags.isNullOrEmpty()) {
                                Text("Tags", color = AppColors.text, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    anime.tags.forEach { tagObj ->
                                        val tagName = tagObj["name"]?.toString()?.trim('"') ?: ""
                                        if (tagName.isNotBlank()) {
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(AppColors.surface)
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(tagName, color = AppColors.textMuted, fontSize = 13.sp)
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
}

@Composable
private fun DesktopLayout(
    anime: AnimeDetails,
    state: AnimeInfoUiState,
    onWatchlistUpdate: (String) -> Unit,
    onWatchNow: () -> Unit,
    onWatchEpisode: (Int) -> Unit,
    onGenreClick: (String) -> Unit,
    onDownloadEpisode: (to.kuudere.anisuge.data.models.EpisodeItem) -> Unit,
    onDownloadSeason: (List<to.kuudere.anisuge.data.models.EpisodeItem>) -> Unit,
    isPremiumUser: Boolean,
    onShareAnime: () -> Unit,
    onDownloadsClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onExit: () -> Unit,
    onBack: () -> Unit,
    onPosterClick: (String) -> Unit = {},
    showFullAnimeTitles: Boolean = false,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val baseWidth = 1400.dp
        val scaleFactor = (maxWidth / baseWidth).coerceAtLeast(1f)
        val currentDensity = androidx.compose.ui.platform.LocalDensity.current
        val scaledDensity =
            androidx.compose.ui.unit.Density(currentDensity.density * scaleFactor, currentDensity.fontScale)

        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaledDensity) {
            val scrollState = rememberScrollState()

            Column(Modifier.fillMaxSize().background(AppColors.background).verticalScroll(scrollState)) {
                // Hero Section Box
                Box(Modifier.fillMaxWidth().height(500.dp)) {
                    val bannerUrl = anime.banner?.takeIf {
                        it.isNotBlank() && it != "null" && !it.contains("placeholder") && it.startsWith("http")
                    }
                    val bgImage = bannerUrl ?: anime.bestCoverImage()
                    val hasBanner = bannerUrl != null

                    AsyncImage(
                        model = bgImage,
                        contentDescription = "Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(if (hasBanner) 16.dp else 48.dp)
                            .alpha(if (hasBanner) 0.6f else 0.75f),
                        alignment = Alignment.TopCenter
                    )

                    // Replicate Nuvio gradient layers exactly
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0.0f to Color.Black,
                                0.22f to Color.Black.copy(alpha = 0.84f),
                                0.52f to Color.Black.copy(alpha = 0.34f),
                                0.78f to Color.Black.copy(alpha = 0.07f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.38f to Color.Transparent,
                                0.60f to Color.Black.copy(alpha = 0.38f),
                                0.91f to Color.Black.copy(alpha = 0.91f),
                                1.0f to Color.Black
                            )
                        )
                    )

                    // Window management buttons and Back button - inside scrollable Column
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .align(Alignment.TopStart)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = AppColors.text
                            )
                        }

                        to.kuudere.anisuge.platform.WindowManagementButtons(
                            onClose = onExit,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }

                    // Constrained inner content
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 1400.dp)
                                .fillMaxWidth()
                                .padding(start = 48.dp, bottom = 48.dp, end = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left aligned content matching Nuvio TV HeroSection
                            Column(
                                modifier = Modifier.fillMaxWidth(0.6f)
                            ) {
                                var titleExpanded by remember { mutableStateOf(false) }
                                val showExpandedTitle = showFullAnimeTitles || titleExpanded
                                Text(
                                    text = anime.resolveDisplayTitle(),
                                    color = Color.White,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 52.sp,
                                    maxLines = if (showExpandedTitle) Int.MAX_VALUE else 2,
                                    overflow = if (showExpandedTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { titleExpanded = !titleExpanded }
                                )

                                Spacer(Modifier.height(24.dp))

                                // "For a chance..." Text
                                Text(
                                    text = stripHtmlTags(anime.description),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 17.sp,
                                    lineHeight = 26.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(24.dp))

                                // Meta Info Row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Genres
                                    val genresText = anime.genres.take(3).joinToString(" • ")
                                    if (genresText.isNotEmpty()) {
                                        Text(genresText, color = AppColors.text, fontSize = 15.sp)
                                        Text("•", color = AppColors.textMuted, fontSize = 15.sp)
                                    }

                                    // Duration
                                    val duration = anime.duration
                                    if (duration != null && duration > 0) {
                                        Text(
                                            "${duration}m",
                                            color = AppColors.text,
                                            fontSize = 15.sp
                                        )
                                        Text("•", color = AppColors.textMuted, fontSize = 15.sp)
                                    }

                                    // Season & Year
                                    val seasonStr = anime.season?.lowercase()?.replaceFirstChar { it.uppercase() }
                                    val year = anime.year
                                    val seasonLabel = if (seasonStr != null && anime.seasonYear != null) {
                                        "$seasonStr ${anime.seasonYear}"
                                    } else if (year != null && year > 0) {
                                        "$year"
                                    } else null
                                    if (seasonLabel != null) {
                                        Text(seasonLabel, color = AppColors.text, fontSize = 15.sp)
                                        Text("•", color = AppColors.textMuted, fontSize = 15.sp)
                                    }

                                    // Rating
                                    if (anime.malScore != null && anime.malScore > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                Modifier.background(Color(0xFFE6B91E), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    "MAL",
                                                    color = Color.Black,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                anime.malScore.toString(),
                                                color = AppColors.text,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Text("•", color = AppColors.textMuted, fontSize = 15.sp)
                                    }

                                    // Anisurge Score
                                    val score = anime.score
                                    if (score != null && score > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                Modifier.background(Color(0xFF6C5CE7), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    "★",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                score.toString(),
                                                color = AppColors.text,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Text("•", color = AppColors.textMuted, fontSize = 15.sp)
                                    }

                                    // Language/Format
                                    if (anime.status.isNotBlank()) {
                                        Text(
                                            anime.status.uppercase(),
                                            color = AppColors.textMuted,
                                            fontSize = 14.sp
                                        )
                                        Text("•", color = AppColors.textMuted, fontSize = 15.sp)
                                    }
                                    if (anime.type != null) {
                                        Text(
                                            anime.type.uppercase(),
                                            color = AppColors.textMuted,
                                            fontSize = 14.sp
                                        )
                                        Text("•", color = AppColors.textMuted, fontSize = 15.sp)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("🇯🇵", fontSize = 14.sp)
                                        Text("JP", color = AppColors.textMuted, fontSize = 14.sp)
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                // Buttons (Watch Now, Watchlist)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (anime.watchProgress != null && anime.watchProgress.episode != null) {
                                                onWatchEpisode(anime.watchProgress.episode)
                                            } else {
                                                onWatchNow()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AppColors.surface,
                                            contentColor = AppColors.text
                                        ),
                                        shape = RoundedCornerShape(32.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                            val watchText =
                                                if (anime.watchProgress != null && anime.watchProgress.episode != null) {
                                                    "CONTINUE - EP ${anime.watchProgress.episode}"
                                                } else {
                                                    "WATCH NOW"
                                                }
                                            Text(watchText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Box {
                                        var showSheet by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(if (state.inWatchlist) AppColors.accent else AppColors.surface)
                                                .border(
                                                    2.dp,
                                                    if (state.inWatchlist) Color.Transparent else Color.Transparent,
                                                    CircleShape
                                                )
                                                .clickable { showSheet = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (state.isUpdatingWatchlist) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = if (state.inWatchlist) AppColors.onAccent else AppColors.text,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    if (state.inWatchlist) Icons.Default.Check else Icons.Default.Add,
                                                    contentDescription = "Watchlist",
                                                    tint = if (state.inWatchlist) AppColors.onAccent else AppColors.text,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        if (showSheet) {
                                            WatchlistBottomSheet(
                                                currentFolder = state.folder,
                                                onSelect = { option ->
                                                    showSheet = false
                                                    onWatchlistUpdate(option)
                                                },
                                                onDismiss = { showSheet = false }
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(AppColors.surface)
                                            .clickable(onClick = onShareAnime),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = AppColors.text,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }

                                watchedProgressSummary(anime)?.let { summary ->
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = summary,
                                        color = AppColors.text,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                    )
                                }
                            } // End Column

                            // Cover Image on the right side
                            val coverImage = anime.bestCoverImage()
                            AsyncImage(
                                model = coverImage,
                                contentDescription = "Cover Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(180.dp) // Adjusted slightly for optimal fit
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    .clickable { onPosterClick(coverImage) }
                            )
                        } // End inner Row
                    } // End inner content alignment Box
                } // End of Hero Box

                // Season Chunks and Episodes section below Hero Box
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 1400.dp)
                            .fillMaxWidth()
                            .padding(start = 48.dp, bottom = 48.dp, end = 48.dp)
                    ) {
                        FranchiseOrderSection(
                            state = state,
                            onAnimeClick = onAnimeClick,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(24.dp))
                        AnimeThemesSection(state = state, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(30.dp))

                        val episodesPerPage = 100
                        val totalEpisodes = state.episodes.size
                        var currentPageStart by remember(totalEpisodes > 0) {
                            mutableStateOf(
                                if (totalEpisodes > 0) maxOf(
                                    1,
                                    ((totalEpisodes - 1) / episodesPerPage) * episodesPerPage + 1
                                ) else 1
                            )
                        }

                        if (state.isLoadingEpisodes) {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AppColors.accent)
                            }
                        } else if (totalEpisodes == 0) {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text("No episodes available.", color = AppColors.textMuted, fontSize = 16.sp)
                            }
                        } else {
                            var isAscending by remember { mutableStateOf(false) }
                            var searchQuery by remember { mutableStateOf("") }
                            val pageGroups = (1..totalEpisodes step episodesPerPage).toList()
                            val displayGroups = if (isAscending) pageGroups else pageGroups.reversed()

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Search bar
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 15.sp
                                        ),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.text),
                                        modifier = Modifier
                                            .width(220.dp)
                                            .clip(RoundedCornerShape(32.dp))
                                            .background(AppColors.surfaceVariant)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        decorationBox = { innerTextField ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Search,
                                                    contentDescription = "Search",
                                                    tint = AppColors.textMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Box(modifier = Modifier.weight(1f)) {
                                                    if (searchQuery.isEmpty()) {
                                                        Text(
                                                            "Search episodes...",
                                                            color = AppColors.textMuted,
                                                            fontSize = 15.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                                if (searchQuery.isNotEmpty()) {
                                                    Icon(
                                                        Icons.Default.Clear,
                                                        contentDescription = "Clear",
                                                        tint = AppColors.textMuted,
                                                        modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    // Episode Chunks Dropdown
                                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                                        var chunkExpanded by remember { mutableStateOf(false) }
                                        val end = (currentPageStart + episodesPerPage - 1).coerceAtMost(totalEpisodes)
                                        val currentText =
                                            if (pageGroups.size > 1) "Eps $currentPageStart-$end" else "Season 1"

                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(32.dp))
                                                .background(AppColors.surfaceVariant)
                                                .clickable { chunkExpanded = true }
                                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = currentText,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Icon(
                                                    imageVector = if (chunkExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Expand",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        if (chunkExpanded) {
                                            androidx.compose.ui.window.Popup(
                                                alignment = Alignment.TopStart,
                                                onDismissRequest = { chunkExpanded = false },
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .padding(top = 48.dp) // Offset below the button
                                                        .width(200.dp)
                                                        .heightIn(max = 300.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(AppColors.surface)
                                                        .border(
                                                            1.dp,
                                                            Color.White.copy(alpha = 0.05f),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(vertical = 8.dp)
                                                ) {
                                                    displayGroups.forEachIndexed { index, start ->
                                                        val itemEnd =
                                                            (start + episodesPerPage - 1).coerceAtMost(totalEpisodes)
                                                        val isSelected = start == currentPageStart
                                                        val text =
                                                            if (pageGroups.size > 1) "Eps $start-$itemEnd" else "Season 1"

                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    currentPageStart = start
                                                                    chunkExpanded = false
                                                                }
                                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(text, color = AppColors.text, fontSize = 14.sp)
                                                                if (isSelected) {
                                                                    Icon(
                                                                        Icons.Default.Check,
                                                                        null,
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (index < displayGroups.size - 1) {
                                                            Box(
                                                                Modifier.fillMaxWidth().height(1.dp)
                                                                    .background(AppColors.border)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                } // Close inner Row

                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(if (isPremiumUser) AppColors.accent else AppColors.surfaceVariant)
                                        .clickable(enabled = isPremiumUser && state.episodes.isNotEmpty()) {
                                            onDownloadSeason(state.episodes)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            if (isPremiumUser) Icons.Default.Download else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (isPremiumUser) AppColors.onAccent else AppColors.textMuted,
                                            modifier = Modifier.size(17.dp),
                                        )
                                        Text(
                                            if (isPremiumUser) "Download Batch (${state.episodes.size})" else "Premium Batch",
                                            color = if (isPremiumUser) AppColors.onAccent else AppColors.textMuted,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                        )
                                    }
                                }

                                // Sort Button Dropdown
                                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                                    var expanded by remember { mutableStateOf(false) }

                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(32.dp))
                                            .background(AppColors.surfaceVariant)
                                            .clickable { expanded = true }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (isAscending) "Oldest" else "Newest",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Icon(
                                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (expanded) {
                                        androidx.compose.ui.window.Popup(
                                            alignment = Alignment.TopEnd,
                                            onDismissRequest = { expanded = false },
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(top = 48.dp) // Offset below the button
                                                    .width(180.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(AppColors.surface)
                                                    .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.05f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(vertical = 8.dp)
                                            ) {
                                                // Oldest Option
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            isAscending = true
                                                            expanded = false
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Oldest First", color = AppColors.text, fontSize = 14.sp)
                                                        if (isAscending) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                // Divider
                                                Box(
                                                    Modifier.fillMaxWidth().height(1.dp)
                                                        .background(AppColors.border)
                                                )

                                                // Newest Option
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            isAscending = false
                                                            expanded = false
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Newest First", color = AppColors.text, fontSize = 14.sp)
                                                        if (!isAscending) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Episode List
                            val filteredEpisodes = state.episodes.filter {
                                if (searchQuery.isNotBlank()) {
                                    val numMatch = it.number.toString().contains(searchQuery)
                                    val titleMatch = (it.title ?: it.titles?.filterNotNull()?.firstOrNull())?.contains(
                                        searchQuery,
                                        ignoreCase = true
                                    ) == true
                                    numMatch || titleMatch
                                } else {
                                    it.number in currentPageStart until (currentPageStart + episodesPerPage)
                                }
                            }.let { list ->
                                if (isAscending) list.sortedBy { it.number } else list.sortedByDescending { it.number }
                            }

                            val listState = rememberLazyListState()
                            val coroutineScope = rememberCoroutineScope()

                            androidx.compose.runtime.LaunchedEffect(currentPageStart, isAscending, searchQuery) {
                                if (filteredEpisodes.isNotEmpty()) {
                                    listState.animateScrollToItem(0)
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                LazyRow(
                                    state = listState,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    listState.scrollBy(-dragAmount)
                                                }
                                            }
                                        }
                                ) {
                                    items(filteredEpisodes, key = { it.number }) { episode ->
                                        DesktopEpisodeCard(
                                            episode = episode,
                                            thumbnail = anime.bestCoverImage(),
                                            watchedEpisode = anime.watchProgress?.episode,
                                            currentProgressSeconds = anime.watchProgress?.currentTime,
                                            episodeProgress = state.episodeProgress[episode.number],
                                            modifier = Modifier.animateItem(),
                                            onClick = { onWatchEpisode(episode.number) },
                                            onDownloadClick = { onDownloadEpisode(episode) }
                                        )
                                    }
                                }

                                // Navigation Arrows
                                if (listState.canScrollBackward) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(
                                                    maxOf(
                                                        0,
                                                        listState.firstVisibleItemIndex - 3
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = (-24).dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, "Previous", tint = Color.White)
                                    }
                                }

                                if (listState.canScrollForward) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(
                                                    minOf(
                                                        filteredEpisodes.size - 1,
                                                        listState.firstVisibleItemIndex + 3
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .offset(x = (-24).dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, "Next", tint = Color.White)
                                    }
                                }
                            }
                        } // Close Box (Navigation + LazyRow)
                    } // Close Column
                } // Close Outer Box for episodes
            } // Close Main Scroll Column
        } // Close CompositionLocalProvider
    } // Close BoxWithConstraints
}

@Composable
private fun DesktopEpisodeCard(
    episode: EpisodeItem,
    thumbnail: String?,
    watchedEpisode: Int? = null,
    currentProgressSeconds: Double? = null,
    episodeProgress: EpisodeProgress? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .width(300.dp)
            .aspectRatio(16f / 9.5f)
            .clip(RoundedCornerShape(12.dp))
            .tvFocusableClick(shape = RoundedCornerShape(12.dp), onClick = onClick)
    ) {
        // Thumbnail
        AsyncImage(
            model = thumbnail,
            contentDescription = "Episode ${episode.number}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for bottom section text readability
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.2f to Color.Transparent,
                    0.6f to Color.Black.copy(alpha = 0.7f),
                    1.0f to Color.Black.copy(alpha = 0.95f)
                )
            )
        )

        // Filler/Recap Badge
        if (episode.filler == true || episode.recap == true) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(bottomEnd = 12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (episode.filler == true) {
                        Text("FILLER", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (episode.recap == true) {
                        Text("RECAP", color = Color(0xFF2196F3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Download Button
        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = "Download",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Bottom Info Match to Screenshot exactly
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "EPISODE ${episode.number}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val progress = episodeProgress
                    val hasDetailedProgress = progress != null
                    val progressPercent = if (progress != null && progress.duration > 0) {
                        (progress.currentTime / progress.duration).coerceIn(0.0, 1.0)
                    } else 0.0

                    when {
                        hasDetailedProgress && progressPercent >= 0.9 -> {
                            Text("WATCHED", color = Color(0xFF66BB6A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        hasDetailedProgress && progressPercent > 0.0 -> {
                            Text(
                                "IN PROGRESS",
                                color = Color(0xFFFFD54F),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        !hasDetailedProgress && watchedEpisode != null && episode.number < watchedEpisode -> {
                            Text("WATCHED", color = Color(0xFF66BB6A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        !hasDetailedProgress && watchedEpisode != null && episode.number == watchedEpisode -> {
                            Text(
                                if ((currentProgressSeconds ?: 0.0) > 0.0) "IN PROGRESS" else "LAST WATCHED",
                                color = Color(0xFFFFD54F),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (episode.filler == true) {
                        Text("FILLER", color = Color(0xFFFF9800), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    if (episode.recap == true) {
                        Text("RECAP", color = Color(0xFF2196F3), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            val title = episode.title ?: episode.titles?.filterNotNull()?.firstOrNull() ?: "Episode ${episode.number}"
            Text(
                text = title,
                color = AppColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Description equivalent text (or skip if empty)
            Text(
                text = "Watch episode ${episode.number} right now.",
                color = AppColors.textDim,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(4.dp))

            // Date (ago) at bottom right logically
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(episode.ago ?: "", color = AppColors.textMuted, fontSize = 10.sp)
            }
        }

        // Red progress bar at the very bottom
        if (episodeProgress != null && episodeProgress.duration > 0 && episodeProgress.currentTime > 0) {
            val progressPercentValue =
                (episodeProgress.currentTime / episodeProgress.duration).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progressPercentValue },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color.Red,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun WatchlistDropdownIcon(
    state: AnimeInfoUiState,
    onUpdate: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }) {
        if (state.isUpdatingWatchlist) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(
                if (state.inWatchlist) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Watchlist",
                tint = Color.White
            )
        }
    }

    if (showSheet) {
        WatchlistBottomSheet(
            currentFolder = state.folder,
            onSelect = { option ->
                showSheet = false
                onUpdate(option)
            },
            onDismiss = { showSheet = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonBatchPickerDialog(
    episodes: List<to.kuudere.anisuge.data.models.EpisodeItem>,
    onDismiss: () -> Unit,
    onConfirm: (episodes: List<to.kuudere.anisuge.data.models.EpisodeItem>, server: String, preferDub: Boolean) -> Unit,
) {
    val serverRepository = remember { to.kuudere.anisuge.AppComponent.serverRepository }
    val catalogServers by serverRepository.servers.collectAsState()
    // Recommended batch servers (Anikage, Anitaku, AniDB, Miruro) shown first, then the rest of the catalog.
    val batchServers = remember(catalogServers) {
        val available = catalogServers
            .filterNot { it.id.equals("animepahe", ignoreCase = true) }
        val byId = available.associateBy { it.id.lowercase() }
        val recommended = BATCH_RECOMMENDED_SERVERS.mapNotNull { id ->
            byId[id.lowercase()] ?: when (id.lowercase()) {
                // Surface requested servers even if the live catalog hasn't loaded them yet.
                "anidb" -> to.kuudere.anisuge.data.models.ServerInfo(id = "anidb", label = "AniDB", type = "sub_dub")
                "miruro" -> to.kuudere.anisuge.data.models.ServerInfo(id = "miruro", label = "Miruro", type = "sub_dub")
                "anikage" -> to.kuudere.anisuge.data.models.ServerInfo(id = "anikage", label = "Anikage", type = "sub")
                "anitaku-1" -> to.kuudere.anisuge.data.models.ServerInfo(
                    id = "anitaku-1",
                    label = "Anitaku 1",
                    type = "sub"
                )

                "anitaku" -> to.kuudere.anisuge.data.models.ServerInfo(id = "anitaku", label = "Anitaku", type = "sub")
                else -> null
            }
        }
        val recommendedIds = recommended.map { it.id.lowercase() }.toSet()
        val rest = available.filterNot { it.id.lowercase() in recommendedIds }
        (recommended + rest).distinctBy { it.id.lowercase() }
    }

    var selectedServer by remember(batchServers) {
        mutableStateOf(batchServers.firstOrNull()?.id ?: "anikage")
    }
    val selectedServerInfo = remember(selectedServer, batchServers) {
        batchServers.firstOrNull { it.id.equals(selectedServer, ignoreCase = true) }
    }
    var preferDub by remember { mutableStateOf(false) }
    // Sub-only servers can't honour a Dub batch — reset to Sub when one is picked.
    LaunchedEffect(selectedServerInfo) {
        if (selectedServerInfo?.supportsDub == false) preferDub = false
    }

    val sortedEpisodes = remember(episodes) {
        episodes
            .filter { it.number > 0 }
            .sortedBy { it.number }
    }
    var selectedNumbers by remember(sortedEpisodes) {
        mutableStateOf(sortedEpisodes.take(12).map { it.number }.toSet())
    }
    var rangeStart by remember(sortedEpisodes) {
        mutableStateOf(
            sortedEpisodes.firstOrNull()?.number?.toString().orEmpty()
        )
    }
    var rangeEnd by remember(sortedEpisodes) {
        mutableStateOf(
            sortedEpisodes.getOrNull(11)?.number?.toString() ?: sortedEpisodes.lastOrNull()?.number?.toString()
                .orEmpty()
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredEpisodes = remember(sortedEpisodes, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedEpisodes
        } else {
            val query = searchQuery.trim().lowercase()
            sortedEpisodes.filter {
                it.number.toString().contains(query) ||
                        it.title?.lowercase()?.contains(query) == true
            }
        }
    }
    val selectedEpisodes = remember(sortedEpisodes, selectedNumbers) {
        sortedEpisodes.filter { it.number in selectedNumbers }
    }

    fun selectFirst(count: Int) {
        selectedNumbers = sortedEpisodes.take(count).map { it.number }.toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = {
            Text("Batch download", color = AppColors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        "Choose up to ${to.kuudere.anisuge.utils.DownloadManager.MAX_SEASON_BATCH_EPISODES} episodes. You can use a quick range or pick episodes manually.",
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val quickActions = listOf(
                                "12 eps" to 12,
                                "24 eps" to 24,
                            )
                            quickActions.forEach { (label, count) ->
                                AssistChip(
                                    onClick = { selectFirst(count) },
                                    label = { Text(label) },
                                    enabled = sortedEpisodes.isNotEmpty(),
                                )
                            }
                            AssistChip(
                                onClick = {
                                    selectedNumbers =
                                        sortedEpisodes.take(to.kuudere.anisuge.utils.DownloadManager.MAX_SEASON_BATCH_EPISODES)
                                            .map { it.number }.toSet()
                                },
                                label = { Text("Select All") },
                                enabled = sortedEpisodes.isNotEmpty(),
                            )
                            AssistChip(
                                onClick = {
                                    selectedNumbers = emptySet()
                                },
                                label = { Text("Clear All") },
                                enabled = selectedNumbers.isNotEmpty(),
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search episode number or title...",
                                    color = AppColors.textMuted,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.text,
                                unfocusedTextColor = AppColors.text,
                                focusedBorderColor = AppColors.border,
                                unfocusedBorderColor = AppColors.border,
                                focusedLabelColor = AppColors.text,
                                unfocusedLabelColor = AppColors.textMuted,
                            ),
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.heightIn(max = 320.dp).fillMaxWidth()) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(filteredEpisodes, key = { it.number }) { episode ->
                                    val checked = episode.number in selectedNumbers
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (checked) AppColors.surfaceVariant else AppColors.surface)
                                            .clickable {
                                                selectedNumbers = if (checked) {
                                                    selectedNumbers - episode.number
                                                } else if (selectedNumbers.size < to.kuudere.anisuge.utils.DownloadManager.MAX_SEASON_BATCH_EPISODES) {
                                                    selectedNumbers + episode.number
                                                } else {
                                                    selectedNumbers
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { next ->
                                                selectedNumbers = if (next) {
                                                    if (selectedNumbers.size < to.kuudere.anisuge.utils.DownloadManager.MAX_SEASON_BATCH_EPISODES) selectedNumbers + episode.number else selectedNumbers
                                                } else {
                                                    selectedNumbers - episode.number
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = AppColors.accent,
                                                uncheckedColor = AppColors.textMuted,
                                                checkmarkColor = AppColors.onAccent,
                                            ),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Episode ${episode.number}",
                                                color = AppColors.text,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            episode.title?.takeIf { it.isNotBlank() }?.let {
                                                Text(
                                                    it,
                                                    color = AppColors.textMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = rangeStart,
                                onValueChange = { rangeStart = it.filter(Char::isDigit).take(4) },
                                label = { Text("From") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppColors.text,
                                    unfocusedTextColor = AppColors.text,
                                    focusedBorderColor = AppColors.border,
                                    unfocusedBorderColor = AppColors.border,
                                    focusedLabelColor = AppColors.text,
                                    unfocusedLabelColor = AppColors.textMuted,
                                ),
                            )
                            OutlinedTextField(
                                value = rangeEnd,
                                onValueChange = { rangeEnd = it.filter(Char::isDigit).take(4) },
                                label = { Text("To") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppColors.text,
                                    unfocusedTextColor = AppColors.text,
                                    focusedBorderColor = AppColors.border,
                                    unfocusedBorderColor = AppColors.border,
                                    focusedLabelColor = AppColors.text,
                                    unfocusedLabelColor = AppColors.textMuted,
                                ),
                            )
                            Button(
                                onClick = {
                                    val start = rangeStart.toIntOrNull()
                                    val end = rangeEnd.toIntOrNull()
                                    if (start != null && end != null) {
                                        val low = minOf(start, end)
                                        val high = maxOf(start, end)
                                        selectedNumbers = sortedEpisodes
                                            .filter { it.number in low..high }
                                            .take(to.kuudere.anisuge.utils.DownloadManager.MAX_SEASON_BATCH_EPISODES)
                                            .map { it.number }
                                            .toSet()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.accent,
                                    contentColor = AppColors.onAccent
                                ),
                            ) {
                                Text("Use")
                            }
                        }

                        Text(
                            "Selected ${selectedEpisodes.size} episode${if (selectedEpisodes.size == 1) "" else "s"}",
                            color = AppColors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // ── Audio (Sub / Dub) ─────────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Audio", color = AppColors.textMuted, fontSize = 13.sp)
                        val dubSupported = selectedServerInfo?.supportsDub != false
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(false to "Sub", true to "Dub").forEach { (dub, label) ->
                                val enabled = !dub || dubSupported
                                val isSelected = preferDub == dub
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> AppColors.accent
                                                else -> AppColors.surface
                                            }
                                        )
                                        .then(if (enabled) Modifier.clickable { preferDub = dub } else Modifier)
                                        .alpha(if (enabled) 1f else 0.35f)
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) AppColors.onAccent else AppColors.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        if (selectedServerInfo?.supportsDub == false) {
                            Text(
                                "${selectedServerInfo.displayName} is Sub-only.",
                                color = AppColors.textDim,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // ── Server (recommended first) ────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Server", color = AppColors.textMuted, fontSize = 13.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(batchServers, key = { it.id }) { server ->
                                val isSelected = server.id.equals(selectedServer, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AppColors.accent else AppColors.surface)
                                        .clickable { selectedServer = server.id }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = server.displayName,
                                        color = if (isSelected) AppColors.onAccent else AppColors.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        Text(
                            "If the selected server fails, we try Anikage, Anitaku, AniDB, then Miruro automatically.",
                            color = AppColors.textDim,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedEpisodes.isNotEmpty(),
                onClick = { onConfirm(selectedEpisodes, selectedServer, preferDub) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = AppColors.onAccent
                ),
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppColors.textMuted)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeListSection(
    state: AnimeInfoUiState,
    onWatchEpisode: (Int) -> Unit,
    onDownloadEpisode: (to.kuudere.anisuge.data.models.EpisodeItem) -> Unit,
    onDownloadSeason: (List<to.kuudere.anisuge.data.models.EpisodeItem>) -> Unit,
    isPremiumUser: Boolean,
    watchedEpisode: Int? = null,
    currentProgressSeconds: Double? = null,
) {
    if (state.isLoadingEpisodes) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.accent, strokeWidth = 2.dp)
        }
        return
    }

    if (state.episodes.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("No episodes found", color = AppColors.textMuted)
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var currentPageStart by remember { mutableStateOf(1) }
    val episodesPerPage = 100
    val totalEpisodes = state.episodes.size
    val pageGroups = (1..totalEpisodes step episodesPerPage).toList()

    Column(Modifier.fillMaxWidth()) {
        // Season header + Group selector
        var showGroupSheet by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.surface)
                .clickable { showGroupSheet = true }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val end = (currentPageStart + episodesPerPage - 1).coerceAtMost(totalEpisodes)
                    Text(
                        "Episodes $currentPageStart - $end",
                        color = AppColors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$totalEpisodes episodes / Released ${state.details?.subbed ?: totalEpisodes}",
                        color = AppColors.textMuted,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = AppColors.text
                )
            }
        }

        if (showGroupSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGroupSheet = false },
                containerColor = AppColors.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                dragHandle = {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AppColors.textMuted.copy(alpha = 0.3f))
                        )
                    }
                }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Select Episode Range",
                        color = AppColors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    pageGroups.forEach { start ->
                        val end = (start + episodesPerPage - 1).coerceAtMost(totalEpisodes)
                        val isSelected = start == currentPageStart
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AppColors.surfaceVariant else Color.Transparent)
                                .clickable {
                                    currentPageStart = start
                                    showGroupSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Episodes $start - $end",
                                color = AppColors.text,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, null, tint = AppColors.text, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    if (isPremiumUser && state.episodes.isNotEmpty()) {
                        onDownloadSeason(state.episodes)
                    }
                },
                enabled = state.episodes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPremiumUser) AppColors.accent else AppColors.surface.copy(alpha = 0.7f),
                    disabledContainerColor = AppColors.surface.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Icon(
                    if (isPremiumUser) Icons.Default.Download else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isPremiumUser) AppColors.onAccent else AppColors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPremiumUser) "Download Batch (${state.episodes.size})" else "Premium Batch",
                    color = if (isPremiumUser) AppColors.onAccent else AppColors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            placeholder = { Text("Search episode...", color = AppColors.textMuted, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Default.Clear,
                            null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.border,
                unfocusedBorderColor = AppColors.border,
                focusedContainerColor = AppColors.surface,
                unfocusedContainerColor = AppColors.surface,
                focusedTextColor = AppColors.text,
                unfocusedTextColor = AppColors.text,
                cursorColor = AppColors.text
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        // Filtered episode cards
        val filtered = state.episodes.filter {
            val num = it.number
            val inRange = num >= currentPageStart && num < currentPageStart + episodesPerPage
            if (searchQuery.isEmpty()) return@filter inRange
            val matchNum = num.toString().contains(searchQuery)
            val titleMatches = (it.title ?: it.titles?.filterNotNull()?.firstOrNull())?.contains(
                searchQuery,
                ignoreCase = true
            ) == true
            inRange && (matchNum || titleMatches)
        }.sortedBy { it.number }

        // List of episodes
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            filtered.forEach { episode ->
                EpisodeItemRow(
                    episode = episode,
                    thumbnail = state.details?.image ?: state.details?.poster ?: state.details?.cover,
                    watchedEpisode = watchedEpisode,
                    currentProgressSeconds = currentProgressSeconds,
                    episodeProgress = state.episodeProgress[episode.number],
                    onClick = { onWatchEpisode(episode.number) },
                    onDownloadClick = { onDownloadEpisode(episode) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeItemRow(
    episode: EpisodeItem,
    thumbnail: String?,
    watchedEpisode: Int? = null,
    currentProgressSeconds: Double? = null,
    episodeProgress: EpisodeProgress? = null,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
) {
    val progressFraction = if (episodeProgress != null && episodeProgress.duration > 0) {
        (episodeProgress.currentTime / episodeProgress.duration).coerceIn(0.0, 1.0)
    } else 0.0

    // Modernized card style for episode item
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
            .tvFocusableClick(shape = RoundedCornerShape(12.dp), onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail — 16:9 aspect ratio with modern overlay
            Box(
                Modifier
                    .weight(0.42f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.surface)
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = "Episode ${episode.number}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gradient overlay
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                )

                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(32.dp).align(Alignment.Center)
                )

                Text(
                    text = episode.number.toString().padStart(2, '0'),
                    color = AppColors.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Info and Download
            Row(
                modifier = Modifier.weight(0.58f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val title =
                        episode.title ?: episode.titles?.filterNotNull()?.firstOrNull() ?: "Episode ${episode.number}"
                    Text(
                        text = title,
                        color = AppColors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EP ${episode.number}",
                            color = AppColors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        when {
                            episodeProgress != null && progressFraction >= 0.9 -> {
                                ProgressBadge("WATCHED")
                            }

                            episodeProgress != null && progressFraction > 0.0 -> {
                                ProgressBadge("IN PROGRESS")
                            }

                            watchedEpisode != null && episode.number < watchedEpisode -> {
                                ProgressBadge("WATCHED")
                            }

                            watchedEpisode != null && episode.number == watchedEpisode -> {
                                ProgressBadge(
                                    if ((currentProgressSeconds ?: 0.0) > 0.0) "IN PROGRESS" else "LAST WATCHED"
                                )
                            }
                        }
                        if (episode.filler == true) {
                            Text(
                                text = "FILLER",
                                color = Color(0xFFFF9800),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (episode.recap == true) {
                            Text(
                                text = "RECAP",
                                color = Color(0xFF2196F3),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (episode.ago != null) {
                            Text(
                                text = "• ${episode.ago}",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Modernized Download button
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(32.dp)
                        .background(AppColors.surface, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = AppColors.text,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Red progress bar at the bottom showing how much was watched
        if (episodeProgress != null && progressFraction > 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(AppColors.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.toFloat())
                        .height(3.dp)
                        .background(Color(0xFFE50914))
                )
            }
        }
    }
}

@Composable
private fun ProgressBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = AppColors.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun watchedProgressSummary(anime: AnimeDetails): String? {
    val progress = anime.watchProgress ?: return null
    val episode = progress.episode ?: return null
    if (episode <= 0) return null
    val currentTime = progress.currentTime
    val fullyWatchedUntil = (episode - 1).coerceAtLeast(0)
    val fullyWatchedPart = if (fullyWatchedUntil > 0) "Watched EP 1-$fullyWatchedUntil" else null
    val currentPart = if ((currentTime ?: 0.0) > 0.0) {
        "Now at EP $episode • ${formatProgressTime(currentTime ?: 0.0)}"
    } else {
        "Last opened EP $episode"
    }
    return listOfNotNull(fullyWatchedPart, currentPart).joinToString("\n").ifBlank { null }
}

private fun formatProgressTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }
}

/** Mix of minutes vs seconds across API fields — keep download size estimate in the right ballpark. */
private fun estimateDownloadDurationSeconds(ep: EpisodeItem?, animeFallbackMinutes: Int?): Long {
    fun coerceToSeconds(raw: Int): Long {
        val v = raw.toLong().coerceAtLeast(1)
        return if (v > 500) v else v * 60L
    }
    ep?.duration?.takeIf { it > 0 }?.let { return coerceToSeconds(it) }
    animeFallbackMinutes?.takeIf { it > 0 }?.let { return coerceToSeconds(it) }
    return 24L * 60L
}

@Composable
private fun FullScreenImagePreview(
    imageUrl: String,
    animeTitle: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Full screen poster",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Top close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
            )
        }

        // Bottom save button
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = !isSaving) {
                    scope.launch {
                        isSaving = true
                        saveMessage = null
                        try {
                            val response = to.kuudere.anisuge.AppComponent.httpClient.get(imageUrl)
                            val bytes = response.body<ByteArray>()
                            val safeName = animeTitle.take(50)
                                .replace(Regex("[^a-zA-Z0-9 ]"), "")
                                .trim()
                                .ifBlank { "anime" }
                            val fileName =
                                "$safeName - ${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}.jpg"
                            val dir = to.kuudere.anisuge.utils.getDownloadsDirectory()
                            val imagesDir = "$dir/Images"
                            to.kuudere.anisuge.platform.KmpFileSystem.createDirectories(imagesDir)
                            to.kuudere.anisuge.platform.KmpFileSystem.write(
                                "$imagesDir/$fileName",
                                bytes
                            )
                            saveMessage = "Saved to $imagesDir"
                        } catch (e: Exception) {
                            e.printStackTrace()
                            saveMessage = "Failed to save image"
                        } finally {
                            isSaving = false
                        }
                    }
                }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (saveMessage != null) saveMessage!! else "Save Image",
                color = Color.White,
                fontSize = 14.sp,
            )
        }
    }
}
