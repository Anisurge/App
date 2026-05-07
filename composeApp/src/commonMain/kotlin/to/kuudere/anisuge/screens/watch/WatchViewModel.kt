package to.kuudere.anisuge.screens.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import to.kuudere.anisuge.data.models.BatchScrapeResponse
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.StreamingData
import to.kuudere.anisuge.data.models.WatchInfoResponse
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.InfoService
import to.kuudere.anisuge.data.services.WatchlistService
import okio.Path.Companion.toPath
import to.kuudere.anisuge.player.VideoPlayerConfig
import to.kuudere.anisuge.player.VideoPlayerState

import to.kuudere.anisuge.screens.watch.SettingsMenuPage

data class WatchUiState(
    val animeId: String = "",
    val isLoading: Boolean = true,
    val isLoadingVideo: Boolean = false,
    val loadingMessage: String? = null,
    val episodeData: WatchInfoResponse? = null,
    val thumbnails: Map<String, String> = emptyMap(),
    val currentEpisodeNumber: Int = 1,
    val currentServer: String = "suzu",
    val streamingData: StreamingData? = null,
    val availableQualities: List<Pair<String, String>> = emptyList(), // Pair(Quality, URL)
    val currentQuality: String = "Auto",
    val availableSubtitles: List<to.kuudere.anisuge.data.models.SubtitleData> = emptyList(), // Store the full data object
    val currentSubtitleUrl: String? = null,
    val currentFontsDir: String? = null,
    val playbackSpeed: Double = 1.0,
    val savedWatchPosition: Double = 0.0,
    val showSettingsOverlay: Boolean = false,
    val initialSettingsPage: SettingsMenuPage? = SettingsMenuPage.MAIN,
    val activeSidePanel: String? = null, // "episodes" or "comments"
    val isFullscreen: Boolean = false,
    val targetLang: String? = null,
    val targetSubtitleLang: String? = null,
    val targetSubtitleLangCode: String? = null,
    val isUpdatingWatchlist: Boolean = false,
    val autoPlay: Boolean = true,
    val autoNext: Boolean = true,
    val autoSkipIntro: Boolean = false,
    val autoSkipOutro: Boolean = false,
    val defaultLang: Boolean = false,
    val syncPercentage: Int = 80,
    val subtitleSize: Int = 100,
    val didMarkWatched: Boolean = false,
    val offlinePath: String? = null,
    // Offline metadata
    val offlineTitle: String? = null,
)

class WatchViewModel(
    private val infoService: InfoService,
    private val watchlistService: WatchlistService,
    private val settingsStore: to.kuudere.anisuge.data.services.SettingsStore,
    private val settingsService: to.kuudere.anisuge.data.services.SettingsService,
    private val serverRepository: ServerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState = _uiState.asStateFlow()

    private var currentAnimeId: String = ""
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch { settingsStore.autoPlayFlow.collect { v -> _uiState.update { it.copy(autoPlay = v) } } }
        viewModelScope.launch { settingsStore.autoNextFlow.collect { v -> _uiState.update { it.copy(autoNext = v) } } }
        viewModelScope.launch { settingsStore.autoSkipIntroFlow.collect { v -> _uiState.update { it.copy(autoSkipIntro = v) } } }
        viewModelScope.launch { settingsStore.autoSkipOutroFlow.collect { v -> _uiState.update { it.copy(autoSkipOutro = v) } } }
        viewModelScope.launch { settingsStore.defaultLangFlow.collect { v -> _uiState.update { it.copy(defaultLang = v) } } }
        viewModelScope.launch { settingsStore.syncPercentageFlow.collect { v -> _uiState.update { it.copy(syncPercentage = v) } } }
        viewModelScope.launch { settingsStore.subtitleSizeFlow.collect { v -> _uiState.update { it.copy(subtitleSize = v) } } }
    }

    fun initialize(animeId: String, episodeNumber: Int, server: String? = null, lang: String? = null, offlinePath: String? = null, offlineTitle: String? = null) {
        // Cancel any ongoing loading IMMEDIATELY before touching state
        // This prevents race conditions when switching between videos
        loadJob?.cancel()

        currentAnimeId = animeId

        // Update state IMMEDIATELY (synchronously) to prevent stale data from being used
        // before the coroutine starts. This fixes the bug where switching from online
        // to offline would briefly show the old online video.
        _uiState.update {
            it.copy(
                animeId = animeId,
                currentEpisodeNumber = episodeNumber,
                isLoading = true,
                loadingMessage = if (offlinePath != null) "Loading offline video..." else "Fetching episode $episodeNumber...",
                isLoadingVideo = false,
                episodeData = null,
                streamingData = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                currentFontsDir = null,
                savedWatchPosition = 0.0,
                targetLang = null,
                targetSubtitleLang = null,
                targetSubtitleLangCode = null,
                didMarkWatched = false,
                offlinePath = offlinePath,
                offlineTitle = offlineTitle
            )
        }

        loadJob = viewModelScope.launch {
            if (offlinePath != null) {
                loadOfflineStream(offlinePath)
            } else {
                fetchEpisodeData(episodeNumber, server, lang)
            }
        }
    }

    private suspend fun loadOfflineStream(path: String) {
        // Guard: if state shows we're not in offline mode anymore, abort
        if (_uiState.value.offlinePath != path) {
            return
        }

        // Normalize path separators to the system default
        val normalizedPath = path.replace('/', java.io.File.separatorChar).replace('\\', java.io.File.separatorChar)

        // Check for local subs/fonts in same dir
        val dir = try { 
            val file = java.io.File(normalizedPath)
            file.parent
        } catch(e: Exception) { 
            null 
        }
        val subs = mutableListOf<to.kuudere.anisuge.data.models.SubtitleData>()

        if (dir != null) {
            try {
                val dirPath = dir.toPath()
                if (okio.FileSystem.SYSTEM.exists(dirPath)) {
                    val files = okio.FileSystem.SYSTEM.list(dirPath)
                    files.forEach { file ->
                        val name = file.name
                        if (name.startsWith("subtitle") && (name.endsWith(".ass") || name.endsWith(".vtt") || name.endsWith(".srt"))) {
                            val label = name.substringAfter("subtitle_", "").substringBeforeLast(".").ifEmpty { "Default" }
                            val fileUrl = "file://${file.toString()}"
                            subs.add(to.kuudere.anisuge.data.models.SubtitleData(
                                languageName = label,
                                url = fileUrl,
                                format = name.substringAfterLast(".")
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val defaultSub = subs.find { it.url?.contains("subtitle.ass") == true || it.url?.contains("subtitle.vtt") == true } ?: subs.firstOrNull()
        
        _uiState.update { it.copy(
            isLoading = false,
            isLoadingVideo = false,
            currentQuality = "Offline",
            availableQualities = listOf("Offline" to normalizedPath),
            availableSubtitles = subs,
            currentSubtitleUrl = defaultSub?.url,
            currentFontsDir = dir,
            streamingData = null,
            currentServer = "offline"
        ) }
    }

    private suspend fun fetchEpisodeData(episodeNumber: Int, reqServer: String? = null, reqLang: String? = null) {
        if (_uiState.value.offlinePath != null) {
            return
        }

        val (streamLang, streamServer) = pickStreamServer(reqServer, reqLang)

        _uiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = "Fetching episode data...",
                targetLang = streamLang
            )
        }

        val speculativeAnilistId = currentAnimeId.toIntOrNull()
        var streamLoadingJob: kotlinx.coroutines.Job? = null

        if (speculativeAnilistId != null) {
            streamLoadingJob = viewModelScope.launch {
                loadVideoStream(streamServer, speculativeAnilistId)
            }
        }

        val data = infoService.getWatchInfo(currentAnimeId, ep = episodeNumber.toString())

        if (!coroutineContext.isActive || _uiState.value.offlinePath != null) {
            streamLoadingJob?.cancel()
            return
        }

        if (data != null) {
            val slug = data.anime?.animeId?.takeIf { it.isNotBlank() } ?: currentAnimeId
            val loadedEpisodes = fetchAllEpisodes(slug)
            val mergedEpisodes = when {
                loadedEpisodes.isNotEmpty() -> loadedEpisodes
                data.episodes?.isNotEmpty() == true -> data.episodes
                else -> {
                    val total = data.anime?.epCount ?: data.anime?.episodes
                    synthesizeEpisodesFromCount(total)
                }
            }
            val dataWithEpisodes = data.copy(episodes = mergedEpisodes.takeIf { it.isNotEmpty() })

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    loadingMessage = if (streamLoadingJob != null) null else "Switching servers...",
                    episodeData = dataWithEpisodes,
                    savedWatchPosition = data.currentTime ?: 0.0
                )
            }

            if (streamLoadingJob == null) {
                loadVideoStream(streamServer)
            }
        } else {
            streamLoadingJob?.cancel()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Resolves sub/dub and server for a fetch. Uses [reqServer]/[reqLang] when supplied (e.g. deep link),
     * otherwise keeps [WatchUiState.currentServer] when it supports the active language preference.
     */
    private fun pickStreamServer(reqServer: String?, reqLang: String?): Pair<String, String> {
        val state = _uiState.value
        val preferenceIfUnset = if (state.defaultLang) "dub" else "sub"
        val effectiveLang = normalizeWatchLang(reqLang ?: state.targetLang, preferenceIfUnset)

        fun supportsServerLang(serverId: String, lang: String): Boolean {
            val info = serverRepository.getServerById(serverId) ?: return false
            return when (lang) {
                "dub" -> info.supportsDub
                "sub" -> info.supportsSub
                else -> true
            }
        }

        fun fallbackServerForLang(lang: String): String {
            for (id in serverRepository.getFallbackPriority()) {
                if (supportsServerLang(id, lang)) return id
            }
            return serverRepository.getFallbackPriority().firstOrNull() ?: "suzu"
        }

        if (reqServer != null) {
            val s = reqServer.lowercase()
            val lang = normalizeWatchLang(reqLang ?: state.targetLang, preferenceIfUnset)
            return lang to s
        }

        val prior = state.currentServer.lowercase().takeIf {
            it.isNotBlank() && !it.equals("offline", ignoreCase = true)
        }
        if (prior != null && supportsServerLang(prior, effectiveLang)) {
            return effectiveLang to prior
        }

        return effectiveLang to fallbackServerForLang(effectiveLang)
    }

    private fun normalizeWatchLang(raw: String?, fallback: String): String {
        val v = raw?.lowercase()
        return when (v) {
            "dub", "sub" -> v
            else -> fallback
        }
    }

    private suspend fun fetchAllEpisodes(slug: String): List<EpisodeItem> {
        val all = mutableListOf<EpisodeItem>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = infoService.getEpisodes(slug, limit = limit, offset = offset) ?: break
            if (page.episodes.isEmpty()) break
            all.addAll(page.episodes)
            val total = page.total
            if (total != null && all.size >= total) break
            if (page.episodes.size < limit) break
            offset += limit
        }
        return all
    }

    private fun synthesizeEpisodesFromCount(count: Int?): List<EpisodeItem> {
        val n = count?.takeIf { it > 0 } ?: return emptyList()
        return (1..n).map { EpisodeItem(number = it, id = "ep-$it") }
    }

    private suspend fun loadVideoStream(serverName: String, explicitAnilistId: Int? = null) {
        if (_uiState.value.offlinePath != null) {
            return
        }

        val currState = _uiState.value
        var anilistId = explicitAnilistId ?: currState.episodeData?.anilistId

        if (anilistId == null) {
            val details = infoService.getAnimeDetails(currentAnimeId)
            anilistId = details?.anilistId
        }

        if (anilistId == null) {
            _uiState.update { it.copy(isLoadingVideo = false, loadingMessage = null) }
            return
        }

        val episodeNum = currState.currentEpisodeNumber

        val legacyDub = serverName.endsWith("-dub", ignoreCase = true)
        val apiSource = if (legacyDub) serverName.dropLast(4) else serverName
        val meta = serverRepository.getServerById(serverName)
            ?: serverRepository.getServerById(apiSource)
        val isDub = legacyDub || when (meta?.type) {
            "sub" -> false
            "dub" -> true
            else -> currState.targetLang == "dub"
        }

        _uiState.update { it.copy(isLoadingVideo = true, currentServer = serverName, loadingMessage = "Fetching streaming URL...") }

            val response = infoService.getVideoStream(anilistId, episodeNum, apiSource)

            if (!coroutineContext.isActive) return

            var subStreams = response?.sub
            var dubStreams = response?.dub

            // For suzu server, fetch fresh stream URLs from the embed page
            // because the batch_scrape URLs have IP-bound tokens that expire
            if (apiSource.equals("suzu", ignoreCase = true)) {
                val embedUrl = subStreams?.episodeId ?: dubStreams?.episodeId
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

                        val dubEmbedStreams = freshStreams.filter { it.quality.equals("Dub", ignoreCase = true) }
                        val subEmbedStreams = freshStreams.filter { !it.quality.equals("Dub", ignoreCase = true) }

                        if (subEmbedStreams.isNotEmpty()) {
                            subStreams = (subStreams ?: to.kuudere.anisuge.data.models.BatchScrapeStreamData()).copy(streams = subEmbedStreams)
                        }
                        if (dubEmbedStreams.isNotEmpty()) {
                            dubStreams = (dubStreams ?: to.kuudere.anisuge.data.models.BatchScrapeStreamData()).copy(streams = dubEmbedStreams)
                        }
                    }
                }
            }

            // Pick the right section based on sub/dub
            var streamSection = if (isDub) dubStreams else subStreams

            // If chosen lang has no streams, fall back to the other
            if (streamSection == null || streamSection.streams.isEmpty()) {
                streamSection = if (isDub) subStreams else dubStreams
            }

            if (streamSection != null && streamSection.streams.isNotEmpty()) {
                // Deduplicate qualities: if same quality name appears, append language tag
                val qualities = streamSection.streams.mapNotNull { stream ->
                    val quality = stream.quality ?: "Auto"
                    val url = stream.url.ifBlank { null }
                    if (url != null) quality to url else null
                }

                val subtitles = emptyList<to.kuudere.anisuge.data.models.SubtitleData>()

                var selectedSubUrl: String? = null

                val finalStreamData = StreamingData(
                    sources = streamSection.streams.map { 
                        to.kuudere.anisuge.data.models.SourceData(
                            quality = it.quality, 
                            url = it.url,
                            headers = buildMap {
                                it.headers?.Referer?.let { r -> put("Referer", r) }
                                it.headers?.userAgent?.let { u -> put("User-Agent", u) }
                            }
                        ) 
                    },
                    subtitles = subtitles,
                    intro = null,
                    outro = null,
                )

                val defaultQuality = qualities.firstOrNull()?.first ?: "Auto"

                qualities.firstOrNull { it.first == defaultQuality }?.second?.let { streamUrl ->
                    viewModelScope.launch { infoService.prewarmStreamUrl(streamUrl) }
                }

                _uiState.update { state ->
                    state.copy(
                        isLoadingVideo = false,
                        loadingMessage = null,
                        streamingData = finalStreamData,
                        availableQualities = qualities,
                        currentQuality = defaultQuality,
                        availableSubtitles = subtitles,
                        currentSubtitleUrl = selectedSubUrl,
                        offlinePath = null,
                    )
                }
            } else {
                println("[WatchVM] NO STREAMS FOUND! streamSection is null or empty.")
                _uiState.update { it.copy(isLoadingVideo = false, loadingMessage = null, offlinePath = null) }
            }
    }

    fun setQuality(quality: String) {
        _uiState.update { it.copy(currentQuality = quality, showSettingsOverlay = false) }
    }

    fun setSubtitle(url: String?, subtitleLang: String? = null) {
        _uiState.update { 
            it.copy(
                currentSubtitleUrl = url, 
                targetSubtitleLang = subtitleLang ?: it.targetSubtitleLang,
                showSettingsOverlay = false
            ) 
        }
    }

    fun setSpeed(speed: Double) {
         _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setServer(server: String) {
        loadJob?.cancel()
        _uiState.update { 
            it.copy(
                showSettingsOverlay = false,
                isLoadingVideo = true,
                loadingMessage = "Fetching streaming URL...",
                streamingData = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                currentFontsDir = null
            ) 
        }
        loadJob = viewModelScope.launch {
            loadVideoStream(server)
        }
    }

    fun changeServerWithState(
        newServer: String, 
        position: Double, 
        targetAudioLang: String?, 
        targetSubtitleLang: String?,
        targetSubtitleLangCode: String? = null
    ) {
        loadJob?.cancel()
        _uiState.update { 
            it.copy(
                savedWatchPosition = position,
                targetLang = targetAudioLang,
                targetSubtitleLang = targetSubtitleLang,
                targetSubtitleLangCode = targetSubtitleLangCode,
                showSettingsOverlay = false,
                isLoadingVideo = true,
                loadingMessage = "Fetching streaming URL...",
                streamingData = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                currentFontsDir = null
            ) 
        }
        loadJob = viewModelScope.launch {
            loadVideoStream(newServer)
        }
    }

    /**
     * Get the list of available server IDs for UI display
     */
    fun getAvailableServers(): List<ServerInfo> {
        return serverRepository.getAvailableServers()
    }

    /**
     * Get server info by ID
     */
    fun getServerInfo(serverId: String): ServerInfo? {
        return serverRepository.getSelectableServerInfo(serverId)
            ?: serverRepository.getServerById(serverId)
    }

    fun toggleSettingsOverlay(page: SettingsMenuPage? = null) {
        _uiState.update { 
            it.copy(
                showSettingsOverlay = !it.showSettingsOverlay,
                initialSettingsPage = page ?: SettingsMenuPage.MAIN
            ) 
        }
    }

    fun toggleSidePanel(panel: String?) {
        _uiState.update { it.copy(activeSidePanel = if (it.activeSidePanel == panel) null else panel) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun onEpisodeSelected(episodeNumber: Int) {
        loadJob?.cancel()
        _uiState.update { 
            it.copy(
                currentEpisodeNumber = episodeNumber, 
                activeSidePanel = null, 
                didMarkWatched = false, 
                offlinePath = null,
                isLoading = true,
                isLoadingVideo = false,
                loadingMessage = "Fetching episode $episodeNumber...",
                streamingData = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                currentFontsDir = null,
                savedWatchPosition = 0.0  // always start new episode from beginning
            ) 
        }
        loadJob = viewModelScope.launch {
            fetchEpisodeData(episodeNumber)
        }
    }

    fun saveProgress(currentTime: Double, duration: Double, language: String = "sub") {
        val currState = _uiState.value
        val episodeId = "ep-${currState.currentEpisodeNumber}"
        val server = currState.currentServer

        if (currentAnimeId.isEmpty() || duration <= 0) return

        viewModelScope.launch {
            infoService.saveProgress(
                animeId = currentAnimeId,
                episodeId = episodeId,
                currentTime = currentTime,
                duration = duration,
                server = server,
                language = language
            )
        }
    }

    fun updateWatchlistStatus(folder: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingWatchlist = true) }
            try {
                if (currentAnimeId.isEmpty()) return@launch
                val response = watchlistService.updateStatus(currentAnimeId, folder)
                if (response != null) {
                    _uiState.update { state ->
                        state.copy(
                            episodeData = state.episodeData?.copy(
                                folder = if (folder == "Remove") null else folder
                            )
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isUpdatingWatchlist = false) }
            }
        }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch { 
            settingsStore.setAutoPlay(enabled)
            syncPreferencesToServer()
        }
    }

    fun setAutoNext(enabled: Boolean) {
        viewModelScope.launch { 
            settingsStore.setAutoNext(enabled)
            syncPreferencesToServer()
        }
    }

    fun setAutoSkipIntro(enabled: Boolean) {
        viewModelScope.launch { 
            settingsStore.setAutoSkipIntro(enabled)
            syncPreferencesToServer()
        }
    }

    fun setAutoSkipOutro(enabled: Boolean) {
        viewModelScope.launch { 
            settingsStore.setAutoSkipOutro(enabled)
            syncPreferencesToServer()
        }
    }

    fun setSubtitleSize(sizePercent: Int) {
        viewModelScope.launch {
            settingsStore.setSubtitleSize(sizePercent)
        }
    }

    private fun syncPreferencesToServer() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                val current = settingsService.getSettings()
                val baseSettings = current?.settings ?: to.kuudere.anisuge.data.models.UserSettings()
                
                val updated = baseSettings.copy(
                    autoPlay = state.autoPlay,
                    autoNext = state.autoNext,
                    skipIntro = state.autoSkipIntro,
                    skipOutro = state.autoSkipOutro,
                    defaultLang = state.defaultLang,
                    syncPercentage = state.syncPercentage.toDouble()
                )
                settingsService.updateSettings(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
