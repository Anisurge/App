package to.kuudere.anisuge.screens.info

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
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.i18n.resolveDisplayTitle
import to.kuudere.anisuge.ui.WatchlistBottomSheet
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.ui.tvFocusableClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeInfoScreen(
    animeId: String,
    viewModel: AnimeInfoViewModel,
    onBack: () -> Unit,
    onWatchEpisode: (String, String, Int) -> Unit,
    onDownloadsClick: () -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onExit: () -> Unit = {}
) {
    LaunchedEffect(animeId) {
        viewModel.loadAnimeInfo(animeId)
    }

    val state by viewModel.uiState.collectAsState()
    val preferRomajiAnimeTitles by to.kuudere.anisuge.AppComponent.settingsStore.preferRomajiAnimeTitlesFlow.collectAsState(initial = false)
    var showEpisodes by remember { mutableStateOf(true) }
    var selectedEpisodeForDownload by remember { mutableStateOf<to.kuudere.anisuge.data.models.EpisodeItem?>(null) }

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
            durationMins = state.details!!.duration ?: 24,
            infoService = to.kuudere.anisuge.AppComponent.infoService,
            serverRepository = to.kuudere.anisuge.AppComponent.serverRepository,
            onDismiss = { selectedEpisodeForDownload = null },
            onStartDownload = { server, subLang, audioLang, downloadFonts, headers, m3u8Url, preferBatchDub ->
                val title = state.details!!.title.displayTitle(preferRomajiAnimeTitles)
                to.kuudere.anisuge.utils.DownloadManager.startDownload(
                    animeId = state.details!!.id,
                    anilistId = anilistId,
                    episodeNumber = ep.number,
                    title = title,
                    coverImage = state.details!!.image ?: state.details!!.poster ?: state.details!!.cover,
                    server = server,
                    subLang = subLang,
                    audioLang = audioLang,
                    downloadFonts = downloadFonts,
                    headers = headers,
                    m3u8Url = m3u8Url,
                    preferBatchDub = preferBatchDub,
                )
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        to.kuudere.anisuge.platform.DraggableWindowArea(
            modifier = Modifier.fillMaxWidth().height(84.dp).align(Alignment.TopStart)
        ) { }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Unknown error", color = Color.White)
            }
        } else if (state.details != null) {
            val anime = state.details!!
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isDesktop = maxWidth >= 1024.dp
                if (isDesktop) {
                    DesktopLayout(
                        anime = anime,
                        state = state,
                        onWatchlistUpdate = { viewModel.updateWatchlist(it) },
                        onWatchNow = { onWatchEpisode(anime.slug ?: anime.id, "sub", 1) },
                        onWatchEpisode = { epNum -> onWatchEpisode(anime.slug ?: anime.id, "sub", epNum) },
                        onGenreClick = onGenreClick,
                        onDownloadEpisode = { selectedEpisodeForDownload = it },
                        onDownloadsClick = onDownloadsClick,
                        onExit = onExit,
                        onBack = onBack
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
                        onWatchNow = { onWatchEpisode(anime.slug ?: anime.id, "sub", 1) },
                        onWatchEpisode = { epNum -> onWatchEpisode(anime.slug ?: anime.id, "sub", epNum) },
                        onGenreClick = onGenreClick,
                        onDownloadEpisode = { selectedEpisodeForDownload = it },
                        onDownloadsClick = onDownloadsClick
                    )
                }
            }
        }
    }
}

private fun stripHtmlTags(htmlContent: String): String {
    return htmlContent.replace(Regex("<.*?>"), "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'").replace("<br>", "\n").replace("<br/>", "\n")
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
    onDownloadsClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().verticalScroll(scrollState)) {
             // Header Background (Image + Gradient) — stays under but is not in the flow
             Box(Modifier.fillMaxWidth().height(250.dp)) {
                // Blur Background — use cover as fallback with stronger blur
                val bannerUrl = anime.banner?.takeIf { 
                    it.isNotBlank() && it != "null" && !it.contains("placeholder") && it.startsWith("http")
                }
                val bgImage = bannerUrl ?: (anime.image ?: anime.poster ?: anime.cover)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { /* Share */ }) {
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
                 AsyncImage(
                    model = anime.image ?: anime.poster ?: anime.cover,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(130.dp)
                        .height(190.dp)
                        .clip(RoundedCornerShape(8.dp))
                 )
                 Spacer(Modifier.width(16.dp))
                 // Right Details
                 Column(Modifier.weight(1f)) {
                     // Title
                    Text(
                        text = anime.resolveDisplayTitle(),
                        color = Color.White,
                        fontSize = 24.sp,
                         fontWeight = FontWeight.Bold,
                         maxLines = 2,
                         overflow = TextOverflow.Ellipsis
                     )
                     Spacer(Modifier.height(8.dp))
                     // Stars
                     val ratingValue = ((anime.score ?: 0)) / 20.0
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         repeat(5) { i ->
                             if (i < ratingValue.toInt()) {
                                 Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                             } else {
                                 Icon(Icons.Default.StarBorder, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                             }
                         }
                         if (ratingValue > 0.0) {
                             Spacer(Modifier.width(4.dp))
                             Text(String.format("%.1f", ratingValue * 2.0), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                         }
                     }
                     Spacer(Modifier.height(8.dp))
                     // Duration - Genres
                     Text(
                         text = "${anime.status} • ${anime.genres?.take(2)?.joinToString(" / ") ?: ""}",
                         color = Color.Gray,
                         fontSize = 13.sp
                     )
                     Spacer(Modifier.height(16.dp))

                     // Play Movie button
                     Box(
                         Modifier
                             .clip(RoundedCornerShape(8.dp))
                             .background(Color(0xFF1D1D1D))
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
                             Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                             Spacer(Modifier.width(8.dp))
                            val watchText = if (anime.watchProgress != null && anime.watchProgress.episode != null) {
                                "Continue - EP ${anime.watchProgress.episode}"
                             } else {
                                 "Watch Now"
                             }
                             Text(watchText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                         }
                     }
                 }
             }

             Spacer(Modifier.height(16.dp))

             // Storyline
             Column(Modifier.padding(horizontal = 16.dp)) {
                 var isExpanded by remember { mutableStateOf(false) }

                 Text("Storyline", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Normal)
                 Spacer(Modifier.height(12.dp))
                 Text(
                     text = stripHtmlTags(anime.description ?: ""),
                     color = Color.Gray,
                     fontSize = 14.sp,
                     lineHeight = 22.sp,
                     maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                     overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.clickable { isExpanded = !isExpanded }
                 )
             }

             Spacer(Modifier.height(16.dp))

             // Custom Tabs
             Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                 Text(
                     "Episodes",
                     color = if (showEpisodes) Color.White else Color.Gray,
                     fontSize = 20.sp,
                     fontWeight = FontWeight.Normal,
                     modifier = Modifier.clickable { onToggleEpisodes(true) }
                 )
                 Text(
                     "Details",
                     color = if (!showEpisodes) Color.White else Color.Gray,
                     fontSize = 20.sp,
                     fontWeight = FontWeight.Normal,
                     modifier = Modifier.clickable { onToggleEpisodes(false) }
                 )
             }

             Spacer(Modifier.height(16.dp))

             if (showEpisodes) {
                 Box(Modifier.padding(horizontal = 16.dp)) {
                     EpisodeListSection(state, onWatchEpisode, onDownloadEpisode)
                 }
             } else {
                 Column(Modifier.padding(horizontal = 16.dp)) {
                     if (!anime.genres.isNullOrEmpty()) {
                         Text("Genres", color = Color.White, fontSize = 16.sp)
                         Spacer(Modifier.height(8.dp))
                         @OptIn(ExperimentalLayoutApi::class)
                         FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             anime.genres.forEach { genre ->
                                 Box(
                                     Modifier
                                         .clip(RoundedCornerShape(8.dp))
                                         .background(Color(0xFF000000))
                                         .clickable { onGenreClick(genre) }
                                         .padding(horizontal = 12.dp, vertical = 6.dp)
                                 ) {
                                     Text(genre, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                 }
                             }
                         }
                         Spacer(Modifier.height(24.dp))
                     }

                     if (!anime.studios.isNullOrEmpty()) {
                         Text("Studios", color = Color.White, fontSize = 16.sp)
                         Spacer(Modifier.height(8.dp))
                         @OptIn(ExperimentalLayoutApi::class)
                         FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            anime.studios.forEach { studioObj ->
                                val studioName = studioObj["name"]?.toString()?.trim('"') ?: ""
                                if (studioName.isNotBlank()) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF000000))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(studioName, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                     }

                     if (!anime.tags.isNullOrEmpty()) {
                         Text("Tags", color = Color.White, fontSize = 16.sp)
                         Spacer(Modifier.height(8.dp))
                         @OptIn(ExperimentalLayoutApi::class)
                         FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             anime.tags.forEach { tagObj ->
                                 val tagName = tagObj["name"]?.toString()?.trim('"') ?: ""
                                 if (tagName.isNotBlank()) {
                                     Box(
                                         Modifier
                                             .clip(RoundedCornerShape(8.dp))
                                             .background(Color(0xFF000000))
                                             .padding(horizontal = 12.dp, vertical = 6.dp)
                                     ) {
                                         Text(tagName, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
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
    onDownloadsClick: () -> Unit,
    onExit: () -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val baseWidth = 1400.dp
        val scaleFactor = (maxWidth / baseWidth).coerceAtLeast(1f)
        val currentDensity = androidx.compose.ui.platform.LocalDensity.current
        val scaledDensity = androidx.compose.ui.unit.Density(currentDensity.density * scaleFactor, currentDensity.fontScale)

        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaledDensity) {
            val scrollState = rememberScrollState()

            Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(scrollState)) {
                // Hero Section Box
        Box(Modifier.fillMaxWidth().height(500.dp)) {
            val bannerUrl = anime.banner?.takeIf { 
                it.isNotBlank() && it != "null" && !it.contains("placeholder") && it.startsWith("http")
            }
            val bgImage = bannerUrl ?: (anime.image ?: anime.poster ?: anime.cover)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                Text(
                    text = anime.resolveDisplayTitle(),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 52.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(24.dp))

                // "For a chance..." Text
                Text(
                    text = stripHtmlTags(anime.description ?: ""),
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
                    val genresText = anime.genres?.take(3)?.joinToString(" • ")
                    if (!genresText.isNullOrEmpty()) {
                        Text(genresText, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                        Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    
                    // Duration
                    if (anime.duration != null && anime.duration!! > 0) {
                        Text("${anime.duration}m", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                        Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    
                    // Year
                    if (anime.year != null && anime.year!! > 0) {
                        Text("${anime.year}", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                        Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    
                    // Rating
                    if (anime.malScore != null && anime.malScore > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.background(Color(0xFFE6B91E), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("MAL", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(anime.malScore.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                        }
                        Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    
                    // Anisurge Score
                    if (anime.score != null && anime.score!! > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.background(Color(0xFF6C5CE7), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("★", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(anime.score.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                        }
                        Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }

                    // Language/Format
                    if (anime.status != null) {
                        Text(anime.status.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    if (anime.type != null) {
                        Text(anime.type.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🇯🇵", fontSize = 14.sp)
                        Text("JP", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1D1D), contentColor = Color.White),
                        shape = RoundedCornerShape(32.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                            val watchText = if (anime.watchProgress != null && anime.watchProgress.episode != null) {
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
                                .background(if (state.inWatchlist) Color.White else Color(0xFF000000))
                                .border(2.dp, if (state.inWatchlist) Color.Transparent else Color.Transparent, CircleShape)
                                .clickable { showSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isUpdatingWatchlist) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = if (state.inWatchlist) Color.Black else Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (state.inWatchlist) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = "Watchlist",
                                    tint = if (state.inWatchlist) Color.Black else Color.White,
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
                }
            } // End Column
            
            // Cover Image on the right side
            AsyncImage(
                model = anime.image ?: anime.poster ?: anime.cover,
                contentDescription = "Cover Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(180.dp) // Adjusted slightly for optimal fit
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
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
            val episodesPerPage = 100
            val totalEpisodes = state.episodes.size
            var currentPageStart by remember(totalEpisodes > 0) { 
                mutableStateOf(if (totalEpisodes > 0) maxOf(1, ((totalEpisodes - 1) / episodesPerPage) * episodesPerPage + 1) else 1)
            }
            
            if (state.isLoadingEpisodes) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (totalEpisodes == 0) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No episodes available.", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
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
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier
                                .width(220.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text("Search episodes...", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.5f),
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
                        val currentText = if (pageGroups.size > 1) "Eps $currentPageStart-$end" else "Season 1"
                        
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { chunkExpanded = true }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        .background(Color(0xFF000000))
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp)
                                ) {
                                    displayGroups.forEachIndexed { index, start ->
                                        val itemEnd = (start + episodesPerPage - 1).coerceAtMost(totalEpisodes)
                                        val isSelected = start == currentPageStart
                                        val text = if (pageGroups.size > 1) "Eps $start-$itemEnd" else "Season 1"
                                        
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
                                                Text(text, color = Color.White, fontSize = 14.sp)
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        
                                        if (index < displayGroups.size - 1) {
                                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    } // Close inner Row

                    // Sort Button Dropdown
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { expanded = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        .background(Color(0xFF000000))
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
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
                                            Text("Oldest First", color = Color.White, fontSize = 14.sp)
                                            if (isAscending) {
                                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    
                                    // Divider
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))

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
                                            Text("Newest First", color = Color.White, fontSize = 14.sp)
                                            if (!isAscending) {
                                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                        val titleMatch = (it.title ?: it.titles?.filterNotNull()?.firstOrNull())?.contains(searchQuery, ignoreCase = true) == true
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
                                thumbnail = anime.image ?: anime.poster ?: anime.cover,
                                modifier = Modifier.animateItem(),
                                onClick = { onWatchEpisode(episode.number) },
                                onDownloadClick = { onDownloadEpisode(episode) }
                            )
                        }
                    }

                    // Navigation Arrows
                    if (listState.canScrollBackward) {
                        IconButton(
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(maxOf(0, listState.firstVisibleItemIndex - 3)) } },
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
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(minOf(filteredEpisodes.size - 1, listState.firstVisibleItemIndex + 3)) } },
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
private fun DesktopEpisodeCard(episode: EpisodeItem, thumbnail: String?, modifier: Modifier = Modifier, onClick: () -> Unit, onDownloadClick: () -> Unit = {}) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("EPISODE ${episode.number}", color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Description equivalent text (or skip if empty)
            Text(
                text = "Watch episode ${episode.number} right now.",
                color = Color.LightGray,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Date (ago) at bottom right logically
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(episode.ago ?: "", color = Color.Gray, fontSize = 10.sp)
            }
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
private fun EpisodeListSection(
    state: AnimeInfoUiState,
    onWatchEpisode: (Int) -> Unit,
    onDownloadEpisode: (to.kuudere.anisuge.data.models.EpisodeItem) -> Unit
) {
    if (state.isLoadingEpisodes) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
        return
    }

    if (state.episodes.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("No episodes found", color = Color.Gray)
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
                .background(Color(0xFF1D1D1D))
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
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$totalEpisodes episodes / Released ${state.details?.subbed ?: totalEpisodes}",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = Color.White
                )
            }
        }

        if (showGroupSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGroupSheet = false },
                containerColor = Color(0xFF1D1D1D),
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
                                .background(Color.White.copy(alpha = 0.3f))
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
                        color = Color.White,
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
                                .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
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
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            placeholder = { Text("Search episode...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.18f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = Color(0xFF141414),
                unfocusedContainerColor = Color(0xFF141414),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
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
            val titleMatches = (it.title ?: it.titles?.filterNotNull()?.firstOrNull())?.contains(searchQuery, ignoreCase = true) == true
            inRange && (matchNum || titleMatches)
        }.sortedBy { it.number }

        // List of episodes
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            filtered.forEach { episode ->
                EpisodeItemRow(
                    episode = episode,
                    thumbnail = state.details?.image ?: state.details?.poster ?: state.details?.cover,
                    onClick = { onWatchEpisode(episode.number) },
                    onDownloadClick = { onDownloadEpisode(episode) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeItemRow(episode: EpisodeItem, thumbnail: String?, onClick: () -> Unit, onDownloadClick: () -> Unit = {}) {
    // Modernized card style for episode item
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .tvFocusableClick(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail — 16:9 aspect ratio with modern overlay
            Box(
                Modifier
                    .weight(0.42f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF000000))
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
                    color = Color.White,
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
                    val title = episode.title ?: episode.titles?.filterNotNull()?.firstOrNull() ?: "Episode ${episode.number}"
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "EP ${episode.number}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
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
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
