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
    onDismiss: () -> Unit,
    onStartDownload: (server: String, subLang: String?, audioLang: String?, downloadFonts: Boolean, headers: Map<String, String>?, m3u8Url: String?, preferBatchDub: Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedServer by remember { mutableStateOf("suzu") }
    var selectedSubLang by remember { mutableStateOf<String?>("English") }
    /** HLS audio track code after playlist parse (e.g. jpn); unrelated to batch_scrape sub/dub. */
    var selectedAudioLang by remember { mutableStateOf<String?>("sub") }
    /** For providers with one `source` id and both `sub` / `dub` in batch_scrape JSON (api.md). */
    var preferBatchDub by remember { mutableStateOf(false) }
    var selectedQualityIndex by remember { mutableIntStateOf(0) }
    
    val serverListState = rememberLazyListState()
    val subListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var availableSubtitles by remember { mutableStateOf<List<String>>(listOf("All", "English")) }
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
    var shouldRequestPermission by remember { mutableStateOf(false) }
    var pendingMp4AfterStorageGrant by remember {
        mutableStateOf<Triple<String, Map<String, String>, String>?>(null)
    }

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
    val selectableServers = remember(catalogServers.value) {
        // Keep AnimePahe available for streaming; hide it only in the download dialog.
        catalogServers.value
            .expandForSelection()
            .filterNot { it.id.equals("animepahe", ignoreCase = true) }
    }

    // Update selected server when repository loads
    LaunchedEffect(selectableServers) {
        if (selectableServers.none { it.id == selectedServer } && selectableServers.isNotEmpty()) {
            selectedServer = selectableServers.first().id
        }
    }

    LaunchedEffect(selectedServer) {
        preferBatchDub = false
    }

    LaunchedEffect(selectedServer, preferBatchDub) {
        isLoadingSubs = true
        estimatedSizeBytes = 0L
        availableQualities = emptyList()
        selectedQualityIndex = 0
        try {
            val legacyDub = selectedServer.endsWith("-dub", ignoreCase = true)
            val apiSource = if (legacyDub) selectedServer.dropLast(4) else selectedServer
            val meta = serverRepository.getServerById(selectedServer)
                ?: serverRepository.getServerById(apiSource)
            val useDubSection = when {
                legacyDub -> true
                meta?.type == "dub" -> true
                meta?.type == "sub" -> false
                else -> preferBatchDub
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
            val m3u8 = qualities.getOrNull(selectedQualityIndex)?.second
            val stream = to.kuudere.anisuge.utils.BatchSubtitleExtract.findStream(
                streamSection,
                m3u8Url = m3u8,
            )
            val subLabels = to.kuudere.anisuge.utils.BatchSubtitleExtract.fromStream(stream)
                .mapNotNull { it.title ?: it.resolvedLang }
                .distinct()
            availableSubtitles = if (subLabels.isEmpty()) listOf("All") else listOf("All") + subLabels
            if (selectedSubLang !in availableSubtitles) {
                selectedSubLang = when {
                    "English" in availableSubtitles -> "English"
                    subLabels.isNotEmpty() -> subLabels.first()
                    else -> "All"
                }
            }
            println("[DownloadDialog] server=$selectedServer qualities=${qualities.size} streams=${streams.size} subs=${subLabels.size}")
        } catch (e: Exception) {
            println("[DownloadDialog] Failed to fetch for $selectedServer: ${e.message}")
            e.printStackTrace()
        } finally {
            isLoadingSubs = false
        }
    }

    LaunchedEffect(batchStreamSection, availableQualities, selectedQualityIndex) {
        val section = batchStreamSection ?: return@LaunchedEffect
        val m3u8 = availableQualities.getOrNull(selectedQualityIndex.coerceAtLeast(0))?.second
        val stream = to.kuudere.anisuge.utils.BatchSubtitleExtract.findStream(section, m3u8Url = m3u8)
        val subLabels = to.kuudere.anisuge.utils.BatchSubtitleExtract.fromStream(stream)
            .mapNotNull { it.title ?: it.resolvedLang }
            .distinct()
        availableSubtitles = if (subLabels.isEmpty()) listOf("All") else listOf("All") + subLabels
        if (selectedSubLang !in availableSubtitles) {
            selectedSubLang = when {
                "English" in availableSubtitles -> "English"
                subLabels.isNotEmpty() -> subLabels.first()
                else -> "All"
            }
        }
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

    if (shouldRequestPermission) {
        to.kuudere.anisuge.utils.RequestStoragePermission { granted ->
            shouldRequestPermission = false
            if (granted) {
                val mp4Pending = pendingMp4AfterStorageGrant
                pendingMp4AfterStorageGrant = null
                if (mp4Pending != null) {
                    val (u, h, q) = mp4Pending
                    to.kuudere.anisuge.utils.DownloadManager.startMp4Download(
                        animeId = animeId,
                        episodeNumber = episodeNumber,
                        title = episodeDisplayTitle,
                        coverImage = coverImage,
                        mp4Url = u,
                        headers = h,
                        qualityLabel = q,
                    )
                } else {
                    onStartDownload(selectedServer, selectedSubLang, selectedAudioLang, true, currentHeaders, selectedM3u8, preferBatchDub)
                }
            } else pendingMp4AfterStorageGrant = null
        }
    }

    LaunchedEffect(currentTask) {
        if (currentTask != null && !currentTask.isPaused && 
            currentTask.status != "Finished" && !currentTask.status.startsWith("Failed") &&
            !to.kuudere.anisuge.utils.hasNotificationPermission()) {
            shouldRequestNotificationPermission = true
        }
    }

    if (shouldRequestNotificationPermission) {
        to.kuudere.anisuge.utils.RequestNotificationPermission { granted ->
            shouldRequestNotificationPermission = false
            // We don't block download for now because it might still work in foreground, 
            // but user won't see notification. We just try to get it.
            if (to.kuudere.anisuge.utils.hasStoragePermission()) {
                onStartDownload(selectedServer, selectedSubLang, selectedAudioLang, true, currentHeaders, selectedM3u8, preferBatchDub)
            } else {
                shouldRequestPermission = true
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = strings.downloadEpisode(episodeNumber),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // Server Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.selectServer, color = Color.Gray, fontSize = 14.sp)
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
                                .background(if (isSelected) Color.White else Color(0xFF000000))
                                .clickable {
                                    selectedServer = server.id
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = server.displayName,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Quality — progressive MP4 (e.g. AllAnime) uses HEAD sizes + per-row download; HLS uses chips + main button
            if (showDirectMp4Picker) {
                DirectMp4QualityPicker(
                    httpClient = to.kuudere.anisuge.AppComponent.httpClient,
                    options = mp4QualityOptions,
                    onDownloadRequested = { url, hdrs, qual ->
                        if (to.kuudere.anisuge.utils.hasStoragePermission()) {
                            to.kuudere.anisuge.utils.DownloadManager.startMp4Download(
                                animeId = animeId,
                                episodeNumber = episodeNumber,
                                title = episodeDisplayTitle,
                                coverImage = coverImage,
                                mp4Url = url,
                                headers = hdrs,
                                qualityLabel = qual,
                            )
                        } else {
                            pendingMp4AfterStorageGrant = Triple(url, hdrs, qual)
                            shouldRequestPermission = true
                        }
                    },
                )
            } else if (availableQualities.isNotEmpty()) {
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
                                        .background(if (isSelected) Color.White else Color(0xFF000000))
                                        .clickable {
                                            selectedQualityIndex = index
                                            currentHeaders = availableQualities[index].third
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
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

            // Audio Selection (Only relevant for Zen servers which embed multiple tracks)
            if (availableAudioTracks.isNotEmpty()) {
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
                                    .background(if (isSelected) Color.White else Color(0xFF000000))
                                    .clickable { selectedAudioLang = code }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Subtitle Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.subtitleLanguage, color = Color.Gray, fontSize = 14.sp)
                if (isLoadingSubs) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (availableSubtitles.size <= 1) {
                    Text(strings.noSubtitlesAvailable, color = Color.Gray, fontSize = 13.sp)
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
                                    .background(if (isSelected) Color.White else Color(0xFF000000))
                                    .clickable { selectedSubLang = sub }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sub,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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

            // Options
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF000000)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.delete, color = Color(0xFFBF80FF), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }

                if (!showDirectMp4Picker) {
                    Button(
                        onClick = {
                            if (currentTask == null) {
                                if (!to.kuudere.anisuge.utils.hasNotificationPermission()) {
                                    shouldRequestNotificationPermission = true
                                } else if (to.kuudere.anisuge.utils.hasStoragePermission()) {
                                    onStartDownload(selectedServer, selectedSubLang, selectedAudioLang, true, currentHeaders, selectedM3u8, preferBatchDub)
                                } else {
                                    shouldRequestPermission = true
                                }
                            } else {
                                if (!to.kuudere.anisuge.utils.hasNotificationPermission()) {
                                    shouldRequestNotificationPermission = true
                                } else {
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isFinished && isPathValid && !isLoadingSubs && !selectedQualityIsDash,
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
                            val sizeText = if (estimatedSizeBytes > 0) " (~${formatFileSize(estimatedSizeBytes)})" else ""
                            Text(
                                text = when {
                                    currentTask == null -> if (isPathValid) strings.startDownload(sizeText) else strings.chooseValidFolder
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

internal fun isDashManifestUrl(url: String): Boolean {
    val path = url.lowercase().substringBefore("#").substringBefore("?")
    return path.endsWith(".mpd") || ".mpd/" in path
}

/** Prefer HLS with Referer (Anikage proxy / Vibeplayer) over DASH or tokenized MP4. */
internal fun preferredDownloadQualityIndex(
    qualities: List<Triple<String, String, Map<String, String>>>,
): Int {
    if (qualities.isEmpty()) return 0
    val downloadable = qualities.withIndex().filter { (_, triple) ->
        !isDashManifestUrl(triple.second) && !isDirectProgressiveMp4Url(triple.second)
    }
    if (downloadable.isEmpty()) return 0
    val withReferer = downloadable.filter { (_, triple) ->
        triple.third["Referer"]?.isNotBlank() == true
    }
    val pool = if (withReferer.isNotEmpty()) withReferer else downloadable
    pool.firstOrNull { (_, triple) ->
        triple.first.contains("1080", ignoreCase = true) &&
            (triple.second.contains("anikage", ignoreCase = true) ||
                triple.second.contains("vibeplayer", ignoreCase = true))
    }?.index?.let { return it }
    pool.firstOrNull { (_, triple) ->
        triple.second.contains("vibeplayer", ignoreCase = true)
    }?.index?.let { return it }
    pool.firstOrNull { (_, triple) ->
        triple.second.contains("anikage", ignoreCase = true)
    }?.index?.let { return it }
    return pool.first().index
}

internal fun isDirectProgressiveMp4Url(url: String): Boolean {
    val lower = url.lowercase().substringBefore("#")
    val path = lower.substringBefore("?")
    if (path.endsWith(".mp4") || ".mp4?" in lower || "/mp4/" in lower) return true
    val host = try {
        urlHost(url)?.lowercase()
    } catch (_: Exception) {
        null
    } ?: return false
    // All anime (`allmanga`) serves progressive MP4 from Wix video CDN paths that may omit a `.mp4` suffix.
    if (host == "video.wixstatic.com" || (host.length >= 8 && host.substring(host.length - 8) == ".wixmp.com")) return true
    // All anime can also serve direct video blobs from fast4speed without an explicit `.mp4` extension.
    if (host == "tools.fast4speed.rsvp" || (host.length >= 16 && host.substring(host.length - 16) == ".fast4speed.rsvp")) {
        if ("/videos/" in path && (path.length < 5 || path.substring(path.length - 5).lowercase() != ".m3u8")) return true
    }
    return false
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${formatFloat(bytes.toDouble() / (1024 * 1024 * 1024), 1)} GB"
        bytes >= 1024 * 1024 -> "${formatFloat(bytes.toDouble() / (1024 * 1024), 0)} MB"
        else -> "${formatFloat(bytes.toDouble() / 1024, 0)} KB"
    }
}
