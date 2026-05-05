package to.kuudere.anisuge.screens.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import to.kuudere.anisuge.data.models.EpisodeDataResponse
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.models.EpisodeLink
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.StreamingData
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.InfoService
import okio.Path.Companion.toPath
import to.kuudere.anisuge.player.VideoPlayerConfig
import to.kuudere.anisuge.player.VideoPlayerState

import to.kuudere.anisuge.screens.watch.SettingsMenuPage

data class WatchUiState(
    val animeId: String = "",
    val isLoading: Boolean = true,
    val isLoadingVideo: Boolean = false,
    val loadingMessage: String? = null,
    val episodeData: EpisodeDataResponse? = null,
    val thumbnails: Map<String, String> = emptyMap(),
    val currentEpisodeNumber: Int = 1,
    val currentServer: String = "animepahe",
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
    val offlineTitle: String? = null
)

class WatchViewModel(
    private val infoService: InfoService,
    private val watchService: to.kuudere.anisuge.data.services.WatchService,
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
        // Guard: if we're in offline mode, don't fetch online data
        if (_uiState.value.offlinePath != null) {
            println("[WatchVM] fetchEpisodeData aborted - offlinePath is set")
            return
        }

        _uiState.update { it.copy(isLoading = true, loadingMessage = "Fetching episode data...") }
        
        // Speculative parallel fetch: if animeId is an integer, we can start loading video immediately
        val speculativeAnilistId = currentAnimeId.toIntOrNull()
        var streamLoadingJob: kotlinx.coroutines.Job? = null
        
        if (speculativeAnilistId != null) {
            val priorityLang = reqLang ?: if (_uiState.value.defaultLang) "dub" else "sub"
            var speculativeServer: String? = reqServer
            
            if (speculativeServer == null) {
                for (candidate in serverRepository.getFallbackPriority()) {
                    val serverInfo = serverRepository.getServerById(candidate)
                    val supportsLang = when (priorityLang) {
                        "dub" -> serverInfo?.supportsDub ?: (candidate.endsWith("-dub"))
                        "sub" -> serverInfo?.supportsSub ?: (!candidate.endsWith("-dub"))
                        else -> true
                    }
                    if (supportsLang) {
                        speculativeServer = candidate
                        break
                    }
                }
            }
            
            val finalSpeculativeServer = speculativeServer ?: "animepahe"
            streamLoadingJob = viewModelScope.launch {
                loadVideoStream(finalSpeculativeServer, speculativeAnilistId)
            }
        }

        val allEpisodes = infoService.getAllEpisodes(currentAnimeId)

        // Check if cancelled or switched to offline before updating state
        if (!coroutineContext.isActive || _uiState.value.offlinePath != null) {
            streamLoadingJob?.cancel()
            return
        }

        if (allEpisodes.isNotEmpty()) {
            // Build an EpisodeDataResponse from the new API data for compatibility with existing UI state
            val episodeData = EpisodeDataResponse(
                current = episodeNumber,
                allEpisodes = allEpisodes,
            )

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    loadingMessage = if (streamLoadingJob != null) null else "Switching servers...",
                    episodeData = episodeData,
                    savedWatchPosition = 0.0
                )
            }

            // If we didn't start speculative loading, start it now
            if (streamLoadingJob == null) {
                val fallbackPriority = serverRepository.getFallbackPriority()
                var targetServerName: String? = null
                var finalLang: String? = reqLang

                // Use requested server if specified
                if (reqServer != null && reqLang != null) {
                    targetServerName = reqServer.lowercase()
                    finalLang = reqLang
                }

                // Otherwise use priority list
                if (targetServerName == null) {
                    val priorityLang = reqLang ?: if (_uiState.value.defaultLang) "dub" else "sub"
                    for (candidate in fallbackPriority) {
                        val serverInfo = serverRepository.getServerById(candidate)
                        val candidateLang = if (candidate.endsWith("-dub")) "dub" else priorityLang

                        val supportsLang = when (candidateLang) {
                            "dub" -> serverInfo?.supportsDub ?: (candidate.endsWith("-dub"))
                            "sub" -> serverInfo?.supportsSub ?: (!candidate.endsWith("-dub"))
                            else -> true
                        }

                        if (supportsLang) {
                            targetServerName = if (candidate == "hiya" && candidateLang == "dub") "hiya-dub" else candidate
                            finalLang = candidateLang
                            break
                        }
                    }
                }

                val serverName = targetServerName ?: fallbackPriority.firstOrNull() ?: "animepahe"
                _uiState.update { it.copy(targetLang = finalLang) }
                loadVideoStream(serverName)
            }
        } else {
            streamLoadingJob?.cancel()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun fetchThumbnails(anilistId: Int) {
        // Thumbnails endpoint removed in Project-R API
    }

    private fun isSuzuServer(serverName: String): Boolean {
        return serverName.equals("suzu", ignoreCase = true)
    }

    private suspend fun loadVideoStream(serverName: String, explicitAnilistId: Int? = null) {
        // Guard: if we're in offline mode, don't load online stream
        if (_uiState.value.offlinePath != null) {
            println("[WatchVM] loadVideoStream aborted - offlinePath is set, should not load online stream")
            return
        }

        val currState = _uiState.value
        val anilistId = explicitAnilistId ?: currState.episodeData?.animeInfo?.anilist ?: currentAnimeId.toIntOrNull() ?: return
        val episodeNum = currState.currentEpisodeNumber

        _uiState.update { it.copy(isLoadingVideo = true, currentServer = serverName, loadingMessage = "Fetching streaming URL...") }

        val response = infoService.getVideoStream(anilistId, episodeNum, serverName)

        // Check if cancelled before updating state
        if (!coroutineContext.isActive) return

        val streamData = response?.directLink?.data ?: response?.data
        if (streamData != null) {
            val isSuzuServer = isSuzuServer(serverName)
            val qualities = mutableListOf<Pair<String, String>>()
            val streamSources = if (isSuzuServer) {
                infoService.getSenshiSources(streamData.file_id ?: streamData.file_code.orEmpty())
                    .ifEmpty { streamData.sources ?: emptyList() }
            } else {
                streamData.sources ?: emptyList()
            }

            if (streamSources.isNotEmpty()) {
                streamSources.forEach {
                    if (it.quality != null && it.url != null) {
                        qualities.add(it.quality to it.url)
                    }
                }
            } else if (streamData.m3u8_url != null) {
                qualities.add("Auto" to streamData.m3u8_url)
            }
            val orderedQualities = if (isSuzuServer) {
                qualities.sortedBy { if (it.first.equals("HardSub", ignoreCase = true)) 0 else 1 }
            } else {
                qualities
            }

            val subtitles = streamData.subtitles ?: emptyList()

            // Refined Subtitle Selection Logic
            var selectedSubUrl: String? = null
            val isDub = currState.targetLang == "dub"
            val englishSubs = subtitles.filter {
                val lang = (it.language ?: it.lang ?: "").lowercase()
                lang == "en" || lang == "eng" || it.languageName?.lowercase()?.contains("english") == true
            }

            if (englishSubs.isNotEmpty()) {
                val candidates = englishSubs
                if (isDub) {
                    selectedSubUrl = candidates.find { it.title?.lowercase()?.contains("dubtitle") == true }?.url
                        ?: candidates.find { it.title?.lowercase()?.contains("cc") == true }?.url
                        ?: candidates.find { it.title?.lowercase()?.contains("forced") == true }?.url
                        ?: candidates.find { !listOf("signs", "songs", "commentary").any { k -> it.title?.lowercase()?.contains(k) == true } }?.url
                        ?: candidates.firstOrNull()?.url
                } else {
                    selectedSubUrl = candidates.find { !listOf("cc", "forced", "dubtitle", "signs", "songs", "commentary").any { k -> it.title?.lowercase()?.contains(k) == true } }?.url
                        ?: candidates.find { it.title?.lowercase()?.contains("full") == true && !listOf("cc", "forced", "dubtitle").any { k -> it.title?.lowercase()?.contains(k) == true } }?.url
                        ?: candidates.find { !listOf("cc", "forced", "dubtitle").any { k -> it.title?.lowercase()?.contains(k) == true } }?.url
                        ?: candidates.firstOrNull()?.url
                }
            }

            if (selectedSubUrl == null) {
                selectedSubUrl = subtitles.find { it.is_default == true }?.url
                    ?: subtitles.firstOrNull()?.url
            }

            // Handle missing chapters/intro/outro
            var intro = streamData.intro
            var outro = streamData.outro
            val chapters = streamData.chapters ?: emptyList()

            if (chapters.isNotEmpty()) {
                if (intro == null) {
                    var foundIntro = chapters.find { ch ->
                        val t = ch.title?.lowercase()?.trim() ?: ""
                        t == "opening" || t == "title sequence" || t == "op" ||
                        t.contains("opening") || t.contains("theme") || t.contains(" op") || t.contains("op ")
                    }
                    if (foundIntro == null) {
                        foundIntro = chapters.find { ch ->
                            val t = ch.title?.lowercase()?.trim() ?: ""
                            t == "intro" || t.contains("intro")
                        }
                    }
                    foundIntro?.let { ch ->
                        intro = to.kuudere.anisuge.data.models.SkipData(ch.resolvedStart, ch.resolvedEnd)
                    }
                }
                if (outro == null) {
                    chapters.find { ch ->
                        val t = ch.title?.lowercase()?.trim() ?: ""
                        t.contains("credit") || t.contains("end") || t.contains("ed") ||
                        t.contains("outro") || t.contains("closing") || t.contains("credits") ||
                        t.contains(" ed") || t.contains("ed ")
                    }?.let { ch ->
                        outro = to.kuudere.anisuge.data.models.SkipData(ch.resolvedStart, ch.resolvedEnd)
                    }
                }
            }

            val finalStreamData = streamData.copy(intro = intro, outro = outro)

            // Download fonts in background
            if (!streamData.fonts.isNullOrEmpty()) {
                viewModelScope.launch {
                    val localFontsDir = to.kuudere.anisuge.utils.downloadFontsAndGetDir(streamData.fonts)
                    _uiState.update { it.copy(currentFontsDir = localFontsDir) }
                    println("[WatchVM] Background font download complete: $localFontsDir")
                }
            }

            // Final guard: if offlinePath was set while we were fetching, abort
            if (_uiState.value.offlinePath != null) {
                println("[WatchVM] loadVideoStream aborted at final step - offlinePath was set")
                return
            }

            // Pre-warm DNS/TCP for the stream URL
            val defaultQuality = if (isSuzuServer) {
                orderedQualities.firstOrNull { it.first.equals("HardSub", ignoreCase = true) }?.first
            } else {
                null
            } ?: orderedQualities.firstOrNull()?.first ?: "Auto"

            orderedQualities.firstOrNull { it.first == defaultQuality }?.second?.let { streamUrl ->
                viewModelScope.launch { infoService.prewarmStreamUrl(streamUrl) }
            }

            _uiState.update { state ->
                state.copy(
                    isLoadingVideo = false,
                    loadingMessage = null,
                    streamingData = finalStreamData,
                    availableQualities = orderedQualities,
                    currentQuality = defaultQuality,
                    availableSubtitles = subtitles,
                    currentSubtitleUrl = selectedSubUrl,
                    offlinePath = null
                )
            }
            println("[WatchVM] Selected sub=$selectedSubUrl, intro=${intro?.start}-${intro?.end}, outro=${outro?.start}-${outro?.end}")
        } else {
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
    fun getAvailableServers(): List<String> {
        return serverRepository.serverIds
    }

    /**
     * Get server info by ID
     */
    fun getServerInfo(serverId: String): ServerInfo? {
        return serverRepository.getServerById(serverId)
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
        val episodeId = currState.episodeData?.episodeId ?: return
        val serverInfo = serverRepository.getServerById(currState.currentServer)
        val server = serverInfo?.apiName ?: currState.currentServer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }.let { if (it == "Zen2") "Zen-2" else it }

        if (currentAnimeId.isEmpty() || duration <= 0) return

        viewModelScope.launch {
            watchService.saveProgress(
                to.kuudere.anisuge.data.services.ProgressRequest(
                    animeId = currentAnimeId,
                    episodeId = episodeId,
                    currentTime = currentTime,
                    duration = duration,
                    server = server,
                    language = language
                )
            )

            // Mark watched when progress threshold met
            val state = _uiState.value
            if (!state.didMarkWatched && duration > 0) {
                val ratio = currentTime / duration
                if (ratio * 100 >= state.syncPercentage) {
                    _uiState.update { it.copy(didMarkWatched = true) }
                    println("[WatchVM] Progress threshold met (${state.syncPercentage}%). Episode ${state.currentEpisodeNumber} marked as watched.")
                }
            }
        }
    }

    fun updateWatchlistStatus(folder: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingWatchlist = true) }
            try {
                if (currentAnimeId.isEmpty()) return@launch
                val success = to.kuudere.anisuge.AppComponent.watchlistService.updateStatus(currentAnimeId, folder)
                if (success) {
                    _uiState.update { state ->
                        state.copy(
                            episodeData = state.episodeData?.copy(
                                inWatchlist = folder != "Remove",
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
                val basePrefs = current ?: to.kuudere.anisuge.data.models.UserSettings()
                
                val updated = basePrefs.copy(
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
