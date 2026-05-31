package to.kuudere.anisuge.screens.info

import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.absolutePath

import to.kuudere.anisuge.utils.formatFloat
import to.kuudere.anisuge.utils.urlHost
import to.kuudere.anisuge.utils.urlSchemeHost
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.BatchScrapeResponse
import to.kuudere.anisuge.data.models.expandForSelection
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.InfoService
import to.kuudere.anisuge.data.models.asHttpHeaderMap
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.client.request.header
import to.kuudere.anisuge.i18n.LocalAppStrings
import to.kuudere.anisuge.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadEpisodeDialog(
    animeId: String,
    episodeId: String,
    episodeNumber: Int,
    anilistId: Int,
    estimatedDurationSeconds: Long,
    episodeDisplayTitle: String,
    coverImage: String?,
    infoService: InfoService,
    serverRepository: ServerRepository,
    isSeasonBatch: Boolean = false,
    batchEpisodeCount: Int = 1,
    onDismiss: () -> Unit,
    onStartDownload: (server: String, subLang: String?, audioLang: String?, downloadFonts: Boolean, headers: Map<String, String>?, m3u8Url: String?, preferBatchDub: Boolean) -> Unit
) {
    val strings = LocalAppStrings.current

    // ---- Step 0: Sub or Dub ----
    var selectedSubDub by remember { mutableStateOf<String?>(null) } // null = not chosen yet, "sub" or "dub"

    // ---- Step 1: Server ----
    var selectedServer by remember { mutableStateOf("") }

    // ---- Step 2: Quality ----
    var selectedQualityIndex by remember { mutableIntStateOf(0) }

    // ---- Step 3: Subtitle language ----
    var selectedSubLang by remember { mutableStateOf<String?>(null) }

    /** HLS audio track code after playlist parse (e.g. jpn); unrelated to batch_scrape sub/dub. */
    var selectedAudioLang by remember { mutableStateOf<String?>(null) }

    val serverListState = rememberLazyListState()
    val subListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var availableSubtitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var batchStreamSection by remember {
        mutableStateOf<to.kuudere.anisuge.data.models.BatchScrapeStreamData?>(null)
    }
    var availableAudioTracks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var availableQualities by remember { mutableStateOf<List<Triple<String, String, Map<String, String>>>>(emptyList()) } // Triple(label, url, headers)
    val selectedM3u8 by remember(availableQualities, selectedQualityIndex) {
        derivedStateOf { availableQualities.getOrElse(selectedQualityIndex) { availableQualities.firstOrNull() }?.second }
    }
    var isLoadingSubs by remember { mutableStateOf(false) }
    var estimatedSizeBytes by remember { mutableStateOf(0L) }
    var currentHeaders by remember { mutableStateOf<Map<String, String>?>(null) }
    var shouldRequestNotificationPermission by remember { mutableStateOf(false) }

    val settingsStore = to.kuudere.anisuge.AppComponent.settingsStore

    val directoryPickerLauncher = rememberDirectoryPickerLauncher { dir ->
        dir?.let {
            val path = it.absolutePath()
            to.kuudere.anisuge.platform.persistFolderPermission(path)
            scope.launch {
                settingsStore.setDownloadPath(path)
            }
        }
    }

    val downloadPath by settingsStore.downloadPathFlow.collectAsState("")
    val isPathValid = remember(downloadPath) {
        if (downloadPath.isBlank()) true
        else to.kuudere.anisuge.platform.isFolderWritable(downloadPath)
    }

    val downloadTasks by to.kuudere.anisuge.utils.DownloadManager.tasks.collectAsState()
    val currentTask = downloadTasks.find { it.animeId == animeId && it.episodeNumber == episodeNumber }

    val mp4QualityOptions = remember(availableQualities) {
        availableQualities
            .filter { isDirectProgressiveMp4Url(it.second) }
            .sortedByDescending { (label, _, _) ->
                Regex("(\\d{3,4})").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            }
    }

    /** AllAnime-style catalogs are MP4-only; mixed providers (e.g. Anikage) keep the HLS quality row. */
    val showDirectMp4Picker = remember(availableQualities, mp4QualityOptions) {
        mp4QualityOptions.isNotEmpty() &&
                availableQualities.all { isDirectProgressiveMp4Url(it.second) }
    }
    val selectedQualityIsDash = remember(availableQualities, selectedQualityIndex) {
        availableQualities.getOrNull(selectedQualityIndex)?.second?.let { isDashManifestUrl(it) } == true
    }

    val catalogServers = serverRepository.servers.collectAsState()
    val allSelectableServers = remember(catalogServers.value) {
        catalogServers.value
            .expandForSelection()
            .filterNot { it.id.equals("animepahe", ignoreCase = true) }
    }

    // Filter servers by the chosen sub/dub type
    val selectableServers = remember(allSelectableServers, selectedSubDub) {
        val choice = selectedSubDub ?: return@remember allSelectableServers
        allSelectableServers.filter { server ->
            when (choice) {
                "sub" -> server.type == "sub" || server.type == "sub_dub"
                "dub" -> server.type == "dub" || server.type == "sub_dub"
                else -> true
            }
        }
    }

    // When sub/dub changes: reset API data and auto-pick first matching server (atomic to avoid race)
    LaunchedEffect(selectedSubDub) {
        if (selectedSubDub != null) {
            availableQualities = emptyList()
            availableSubtitles = emptyList()
            availableAudioTracks = emptyList()
            estimatedSizeBytes = 0L
            selectedQualityIndex = 0
            selectedSubLang = null
            selectedAudioLang = null
            selectedServer = if (selectableServers.isNotEmpty()) {
                if (isSeasonBatch) {
                    selectableServers.firstOrNull { it.id.equals("anitaku-1", ignoreCase = true) }?.id
                        ?: selectableServers.firstOrNull { it.id.equals("anitaku", ignoreCase = true) }?.id
                        ?: selectableServers.firstOrNull { it.id.equals("anikage", ignoreCase = true) }?.id
                        ?: selectableServers.first().id
                } else {
                    selectableServers.first().id
                }
            } else {
                ""
            }
        }
    }

    // Fallback: if servers loaded late (after sub/dub was picked), auto-pick first server
    LaunchedEffect(selectableServers, selectedSubDub) {
        if (selectedSubDub != null && selectedServer.isBlank() && selectableServers.isNotEmpty()) {
            selectedServer = if (isSeasonBatch) {
                selectableServers.firstOrNull { it.id.equals("anitaku-1", ignoreCase = true) }?.id
                    ?: selectableServers.firstOrNull { it.id.equals("anitaku", ignoreCase = true) }?.id
                    ?: selectableServers.firstOrNull { it.id.equals("anikage", ignoreCase = true) }?.id
                    ?: selectableServers.first().id
            } else {
                selectableServers.first().id
            }
            println("[DownloadDialog] fallback: picked server=$selectedServer after servers loaded")
        }
    }

    // Fetch stream data when both sub/dub AND server are selected
    LaunchedEffect(selectedServer, selectedSubDub) {
        val server = selectedServer
        val choice = selectedSubDub
        if (server.isBlank() || choice == null) return@LaunchedEffect

        isLoadingSubs = true
        estimatedSizeBytes = 0L
        availableQualities = emptyList()
        selectedQualityIndex = 0
        try {
            val legacyDub = server.endsWith("-dub", ignoreCase = true)
            val apiSource = if (legacyDub) server.dropLast(4) else server
            val meta = serverRepository.getServerById(server)
                ?: serverRepository.getServerById(apiSource)
            // Determine dub section usage from the sub/dub choice AND server metadata
            val useDubSection = when {
                choice == "dub" -> true
                choice == "sub" -> false
                legacyDub -> true
                meta?.type == "dub" -> true
                meta?.type == "sub" -> false
                else -> false
            }
            val response = infoService.getVideoStream(anilistId, episodeNumber, apiSource)
            var streamSection = if (useDubSection) response?.dub else response?.sub
            batchStreamSection = streamSection

            // 2. Extract available qualities from streams
            val streams = streamSection?.streams ?: emptyList()
            var qualities = emptyList<Triple<String, String, Map<String, String>>>()

            // For suzu server, fetch fresh stream URLs from the embed page
            if (apiSource.equals("suzu", ignoreCase = true)) {
                val embedUrl = streamSection?.episodeId
                if (!embedUrl.isNullOrBlank()) {
                    val embedStreams = infoService.fetchSuzuEmbedStreams(embedUrl)
                    if (embedStreams != null && embedStreams.isNotEmpty()) {
                        val referer = try {
                            val schemeHost = urlSchemeHost(embedUrl)
                            "${schemeHost}"
                        } catch (_: Exception) {
                            "https://senshi.live"
                        }
                        val freshStreams = embedStreams.map { embedStream ->
                            to.kuudere.anisuge.data.models.StreamInfo(
                                url = embedStream.url,
                                quality = embedStream.status ?: "Auto",
                                headers = to.kuudere.anisuge.data.models.StreamHeaders(
                                    Referer = referer
                                )
                            )
                        }
                        val targetStreams = if (useDubSection) {
                            freshStreams.filter { it.quality.equals("Dub", ignoreCase = true) }
                        } else {
                            freshStreams.filter { !it.quality.equals("Dub", ignoreCase = true) }
                        }
                        if (targetStreams.isNotEmpty()) {
                            qualities = targetStreams.map { stream ->
                                Triple(stream.quality ?: "Auto", stream.url, stream.headers.asHttpHeaderMap())
                            }
                        }
                    }
                }
            }

            if (qualities.isEmpty()) {
                qualities = streams.map { stream ->
                    Triple(stream.quality ?: "Auto", stream.url, stream.headers.asHttpHeaderMap())
                }
            }
            availableQualities = qualities
            selectedQualityIndex = preferredDownloadQualityIndex(qualities)
            val subLabels = to.kuudere.anisuge.utils.BatchSubtitleExtract.fromStreamSection(streamSection)
                .mapNotNull { it.title ?: it.resolvedLang }
                .filter { isLikelyLanguageLabel(it) }
                .distinct()
            availableSubtitles = subLabels
            if (selectedSubLang == null || selectedSubLang !in availableSubtitles) {
                selectedSubLang = when {
                    "English" in availableSubtitles -> "English"
                    subLabels.isNotEmpty() -> subLabels.first()
                    else -> "All"
                }
            }
            println("[DownloadDialog] subDub=$choice server=$server qualities=${qualities.size} streams=${streams.size} subs=${subLabels.size}")
        } catch (e: Exception) {
            println("[DownloadDialog] Failed to fetch for $server: ${e.message}")
            e.printStackTrace()
        } finally {
            isLoadingSubs = false
        }
    }

    LaunchedEffect(batchStreamSection, availableQualities, selectedQualityIndex) {
        val section = batchStreamSection ?: return@LaunchedEffect
        val subLabels = to.kuudere.anisuge.utils.BatchSubtitleExtract.fromStreamSection(section)
            .mapNotNull { it.title ?: it.resolvedLang }
            .filter { isLikelyLanguageLabel(it) }
            .distinct()
        availableSubtitles = subLabels
        if (selectedSubLang == null || selectedSubLang !in availableSubtitles) {
            selectedSubLang = when {
                "English" in availableSubtitles -> "English"
                subLabels.isNotEmpty() -> subLabels.first()
                else -> "All"
            }
        }
        println("[DownloadDialog] server=$selectedServer subs=${subLabels.size}")
    }

    LaunchedEffect(availableQualities, selectedQualityIndex, estimatedDurationSeconds, showDirectMp4Picker) {
        if (availableQualities.isEmpty()) {
            estimatedSizeBytes = 0L
            availableAudioTracks = emptyList()
            return@LaunchedEffect
        }
        if (showDirectMp4Picker) {
            estimatedSizeBytes = 0L
            availableAudioTracks = emptyList()
            return@LaunchedEffect
        }
        val idxForEstimate = selectedQualityIndex.coerceIn(0, availableQualities.lastIndex)
        if (isDashManifestUrl(availableQualities[idxForEstimate].second)) {
            estimatedSizeBytes = 0L
            availableAudioTracks = emptyList()
            return@LaunchedEffect
        }
        val idx = selectedQualityIndex.coerceIn(0, availableQualities.lastIndex)
        if (idx != selectedQualityIndex) {
            selectedQualityIndex = idx
        }
        val selectedStream = availableQualities[idx]
        val m3u8Url = selectedStream.second
        val streamHeaders = selectedStream.third
        currentHeaders = streamHeaders
        try {
            val masterContent = to.kuudere.anisuge.AppComponent.httpClient.get(m3u8Url) {
                streamHeaders.forEach { (k, v) -> header(k, v) }
            }.bodyAsText()
            val tracks = mutableListOf<Pair<String, String>>()
            var maxBandwidth = 0L

            masterContent.lines().forEach { line ->
                if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                    val lang = Regex("LANGUAGE=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "unknown"
                    val name = Regex("NAME=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: lang
                    tracks.add(lang to name)
                }
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    if (bw > maxBandwidth) maxBandwidth = bw
                }
            }

            val adjustedBps = if (maxBandwidth > 0 && maxBandwidth < 100000) maxBandwidth * 1000 else maxBandwidth
            estimatedSizeBytes = if (adjustedBps > 0) {
                (adjustedBps / 8) * estimatedDurationSeconds
            } else 0L

            availableAudioTracks = tracks.distinctBy { it.first }

            if (availableAudioTracks.isNotEmpty()) {
                if (selectedAudioLang == null || availableAudioTracks.none { it.first == selectedAudioLang }) {
                    selectedAudioLang = availableAudioTracks.find { it.first == "jpn" || it.first == "ja" }?.first
                        ?: availableAudioTracks.first().first
                }
            }
        } catch (_: Exception) {
            estimatedSizeBytes = 0L
            availableAudioTracks = emptyList()
        }
    }

    LaunchedEffect(currentTask) {
        if (currentTask != null && !currentTask.isPaused &&
            currentTask.status != "Finished" && !currentTask.status.startsWith("Failed") &&
            !to.kuudere.anisuge.utils.hasNotificationPermission()
        ) {
            shouldRequestNotificationPermission = true
        }
    }

    if (shouldRequestNotificationPermission) {
        to.kuudere.anisuge.utils.RequestNotificationPermission { granted ->
            shouldRequestNotificationPermission = false
            println("[DownloadDialog] notification permission result: granted=$granted")
            onStartDownload(
                selectedServer,
                selectedSubLang,
                selectedAudioLang,
                true,
                currentHeaders,
                selectedM3u8,
                selectedSubDub == "dub"
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val canDownload = selectedSubDub != null &&
            selectedServer.isNotBlank() &&
            !isLoadingSubs &&
            isPathValid &&
            !selectedQualityIsDash

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isSeasonBatch) "Download ${batchEpisodeCount.coerceAtLeast(1)} episodes" else strings.downloadEpisode(
                    episodeNumber
                ),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // ==========================================
            // Step 0: Sub or Dub
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sub or Dub?", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("sub" to "Sub", "dub" to "Dub").forEach { (key, label) ->
                        val isSelected = selectedSubDub == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AppColors.accent else AppColors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) AppColors.accent else AppColors.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedSubDub = if (isSelected) null else key
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) AppColors.onAccent else AppColors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // Step 1: Server Selection (only after sub/dub chosen)
            // ==========================================
            AnimatedVisibility(visible = selectedSubDub != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.selectServer, color = Color.Gray, fontSize = 14.sp)
                    if (selectableServers.isEmpty()) {
                        Text(
                            "No servers available for ${selectedSubDub?.uppercase()}",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyRow(
                            state = serverListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        scope.launch { serverListState.scrollBy(-delta) }
                                    }
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectableServers.size) { index ->
                                val server = selectableServers[index]
                                val isSelected = server.id == selectedServer
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AppColors.accent else AppColors.surface)
                                        .clickable {
                                            selectedServer = server.id
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = server.displayName,
                                        color = if (isSelected) AppColors.onAccent else AppColors.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Loading indicator for API call
            AnimatedVisibility(visible = selectedSubDub != null && selectedServer.isNotBlank() && isLoadingSubs) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Fetching streams...", color = Color.Gray, fontSize = 13.sp)
                }
            }

            val hasStreamData =
                selectedSubDub != null && selectedServer.isNotBlank() && !isLoadingSubs && availableQualities.isNotEmpty()

            // ==========================================
            // Step 2: Quality (only after API loaded)
            // ==========================================
            AnimatedVisibility(visible = hasStreamData) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Quality — progressive MP4 (e.g. AllAnime) uses HEAD sizes + per-row download; HLS uses chips + main button
                    if (showDirectMp4Picker && !isSeasonBatch) {
                        DirectMp4QualityPicker(
                            httpClient = to.kuudere.anisuge.AppComponent.httpClient,
                            options = mp4QualityOptions,
                            onDownloadRequested = { url, hdrs, qual ->
                                to.kuudere.anisuge.utils.DownloadManager.startMp4Download(
                                    animeId = animeId,
                                    episodeNumber = episodeNumber,
                                    title = episodeDisplayTitle,
                                    coverImage = coverImage,
                                    mp4Url = url,
                                    headers = hdrs,
                                    qualityLabel = qual,
                                )
                            },
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (availableQualities.size > 1) {
                                Text("Quality", color = Color.Gray, fontSize = 14.sp)
                                androidx.compose.foundation.lazy.LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .draggable(
                                            orientation = Orientation.Horizontal,
                                            state = rememberDraggableState { delta ->
                                                scope.launch { serverListState.scrollBy(-delta) }
                                            }
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableQualities.size) { index ->
                                        val (label, _, _) = availableQualities[index]
                                        val isSelected = index == selectedQualityIndex
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) AppColors.accent else AppColors.surface)
                                                .clickable {
                                                    selectedQualityIndex = index
                                                    currentHeaders = availableQualities[index].third
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) AppColors.onAccent else AppColors.text,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            } else if (availableQualities.size == 1) {
                                Text(
                                    "Quality: ${availableQualities.first().first}",
                                    color = AppColors.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (selectedServer.startsWith("suzu", ignoreCase = true)) {
                                Text(
                                    text = "Suzu quality names come from the provider (not our file-size scan). The download button shows an estimate from playlist bitrate and episode length.",
                                    color = Color.Gray.copy(alpha = 0.88f),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            if (selectedQualityIsDash) {
                                Text(
                                    text = "DASH (.mpd) streams are for playback only — pick an HLS or MP4 quality to download.",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Audio Selection (Only relevant for Zen servers which embed multiple tracks)
            AnimatedVisibility(visible = hasStreamData && availableAudioTracks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.audioTrack, color = Color.Gray, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableAudioTracks.forEach { (code, name) ->
                            val isSelected = selectedAudioLang == code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AppColors.accent else AppColors.surface)
                                    .clickable { selectedAudioLang = code }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) AppColors.onAccent else AppColors.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Step 3: Subtitle Selection (only after API loaded)
            // ==========================================
            AnimatedVisibility(visible = hasStreamData) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.subtitleLanguage, color = Color.Gray, fontSize = 14.sp)
                    if (availableSubtitles.isEmpty()) {
                        Text(strings.noSubtitlesAvailable, color = Color.Gray, fontSize = 13.sp)
                    } else if (availableSubtitles.size == 1) {
                        Text(
                            availableSubtitles.first(),
                            color = AppColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyRow(
                            state = subListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        scope.launch { subListState.scrollBy(-delta) }
                                    }
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableSubtitles.size) { index ->
                                val sub = availableSubtitles[index]
                                val isSelected = selectedSubLang == sub
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AppColors.accent else AppColors.surface)
                                        .clickable { selectedSubLang = sub }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sub,
                                        color = if (isSelected) AppColors.onAccent else AppColors.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Download Path Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = strings.downloadLocation, color = Color.Gray, fontSize = 14.sp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPathValid) to.kuudere.anisuge.platform.formatDisplayPath(downloadPath) else strings.locationUnavailable,
                            color = if (downloadPath.isBlank() || !isPathValid) Color.Gray else Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isPathValid) {
                            Text(
                                strings.chooseWritableFolder,
                                color = Color.Red.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = strings.change,
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable {
                                directoryPickerLauncher.launch()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            /* Removed Download Fonts Switch as it is now strictly automatic in DownloadManager */

            if (currentTask != null && currentTask.status != "Finished") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (currentTask.status.startsWith("Downloading") && currentTask.downloadSpeed.isNotEmpty()) {
                            "${currentTask.status.substringBefore(":")}: ${currentTask.downloadSpeed} • ${currentTask.eta}"
                        } else {
                            currentTask.status
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!currentTask.status.startsWith("Failed")) {
                        LinearProgressIndicator(
                            progress = { currentTask.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val isFinished = currentTask?.status == "Finished"
            val deleteWeight by animateFloatAsState(
                targetValue = if (isFinished) 1f else 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
            val buttonSpacing by animateDpAsState(
                targetValue = if (isFinished) 12.dp else 0.dp,
                animationSpec = tween(durationMillis = 400)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (deleteWeight > 0.01f) {
                    Box(Modifier.weight(deleteWeight).padding(end = buttonSpacing)) {
                        Button(
                            onClick = {
                                if (currentTask != null) {
                                    to.kuudere.anisuge.utils.DownloadManager.removeTask(currentTask.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.surface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                strings.delete,
                                color = AppColors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (!showDirectMp4Picker || isSeasonBatch) {
                    Button(
                        onClick = {
                            println("[DownloadDialog] button clicked! subDub=$selectedSubDub server=$selectedServer canDownload=$canDownload")
                            if (currentTask == null) {
                                val notifOk = to.kuudere.anisuge.utils.hasNotificationPermission()
                                println("[DownloadDialog] notifOk=$notifOk")
                                if (!notifOk) {
                                    println("[DownloadDialog] → requesting notification permission")
                                    shouldRequestNotificationPermission = true
                                } else {
                                    println("[DownloadDialog] → calling onStartDownload(server=$selectedServer)")
                                    onStartDownload(
                                        selectedServer,
                                        selectedSubLang,
                                        selectedAudioLang,
                                        true,
                                        currentHeaders,
                                        if (isSeasonBatch) null else selectedM3u8,
                                        selectedSubDub == "dub",
                                    )
                                }
                            } else {
                                if (!to.kuudere.anisuge.utils.hasNotificationPermission()) {
                                    shouldRequestNotificationPermission = true
                                } else {
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isFinished && canDownload,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFinished) Color.White.copy(alpha = 0.4f) else Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoadingSubs) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                strings.preparing,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        } else {
                            val sizeText =
                                if (estimatedSizeBytes > 0) " (~${formatFileSize(estimatedSizeBytes)})" else ""
                            Text(
                                text = when {
                                    currentTask == null -> if (isPathValid) {
                                        if (isSeasonBatch) "Start Batch$sizeText" else strings.startDownload(sizeText)
                                    } else strings.chooseValidFolder

                                    isFinished -> strings.downloaded
                                    else -> strings.keepDownloadingInBackground
                                },
                                color = if (isFinished) Color.Black.copy(alpha = 0.5f) else Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

fun isDashManifestUrl(url: String): Boolean {
    val path = url.substringAfter('?', "").lowercase()
    return path.endsWith(".mpd")
}

fun preferredDownloadQualityIndex(qualities: List<Triple<String, String, Map<String, String>>>): Int {
    val downloadable = qualities.map { it.first.lowercase() }
    return listOf("8k", "4k", "2160p", "1440p", "1080p", "720p", "480p").firstNotNullOfOrNull { pref ->
        downloadable.indexOfFirst { it.contains(pref) }.takeIf { it >= 0 }
    } ?: run {
        val withReferer = qualities.indexOfFirst { (_, _, headers) ->
            headers["Referer"]?.isNotBlank() == true
        }.takeIf { it >= 0 }
        withReferer ?: run {
            val pool = qualities.indexOfFirst { (_, url) -> !isDirectProgressiveMp4Url(url) }
            if (pool >= 0 && pool < qualities.size - 1) pool else 0
        }
    }
}

fun isDirectProgressiveMp4Url(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.contains(".m3u8") || lower.contains(".mpd")) return false
    val path = url.substringAfter('?', "")
    if (path.contains(".m3u8") || path.contains(".mpd")) return false
    val host = urlHost(url).orEmpty()
    if (lower.endsWith(".mp4") || host.endsWith("ibyteimg.com") || host.endsWith("byteimg.com")) return false
    return true
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return "${formatFloat(size, 1)} ${units[unitIndex]}"
}

private fun isLikelyLanguageLabel(label: String): Boolean {
    val cleaned = label.trim().lowercase()
    if (cleaned.isEmpty()) return false
    if (cleaned.contains("://")) return false
    // Known non-language values that appear in sub_N query params
    if (cleaned in setOf("home", "default", "auto", "none", "na")) return false
    return true
}
