package to.kuudere.anisuge.screens.info

import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.absolutePath

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
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.InfoService
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
    durationMins: Int,
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

    val availableServers = serverRepository.servers.collectAsState()
    val defaultServer = availableServers.value.firstOrNull()?.id ?: "suzu"

    // Update selected server when repository loads
    LaunchedEffect(availableServers.value) {
        if (selectedServer !in availableServers.value.map { it.id } && availableServers.value.isNotEmpty()) {
            selectedServer = defaultServer
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
            
            // 1. Subtitles
            val subs = emptyList<String>()
            availableSubtitles = listOf("All") + subs
            if (selectedSubLang !in availableSubtitles) {
                selectedSubLang = if ("English" in availableSubtitles) "English" else availableSubtitles.getOrNull(1) ?: "All"
            }

            // 2. Extract available qualities from streams
            val streams = streamSection?.streams ?: emptyList()

            // For suzu server, fetch fresh stream URLs from the embed page
            if (apiSource.equals("suzu", ignoreCase = true)) {
                val embedUrl = streamSection?.episodeId
                if (!embedUrl.isNullOrBlank()) {
                    val embedStreams = infoService.fetchSuzuEmbedStreams(embedUrl)
                    if (embedStreams != null && embedStreams.isNotEmpty()) {
                        val referer = try {
                            val uri = java.net.URI(embedUrl)
                            "${uri.scheme}://${uri.host}"
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
                            availableQualities = targetStreams.map { stream ->
                                val hdrs = buildMap {
                                    stream.headers?.Referer?.let { put("Referer", it) }
                                    stream.headers?.userAgent?.let { put("User-Agent", it) }
                                }
                                Triple(stream.quality ?: "Auto", stream.url, hdrs)
                            }
                        }
                    }
                }
            }

            // If not suzu or embed fetch failed, use batch_scrape streams directly
            if (availableQualities.isEmpty()) {
                availableQualities = streams.map { stream ->
                    val hdrs = buildMap {
                        stream.headers?.Referer?.let { put("Referer", it) }
                        stream.headers?.userAgent?.let { put("User-Agent", it) }
                    }
                    Triple(stream.quality ?: "Auto", stream.url, hdrs)
                }
            }
            println("[DownloadDialog] server=$selectedServer qualities=${availableQualities.size} streams=${streams.size}")

            // 3. Size estimation from the selected quality's M3U8
            val selectedStream = availableQualities.getOrElse(selectedQualityIndex) { availableQualities.firstOrNull() }
            val m3u8Url = selectedStream?.second
            currentHeaders = selectedStream?.third
            if (m3u8Url != null) {
                val masterContent = to.kuudere.anisuge.AppComponent.httpClient.get(m3u8Url) {
                    currentHeaders?.forEach { (k, v) -> header(k, v) }
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
                if (adjustedBps > 0) {
                    estimatedSizeBytes = (adjustedBps / 8) * (durationMins * 60)
                }

                availableAudioTracks = tracks.distinctBy { it.first }
                
                if (availableAudioTracks.isNotEmpty()) {
                    if (selectedAudioLang == null || availableAudioTracks.none { it.first == selectedAudioLang }) {
                        selectedAudioLang = availableAudioTracks.find { it.first == "jpn" || it.first == "ja" }?.first 
                            ?: availableAudioTracks.first().first
                    }
                }
            } else {
                availableAudioTracks = emptyList()
            }
        } catch (e: Exception) {
            println("[DownloadDialog] Failed to fetch for $selectedServer: ${e.message}")
            e.printStackTrace()
        } finally {
            isLoadingSubs = false
        }
    }

    if (shouldRequestPermission) {
        to.kuudere.anisuge.utils.RequestStoragePermission { granted ->
            shouldRequestPermission = false
            if (granted) {
                onStartDownload(selectedServer, selectedSubLang, selectedAudioLang, true, currentHeaders, selectedM3u8, preferBatchDub)
            }
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
                    items(availableServers.value.size) { index ->
                        val server = availableServers.value[index]
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

            val selectedServerInfo = availableServers.value.find { it.id == selectedServer }
            if (selectedServerInfo?.type == "sub_dub" && !selectedServer.endsWith("-dub", ignoreCase = true)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Episode audio", color = Color.Gray, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(false to "Sub", true to "Dub").forEach { (dub, label) ->
                            val isSel = preferBatchDub == dub
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color.White else Color(0xFF000000))
                                    .clickable { preferBatchDub = dub }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Quality Selection
            if (availableQualities.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            var shouldRequestPermission by remember { mutableStateOf(false) }
            if (shouldRequestPermission) {
                to.kuudere.anisuge.utils.RequestStoragePermission { granted ->
                    shouldRequestPermission = false
                    if (granted) {
                        onStartDownload(selectedServer, selectedSubLang, selectedAudioLang, true, currentHeaders, selectedM3u8, preferBatchDub)
                    }
                }
            }

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

                Button(
                    onClick = {
                        if (currentTask == null || currentTask.status.startsWith("Failed")) {
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
                    enabled = !isFinished && isPathValid && !isLoadingSubs,
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
                                currentTask?.status?.startsWith("Failed") == true -> if (isPathValid) strings.retryDownload else strings.chooseValidFolder
                                else -> strings.keepDownloadingInBackground
                            },
                            color = if (isFinished) Color.Black.copy(alpha = 0.5f) else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.0f MB", bytes.toDouble() / (1024 * 1024))
        else -> String.format("%.0f KB", bytes.toDouble() / 1024)
    }
}
