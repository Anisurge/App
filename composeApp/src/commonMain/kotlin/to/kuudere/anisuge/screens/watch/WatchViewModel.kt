package to.kuudere.anisuge.screens.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import to.kuudere.anisuge.data.models.BatchScrapeResponse
import to.kuudere.anisuge.data.models.BatchScrapeStreamData
import to.kuudere.anisuge.data.models.asHttpHeaderMap
import to.kuudere.anisuge.data.models.EpisodeItem
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.StreamingData
import to.kuudere.anisuge.data.models.WatchInfoResponse
import to.kuudere.anisuge.data.models.expandForSelection
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.InfoService
import to.kuudere.anisuge.data.services.HomeService
import to.kuudere.anisuge.data.services.SyncManager
import to.kuudere.anisuge.data.services.TrackingService
import to.kuudere.anisuge.data.services.WatchlistService
import okio.Path.Companion.toPath
import to.kuudere.anisuge.platform.KmpFileSystem
import to.kuudere.anisuge.utils.DownloadManager
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
    val episodeProgress: Map<Int, WatchEpisodeProgress> = emptyMap(),
    val currentEpisodeNumber: Int = 1,
    val currentServer: String = "",
    val streamingData: StreamingData? = null,
    /** Aniskip intro/outro — kept when [streamingData] is cleared during quality/server reload. */
    val introSkip: to.kuudere.anisuge.data.models.SkipData? = null,
    val outroSkip: to.kuudere.anisuge.data.models.SkipData? = null,
    val availableQualities: List<Pair<String, String>> = emptyList(), // Pair(Quality, URL)
    val currentQuality: String = "Auto",
    val availableSubtitles: List<to.kuudere.anisuge.data.models.SubtitleData> = emptyList(), // Store the full data object
    val currentSubtitleUrl: String? = null,
    /** User picked "Off" in settings; avoids forcing sid=0 while embed subs are still loading. */
    val subtitlesDisabled: Boolean = false,
    val currentFontsDir: String? = null,
    val playbackSpeed: Double = 1.0,
    val videoScaleMode: String = "Fit",
    val isBoostSpeedActive: Boolean = false,
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
    // Sync state
    val hasMalToken: Boolean = false,
    val hasAnilistToken: Boolean = false,
    val isSyncingMal: Boolean = false,
    val isSyncingAnilist: Boolean = false,
    val syncSnackbar: String? = null,
    val berriesToast: String? = null,
    val servers: List<ServerInfo> = emptyList(),
)

data class WatchEpisodeProgress(
    val currentTime: Double,
    val duration: Double,
)

class WatchViewModel(
    private val infoService: InfoService,
    private val homeService: HomeService,
    private val watchlistService: WatchlistService,
    private val settingsStore: to.kuudere.anisuge.data.services.SettingsStore,
    private val settingsService: to.kuudere.anisuge.data.services.SettingsService,
    private val serverRepository: ServerRepository,
    private val aniskipService: to.kuudere.anisuge.data.services.AniskipService,
    private val syncManager: SyncManager? = null,
    private val trackingService: TrackingService? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState = _uiState.asStateFlow()

    private var currentAnimeId: String = ""
    private var loadJob: kotlinx.coroutines.Job? = null

    /** Bumps when a new stream fetch starts; stale loads must not publish UI state. */
    private var streamLoadGeneration = 0
    private var lastAutoEpisodeAdvanceMs = 0L
    private var lastPlaybackSurfaceChangeMs = 0L
    private var pendingQualitySelection: String? = null

    /** Last batch_scrape section (for per-stream subtitles when switching quality). */
    private var cachedStreamSection: BatchScrapeStreamData? = null
    private var cachedEpisodeList: List<EpisodeItem>? = null
    private var cachedEpisodeListKey: String? = null
    private var skipTimesJob: kotlinx.coroutines.Job? = null
    private var lastSkipEpisodeLengthSec = 0.0
    private var lastKnownDurationSec = 0.0
    private var lastPlaybackPositionSec = 0.0
    private var lastPlaybackLanguage = "sub"
    private var lastSavedProgressKey: String? = null
    private var lastAutoServerFallbackKey: String? = null

    /** Server explicitly chosen in player settings (not the Settings → priority list default). */
    private var userPinnedStreamServer: String? = null

    init {
        viewModelScope.launch { settingsStore.autoPlayFlow.collect { v -> _uiState.update { it.copy(autoPlay = v) } } }
        viewModelScope.launch { settingsStore.autoNextFlow.collect { v -> _uiState.update { it.copy(autoNext = v) } } }
        viewModelScope.launch { settingsStore.autoSkipIntroFlow.collect { v -> _uiState.update { it.copy(autoSkipIntro = v) } } }
        viewModelScope.launch { settingsStore.autoSkipOutroFlow.collect { v -> _uiState.update { it.copy(autoSkipOutro = v) } } }
        viewModelScope.launch { settingsStore.defaultLangFlow.collect { v -> _uiState.update { it.copy(defaultLang = v) } } }
        viewModelScope.launch { settingsStore.syncPercentageFlow.collect { v -> _uiState.update { it.copy(syncPercentage = v) } } }
        viewModelScope.launch { settingsStore.subtitleSizeFlow.collect { v -> _uiState.update { it.copy(subtitleSize = v) } } }
        viewModelScope.launch { settingsStore.videoScaleModeFlow.collect { v -> _uiState.update { it.copy(videoScaleMode = v) } } }
        viewModelScope.launch {
            serverRepository.servers.collect { list ->
                val expanded = list.expandForSelection()
                val priority = serverRepository.getFallbackPriority()
                val sorted = expanded.sortedBy { s ->
                    val idx = priority.indexOf(s.id)
                    if (idx == -1) Int.MAX_VALUE else idx
                }
                _uiState.update { it.copy(servers = sorted) }
            }
        }
    }

    fun initialize(
        animeId: String,
        episodeNumber: Int,
        server: String? = null,
        lang: String? = null,
        offlinePath: String? = null,
        offlineTitle: String? = null,
        resumeFromContinueSeconds: Double? = null,
    ) {
        // Cancel any ongoing loading IMMEDIATELY before touching state
        // This prevents race conditions when switching between videos
        loadJob?.cancel()
        streamLoadGeneration++

        val newAnime = animeId != currentAnimeId
        currentAnimeId = animeId
        lastKnownDurationSec = 0.0
        lastPlaybackPositionSec = 0.0
        lastSavedProgressKey = null
        if (newAnime) {
            userPinnedStreamServer = null
        }
        server?.takeIf { it.isNotBlank() }?.let { userPinnedStreamServer = it.lowercase() }

        // Update state IMMEDIATELY (synchronously) to prevent stale data from being used
        // before the coroutine starts. This fixes the bug where switching from online
        // to offline would briefly show the old online video.
        _uiState.update {
            it.copy(
                animeId = animeId,
                currentEpisodeNumber = episodeNumber,
                isLoading = true,
                isLoadingVideo = offlinePath == null,
                loadingMessage = when {
                    offlinePath != null -> "Loading offline video..."
                    else -> "Fetching streaming URL..."
                },
                episodeData = null,
                streamingData = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                subtitlesDisabled = false,
                currentFontsDir = null,
                savedWatchPosition = resumeFromContinueSeconds?.takeIf { it >= 1.0 } ?: 0.0,
                targetLang = null,
                targetSubtitleLang = null,
                targetSubtitleLangCode = null,
                didMarkWatched = false,
                offlinePath = offlinePath,
                offlineTitle = offlineTitle,
                currentServer = server?.lowercase().orEmpty().ifBlank { if (newAnime) "" else it.currentServer },
                introSkip = if (newAnime) null else it.introSkip,
                outroSkip = if (newAnime) null else it.outroSkip,
            )
        }

        cachedStreamSection = null
        if (newAnime) {
            cachedEpisodeList = null
            cachedEpisodeListKey = null
        }
        val slug = currentAnimeId
        if (cachedEpisodeList == null && offlinePath == null) {
            viewModelScope.launch {
                try {
                    val episodes = fetchAllEpisodes(slug)
                    if (episodes.isNotEmpty() && cachedEpisodeList == null) {
                        cachedEpisodeList = episodes
                        cachedEpisodeListKey = slug
                    }
                } catch (_: Exception) {
                }
            }
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

        val normalizedPath = to.kuudere.anisuge.utils.MediaPaths.normalizeSeparators(path)

        // Check for local subs/fonts in same dir
        val dir = normalizedPath.substringBeforeLast('/', "")
        val subs = mutableListOf<to.kuudere.anisuge.data.models.SubtitleData>()
        val seenSubtitleUrls = mutableSetOf<String>()

        fun addSidecar(path: String) {
            val normalized = to.kuudere.anisuge.utils.MediaPaths.normalizeSeparators(path)
            val ext = normalized.substringAfterLast(".", "").substringBefore("?")
            if (ext !in setOf("ass", "vtt", "srt")) return
            val url = offlineSubtitleUrl(normalized)
            if (!seenSubtitleUrls.add(url)) return
            val name = normalized.substringAfterLast("/")
            val label = name.substringAfter("subtitle_", name).substringBeforeLast(".").ifEmpty { "Default" }
            subs.add(
                to.kuudere.anisuge.data.models.SubtitleData(
                    languageName = label,
                    url = url,
                    format = ext,
                )
            )
        }

        DownloadManager.tasks.value
            .firstOrNull { it.localPath == path || it.localPath == normalizedPath }
            ?.sidecarPaths
            ?.forEach(::addSidecar)

        if (dir.isNotBlank()) {
            try {
                val entries = KmpFileSystem.listDir(dir)
                entries.forEach { name ->
                    if (name.startsWith("subtitle") && (name.endsWith(".ass") || name.endsWith(".vtt") || name.endsWith(
                            ".srt"
                        ))
                    ) {
                        val label = name.substringAfter("subtitle_", "").substringBeforeLast(".").ifEmpty { "Default" }
                        val fileUrl = offlineSubtitleUrl("$dir/$name")
                        if (seenSubtitleUrls.add(fileUrl)) {
                            subs.add(
                                to.kuudere.anisuge.data.models.SubtitleData(
                                    languageName = label,
                                    url = fileUrl,
                                    format = name.substringAfterLast(".")
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val defaultSub =
            subs.find { it.url?.contains("subtitle.ass") == true || it.url?.contains("subtitle.vtt") == true }
                ?: subs.firstOrNull()

        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingVideo = false,
                currentQuality = "Offline",
                availableQualities = listOf("Offline" to normalizedPath),
                availableSubtitles = subs,
                currentSubtitleUrl = defaultSub?.url,
                currentFontsDir = dir,
                streamingData = null,
                currentServer = "offline"
            )
        }
    }

    private fun offlineSubtitleUrl(path: String): String {
        return when {
            path.startsWith("content://") || path.startsWith("file://") -> path
            else -> to.kuudere.anisuge.utils.MediaPaths.toFileUri(path)
        }
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

        val data = infoService.getWatchInfo(currentAnimeId, ep = episodeNumber.toString())

        if (!coroutineContext.isActive || _uiState.value.offlinePath != null) {
            return
        }

        if (data != null) {
            val slug = data.anime?.animeId?.takeIf { it.isNotBlank() } ?: currentAnimeId
            _uiState.update { state ->
                val mergedResume = mergeResumeHint(data.currentTime, state.savedWatchPosition)
                state.copy(
                    isLoading = false,
                    loadingMessage = "Fetching streaming URL...",
                    savedWatchPosition = mergedResume,
                )
            }

            coroutineScope {
                val streamJob = launch {
                    if (_uiState.value.offlinePath == null && coroutineContext.isActive) {
                        loadVideoStream(streamServer, data.anilistId)
                    }
                }
                val loadedEpisodesDeferred = async {
                    reuseOrNullEpisodes(data, slug) ?: fetchAllEpisodes(slug)
                }
                val progressDeferred = async {
                    fetchEpisodeProgress(currentAnimeId, data)
                }

                val loadedEpisodes = loadedEpisodesDeferred.await()
                val mergedEpisodes = when {
                    loadedEpisodes.isNotEmpty() -> loadedEpisodes
                    data.episodes?.isNotEmpty() == true -> data.episodes
                    else -> {
                        val total = data.anime?.epCount ?: data.anime?.episodes
                        synthesizeEpisodesFromCount(total)
                    }
                }
                cachedEpisodeList = mergedEpisodes.takeIf { it.isNotEmpty() }
                cachedEpisodeListKey = slug
                val dataWithEpisodes = data.copy(episodes = mergedEpisodes.takeIf { it.isNotEmpty() })
                val progressMap = progressDeferred.await()

                if (coroutineContext.isActive && _uiState.value.offlinePath == null) {
                    _uiState.update { state ->
                        state.copy(
                            episodeData = dataWithEpisodes,
                            episodeProgress = progressMap,
                        )
                    }
                }
                streamJob.join()
            }
        } else {
            _uiState.update { it.copy(isLoading = false, isLoadingVideo = false, loadingMessage = null) }
        }
    }

    private suspend fun fetchEpisodeProgress(
        requestedAnimeId: String,
        data: WatchInfoResponse,
    ): Map<Int, WatchEpisodeProgress> {
        val detailsAnimeId = data.anime?.animeId
        val detailsAnilistId = data.anime?.anilistId
        val detailsMalId = data.anime?.malId
        return runCatching {
            homeService.fetchAllContinueWatching()
                .filter { item ->
                    val itemAnimeId = item.animeId
                    val itemAnimeIdStr = item.effectiveAnimeId
                    val matchId = itemAnimeId == requestedAnimeId || itemAnimeIdStr == requestedAnimeId
                    val matchDetailsId = detailsAnimeId != null &&
                            (itemAnimeId == detailsAnimeId || itemAnimeIdStr == detailsAnimeId)
                    val matchAnilist = detailsAnilistId != null &&
                            item.anime.anilistId != null &&
                            detailsAnilistId == item.anime.anilistId
                    val matchMal = detailsMalId != null &&
                            item.anime.malId != null &&
                            detailsMalId == item.anime.malId

                    matchId || matchDetailsId || matchAnilist || matchMal
                }
                .associate { it.displayEpisode to WatchEpisodeProgress(it.progress, it.duration) }
        }.getOrElse {
            println("[WatchVM] fetchEpisodeProgress error: ${it.message}")
            emptyMap()
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

        userPinnedStreamServer?.takeIf { supportsServerLang(it, effectiveLang) }?.let { pinned ->
            return effectiveLang to pinned
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

    /**
     * If [cachedEpisodeList] holds a non-empty list for the same anime, reuse it and
     * avoid redundant paginated `/episodes` calls when only the episode number changed.
     */
    private fun reuseOrNullEpisodes(
        newData: WatchInfoResponse,
        slug: String,
    ): List<EpisodeItem>? {
        val list = cachedEpisodeList
        if (list.isNullOrEmpty()) return null
        val newKey = newData.anime?.animeId?.takeIf { it.isNotBlank() } ?: slug
        val oldKey = cachedEpisodeListKey ?: return null
        return if (oldKey == newKey) list else null
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

        val generation = ++streamLoadGeneration
        lastPlaybackSurfaceChangeMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        lastSkipEpisodeLengthSec = 0.0
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

        _uiState.update {
            it.copy(
                isLoadingVideo = true,
                currentServer = serverName,
                loadingMessage = "Fetching streaming URL..."
            )
        }

        try {
            val scrapeSource = apiSource
            var response = infoService.getVideoStream(anilistId, episodeNum, scrapeSource)
            if (
                response == null ||
                (response.sub?.streams.isNullOrEmpty() == true &&
                        response.dub?.streams.isNullOrEmpty() == true)
            ) {
                val fallback = when {
                    scrapeSource.equals("anitaku-2", ignoreCase = true) -> "anitaku"
                    scrapeSource.equals("anitaku-1", ignoreCase = true) -> "anitaku"
                    else -> null
                }
                if (fallback != null && !fallback.equals(scrapeSource, ignoreCase = true)) {
                    println("[WatchVM] batch_scrape empty for $scrapeSource, retrying source=$fallback")
                    response = infoService.getVideoStream(anilistId, episodeNum, fallback)
                }
            }

            if (!coroutineContext.isActive || generation != streamLoadGeneration) return

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
                            val schemeHost =
                                embedUrl.substringBefore("://", "") + "://" + embedUrl.substringAfter("://")
                                    .substringBefore("/")
                            if (schemeHost.contains("://")) schemeHost else "https://senshi.live"
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
                            subStreams = (subStreams ?: to.kuudere.anisuge.data.models.BatchScrapeStreamData()).copy(
                                streams = subEmbedStreams
                            )
                        }
                        if (dubEmbedStreams.isNotEmpty()) {
                            dubStreams = (dubStreams ?: to.kuudere.anisuge.data.models.BatchScrapeStreamData()).copy(
                                streams = dubEmbedStreams
                            )
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

                cachedStreamSection = streamSection
                val requestedQuality = pendingQualitySelection
                val defaultQuality = when {
                    requestedQuality != null && qualities.any { it.first == requestedQuality } -> requestedQuality
                    apiSource.equals("suzu", ignoreCase = true) &&
                            qualities.any { it.first.equals("HardSub", ignoreCase = true) } -> "HardSub"

                    apiSource.startsWith("anitaku", ignoreCase = true) &&
                            qualities.any { it.first.equals("HD-2", ignoreCase = true) } &&
                            !qualities.any { it.first.equals("HD-1", ignoreCase = true) } -> "HD-2"

                    else -> qualities.firstOrNull()?.first ?: "Auto"
                }
                pendingQualitySelection = null

                val subtitles = to.kuudere.anisuge.utils.BatchSubtitleExtract.forPlayback(
                    streamSection,
                    defaultQuality,
                )
                val selectedSubUrl = to.kuudere.anisuge.utils.BatchSubtitleExtract.defaultUrl(subtitles)

                val skipIntro = currState.introSkip
                val skipOutro = currState.outroSkip
                val finalStreamData = StreamingData(
                    sources = streamSection.streams.map {
                        to.kuudere.anisuge.data.models.SourceData(
                            quality = it.quality,
                            url = it.url,
                            headers = it.headers.asHttpHeaderMap(),
                        )
                    },
                    subtitles = subtitles,
                    intro = skipIntro,
                    outro = skipOutro,
                )

                if (generation != streamLoadGeneration) return

                _uiState.update { state ->
                    state.copy(
                        isLoadingVideo = false,
                        loadingMessage = null,
                        streamingData = finalStreamData,
                        availableQualities = qualities,
                        currentQuality = defaultQuality,
                        availableSubtitles = subtitles,
                        currentSubtitleUrl = selectedSubUrl,
                        subtitlesDisabled = false,
                        offlinePath = null,
                    )
                }

                qualities.firstOrNull { it.first == defaultQuality }?.second?.let { streamUrl ->
                    viewModelScope.launch { infoService.prewarmStreamUrl(streamUrl) }
                }

                attachAniskipTimes(
                    streamGeneration = generation,
                    malId = currState.episodeData?.malId,
                    anilistId = anilistId,
                    episodeNumber = episodeNum,
                    episodeLengthSec = lastSkipEpisodeLengthSec.takeIf { it >= 60.0 },
                )
            } else {
                println("[WatchVM] NO STREAMS FOUND! streamSection is null or empty.")
                if (generation == streamLoadGeneration) {
                    _uiState.update { it.copy(isLoadingVideo = false, loadingMessage = null, offlinePath = null) }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            println("[WatchVM] loadVideoStream cancelled for server=$serverName")
            throw e
        } catch (e: Exception) {
            println("[WatchVM] loadVideoStream error for server=$serverName: ${e.message}")
            e.printStackTrace()
            if (generation == streamLoadGeneration) {
                _uiState.update { it.copy(isLoadingVideo = false, loadingMessage = null, offlinePath = null) }
            }
        }
    }

    fun setQuality(quality: String) {
        val server = _uiState.value.currentServer
        val shouldRefreshStream = server.equals("animepahe", ignoreCase = true) ||
                server.equals("animepahe-dub", ignoreCase = true)

        if (!shouldRefreshStream) {
            val subtitles = to.kuudere.anisuge.utils.BatchSubtitleExtract.forPlayback(
                cachedStreamSection,
                quality,
            )
            val defaultUrl = to.kuudere.anisuge.utils.BatchSubtitleExtract.defaultUrl(subtitles)
            _uiState.update {
                it.copy(
                    currentQuality = quality,
                    availableSubtitles = subtitles,
                    currentSubtitleUrl = if (!it.subtitlesDisabled) defaultUrl else null,
                    showSettingsOverlay = false,
                )
            }
            return
        }

        loadJob?.cancel()
        streamLoadGeneration++
        pendingQualitySelection = quality
        _uiState.update {
            it.copy(
                currentQuality = quality,
                showSettingsOverlay = false,
                isLoadingVideo = true,
                loadingMessage = "Switching quality...",
                streamingData = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                subtitlesDisabled = false,
                currentFontsDir = null,
            )
        }
        loadJob = viewModelScope.launch {
            loadVideoStream(server)
        }
    }

    fun setSubtitle(url: String?, subtitleLang: String? = null) {
        _uiState.update {
            it.copy(
                currentSubtitleUrl = url,
                subtitlesDisabled = url == null,
                targetSubtitleLang = subtitleLang ?: it.targetSubtitleLang,
                showSettingsOverlay = false
            )
        }
    }

    fun setSpeed(speed: Double) {
        _uiState.update { it.copy(playbackSpeed = speed, isBoostSpeedActive = false) }
    }

    fun setVideoScaleMode(mode: String) {
        val normalized = when (mode) {
            "Zoom" -> "Zoom"
            "Stretch" -> "Stretch"
            else -> "Fit"
        }
        _uiState.update { it.copy(videoScaleMode = normalized) }
        viewModelScope.launch { settingsStore.setVideoScaleMode(normalized) }
    }

    fun setBoostSpeedActive(active: Boolean) {
        _uiState.update { it.copy(isBoostSpeedActive = active) }
    }

    fun setServer(server: String) {
        userPinnedStreamServer = server.lowercase()
        loadJob?.cancel()
        streamLoadGeneration++
        cachedStreamSection = null
        lastAutoServerFallbackKey = null
        _uiState.update {
            it.copy(
                showSettingsOverlay = false,
                isLoadingVideo = true,
                loadingMessage = "Fetching streaming URL...",
                streamingData = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                subtitlesDisabled = false,
                currentFontsDir = null
            )
        }
        loadJob = viewModelScope.launch {
            loadVideoStream(server)
        }
    }

    fun switchServer(serverId: String) {
        val state = _uiState.value
        val resumePosition = lastPlaybackPositionSec.coerceAtLeast(state.savedWatchPosition)
        changeServerWithState(
            newServer = serverId,
            position = resumePosition,
            targetAudioLang = lastPlaybackLanguage,
            targetSubtitleLang = state.currentSubtitleUrl?.let {
                state.availableSubtitles.firstOrNull { s -> s.url == it }?.title
                    ?: state.availableSubtitles.firstOrNull { s -> s.url == it }?.resolvedLang
            },
            targetSubtitleLangCode = state.currentSubtitleUrl?.let {
                state.availableSubtitles.firstOrNull { s -> s.url == it }?.language
                    ?: state.availableSubtitles.firstOrNull { s -> s.url == it }?.lang
            },
        )
    }

    fun changeServerWithState(
        newServer: String,
        position: Double,
        targetAudioLang: String?,
        targetSubtitleLang: String?,
        targetSubtitleLangCode: String? = null
    ) {
        userPinnedStreamServer = newServer.lowercase()
        loadJob?.cancel()
        streamLoadGeneration++
        cachedStreamSection = null
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
                subtitlesDisabled = false,
                currentFontsDir = null
            )
        }
        loadJob = viewModelScope.launch {
            loadVideoStream(newServer)
        }
    }

    fun tryNextServerAfterPlaybackFailure(position: Double) {
        val current = _uiState.value.currentServer.lowercase()
        val fallbackKey = "$current:${_uiState.value.currentEpisodeNumber}"
        if (lastAutoServerFallbackKey == fallbackKey) {
            println("[WatchVM] playback failed again on $current; not auto-falling back repeatedly")
            return
        }
        val failedBase = current.removeSuffix("-dub")
        val servers = getAvailableServers()
            .map { it.id.lowercase() }
            .filter {
                it.isNotBlank() &&
                        it != current &&
                        it != "offline" &&
                        it.removeSuffix("-dub") != failedBase
            }
            .distinct()
        if (servers.isEmpty()) return

        val priority = serverRepository.getFallbackPriority().map { it.lowercase() }
        val premiumOrder = listOf("anitaku-1", "anitaku", "anikage")
        val preferredNext = if (current in premiumOrder) {
            premiumOrder
                .drop(premiumOrder.indexOf(current) + 1)
                .firstOrNull { it in servers }
        } else {
            premiumOrder.firstOrNull { it in servers }
        }
        val next = preferredNext
            ?: priority.firstOrNull { it in servers }
            ?: servers.first()

        lastAutoServerFallbackKey = fallbackKey
        if (userPinnedStreamServer == current) {
            userPinnedStreamServer = null
        }
        println("[WatchVM] playback failed on $current, trying next server=$next")
        changeServerWithState(
            newServer = next,
            position = position,
            targetAudioLang = _uiState.value.targetLang,
            targetSubtitleLang = _uiState.value.targetSubtitleLang,
            targetSubtitleLangCode = _uiState.value.targetSubtitleLangCode,
        )
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
        lastPlaybackSurfaceChangeMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    /**
     * Auto-next / natural EOF only — ignores mpv end-of-file from failed streams (Suzu token expiry, etc.).
     */
    fun tryAutoAdvanceToNextEpisode(
        positionSec: Double,
        durationSec: Double,
        peakPositionSec: Double = positionSec,
        lastUserSeekAtMs: Long = 0L,
    ): Boolean {
        if (!_uiState.value.autoNext) return false
        if (_uiState.value.offlinePath != null) return false
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (now - lastPlaybackSurfaceChangeMs < 12_000L) return false
        if (isWithinUserSeekCooldown(lastUserSeekAtMs, now)) return false
        if (!watchedEnoughForAutoNext(positionSec, durationSec, peakPositionSec)) return false
        if (now - lastAutoEpisodeAdvanceMs < 8_000) return false
        val nextEp = _uiState.value.episodeData?.episodes
            ?.filter { it.number > _uiState.value.currentEpisodeNumber }
            ?.minByOrNull { it.number }
            ?: return false
        lastAutoEpisodeAdvanceMs = now
        println("[WatchVM] auto-advance ep ${_uiState.value.currentEpisodeNumber} → ${nextEp.number} (pos=$positionSec dur=$durationSec)")
        onEpisodeSelected(nextEp.number)
        return true
    }

    fun onEpisodeSelected(episodeNumber: Int) {
        val state = _uiState.value
        if (episodeNumber == state.currentEpisodeNumber &&
            (state.isLoading || state.isLoadingVideo)
        ) {
            return
        }
        if (episodeNumber != state.currentEpisodeNumber) {
            lastKnownDurationSec = 0.0
            lastPlaybackPositionSec = 0.0
            lastSavedProgressKey = null
            lastAutoServerFallbackKey = null
        }
        loadJob?.cancel()
        streamLoadGeneration++
        cachedStreamSection = null
        _uiState.update {
            it.copy(
                currentEpisodeNumber = episodeNumber,
                activeSidePanel = it.activeSidePanel,
                didMarkWatched = false,
                offlinePath = null,
                isLoading = true,
                isLoadingVideo = true,
                loadingMessage = "Fetching episode $episodeNumber...",
                streamingData = null,
                introSkip = null,
                outroSkip = null,
                availableQualities = emptyList(),
                availableSubtitles = emptyList(),
                currentSubtitleUrl = null,
                subtitlesDisabled = false,
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
        if (currState.offlinePath != null) return

        val episodeId = "ep-${currState.currentEpisodeNumber}"
        val animeId = currState.episodeData?.anime?.animeId?.takeIf { it.isNotBlank() }
            ?: currentAnimeId
        val server = currState.currentServer.ifBlank { userPinnedStreamServer.orEmpty() }.ifBlank { "suzu" }
        val effectiveDuration = when {
            duration > 0 -> duration.also { lastKnownDurationSec = it }
            lastKnownDurationSec > 0 -> lastKnownDurationSec
            currentTime >= 5.0 -> (currentTime + 120.0).also { lastKnownDurationSec = it }
            else -> return
        }

        if (animeId.isBlank() || currentTime < 5.0) return

        lastPlaybackPositionSec = currentTime
        lastPlaybackLanguage = language

        val progressKey = "$animeId|$episodeId|${currentTime.toInt()}"
        if (progressKey == lastSavedProgressKey) return
        lastSavedProgressKey = progressKey

        viewModelScope.launch {
            val result = infoService.saveProgress(
                animeId = animeId,
                anilistId = currState.episodeData?.anime?.anilistId ?: currState.episodeData?.anilistId,
                malId = currState.episodeData?.anime?.malId ?: currState.episodeData?.malId,
                episodeId = episodeId,
                currentTime = currentTime,
                duration = effectiveDuration,
                server = server,
                language = language,
            )
            val reward = result?.reward
            if (reward?.granted == true && reward.amount > 0) {
                _uiState.update {
                    it.copy(berriesToast = "+${reward.amount} Berries — episode complete")
                }
            }
        }
    }

    fun dismissBerriesToast() {
        _uiState.update { it.copy(berriesToast = null) }
    }

    /** Call when leaving the player so continue-watching is updated even if playback paused. */
    fun flushWatchProgress(position: Double, duration: Double, language: String = "sub") {
        lastSavedProgressKey = null
        saveProgress(position, duration, language)
    }

    fun flushLatestWatchProgress() {
        if (lastPlaybackPositionSec < 5.0) return
        val duration = lastKnownDurationSec.takeIf { it > 0 }
            ?: (lastPlaybackPositionSec + 120.0)
        flushWatchProgress(lastPlaybackPositionSec, duration, lastPlaybackLanguage)
    }

    /**
     * Called when an episode finishes playing (watched to completion).
     * Syncs progress to MAL/AniList if connected.
     */
    fun markEpisodeWatched(anilistId: Int?, malId: Int?, totalEpisodes: Int?) {
        val episodeNumber = _uiState.value.currentEpisodeNumber
        if (_uiState.value.didMarkWatched) return // prevent double-sync
        _uiState.update { it.copy(didMarkWatched = true) }

        val animeTitle = _uiState.value.episodeData?.anime?.title?.displayTitle?.takeIf { it.isNotBlank() }

        syncManager?.let { mgr ->
            viewModelScope.launch {
                mgr.syncEpisodeComplete(
                    malId = malId,
                    anilistId = anilistId,
                    episodeNumber = episodeNumber,
                    totalEpisodes = totalEpisodes,
                    animeTitle = animeTitle
                )
            }
        }
    }

    fun syncToMAL(malId: Int, totalEpisodes: Int?) {
        val ts = trackingService ?: return
        if (_uiState.value.isSyncingMal) return
        val episode = _uiState.value.currentEpisodeNumber
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingMal = true, syncSnackbar = null) }
            try {
                val success = ts.syncMalProgress(malId, episode, totalEpisodes)
                _uiState.update { it.copy(syncSnackbar = if (success) "Synced to MAL ✓" else "MAL sync failed") }
            } catch (e: Exception) {
                _uiState.update { it.copy(syncSnackbar = "MAL sync failed") }
            } finally {
                _uiState.update { it.copy(isSyncingMal = false) }
            }
        }
    }

    fun syncToAniList(anilistId: Int, totalEpisodes: Int?) {
        val ts = trackingService ?: return
        if (_uiState.value.isSyncingAnilist) return
        val episode = _uiState.value.currentEpisodeNumber
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingAnilist = true, syncSnackbar = null) }
            try {
                val success = ts.syncAnilistProgress(anilistId, episode, totalEpisodes)
                _uiState.update { it.copy(syncSnackbar = if (success) "Synced to AniList ✓" else "AniList sync failed") }
            } catch (e: Exception) {
                _uiState.update { it.copy(syncSnackbar = "AniList sync failed") }
            } finally {
                _uiState.update { it.copy(isSyncingAnilist = false) }
            }
        }
    }

    fun dismissSyncSnackbar() {
        _uiState.update { it.copy(syncSnackbar = null) }
    }

    fun checkSyncTokens() {
        viewModelScope.launch {
            val hasMal = settingsStore.getMalAccessToken() != null
            val hasAni = settingsStore.getAnilistAccessToken() != null
            _uiState.update { it.copy(hasMalToken = hasMal, hasAnilistToken = hasAni) }
        }
    }

    fun updateWatchlistStatus(folder: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingWatchlist = true) }
            try {
                if (currentAnimeId.isEmpty()) return@launch
                val anime = _uiState.value.episodeData?.anime
                val response = watchlistService.updateStatus(
                    animeId = currentAnimeId,
                    folder = folder,
                    anilistId = anime?.anilistId ?: _uiState.value.episodeData?.anilistId,
                    malId = anime?.malId ?: _uiState.value.episodeData?.malId,
                )
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
        _uiState.update { it.copy(autoPlay = enabled) }
        viewModelScope.launch {
            settingsStore.setAutoPlay(enabled)
            syncPreferencesToServer()
        }
    }

    fun setAutoNext(enabled: Boolean) {
        _uiState.update { it.copy(autoNext = enabled) }
        viewModelScope.launch {
            settingsStore.setAutoNext(enabled)
            syncPreferencesToServer()
        }
    }

    fun setAutoSkipIntro(enabled: Boolean) {
        _uiState.update { it.copy(autoSkipIntro = enabled) }
        viewModelScope.launch {
            settingsStore.setAutoSkipIntro(enabled)
            syncPreferencesToServer()
        }
    }

    fun setAutoSkipOutro(enabled: Boolean) {
        _uiState.update { it.copy(autoSkipOutro = enabled) }
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

    /** GET /watch often returns progress.current_time = 0; keep UI-supplied resume (continue-watching) until API sends a real offset. */
    private fun mergeResumeHint(fromApi: Double?, retained: Double): Double {
        val api = fromApi ?: 0.0
        return if (api > 1.0) api else retained
    }

    /** Refetch Aniskip when the player reports a real duration (improves interval matching). */
    fun refreshSkipTimesWhenDurationKnown(durationSec: Double) {
        if (durationSec < 60.0) return
        if (_uiState.value.offlinePath != null) return
        clampSkipRangesToDuration(durationSec)

        val prev = lastSkipEpisodeLengthSec
        if (prev > 0 && kotlin.math.abs(prev - durationSec) < 15.0) return
        lastSkipEpisodeLengthSec = durationSec

        val state = _uiState.value
        attachAniskipTimes(
            streamGeneration = streamLoadGeneration,
            malId = state.episodeData?.malId,
            anilistId = state.episodeData?.anilistId,
            episodeNumber = state.currentEpisodeNumber,
            episodeLengthSec = durationSec,
        )
    }

    fun clampSkipRangesToDuration(durationSec: Double) {
        if (durationSec < 1.0) return
        _uiState.update { state ->
            val intro = clampSkipToDuration(state.introSkip, durationSec) ?: state.introSkip
            val outro = clampSkipToDuration(state.outroSkip, durationSec) ?: state.outroSkip
            val stream = state.streamingData
            val streamIntro = stream?.intro
            val streamOutro = stream?.outro
            if (intro == state.introSkip && outro == state.outroSkip &&
                intro == streamIntro && outro == streamOutro
            ) {
                return@update state
            }
            state.copy(
                introSkip = intro,
                outroSkip = outro,
                streamingData = stream?.copy(intro = intro, outro = outro),
            )
        }
    }

    private suspend fun resolveWatchMalId(malId: Int?, anilistId: Int?): Int? {
        malId?.takeIf { it > 0 }?.let { return it }
        anilistId?.takeIf { it > 0 }?.let { id ->
            aniskipService.resolveMalId(null, id)?.let { return it }
        }
        val details = infoService.getAnimeDetails(currentAnimeId)
        details?.malId?.takeIf { it > 0 }?.let { return it }
        details?.anilistId?.takeIf { it > 0 }?.let { id ->
            return aniskipService.resolveMalId(null, id)
        }
        return null
    }

    private fun attachAniskipTimes(
        streamGeneration: Int,
        malId: Int?,
        anilistId: Int?,
        episodeNumber: Int,
        episodeLengthSec: Double?,
    ) {
        skipTimesJob?.cancel()
        skipTimesJob = viewModelScope.launch {
            val resolvedMal = resolveWatchMalId(malId, anilistId)
            println(
                "[WatchVM] Aniskip lookup malIn=$malId anilist=$anilistId resolvedMal=$resolvedMal ep=$episodeNumber len=${episodeLengthSec ?: 1440.0}",
            )
            val skips = aniskipService.resolveIntroOutro(
                malId = resolvedMal,
                anilistId = null,
                episodeNumber = episodeNumber,
                episodeLengthSec = episodeLengthSec ?: 1440.0,
            )
            if (streamGeneration != streamLoadGeneration) return@launch
            if (skips.intro == null && skips.outro == null) return@launch

            _uiState.update { state ->
                val intro = skips.intro ?: state.introSkip ?: state.streamingData?.intro
                val outro = skips.outro ?: state.outroSkip ?: state.streamingData?.outro
                state.copy(
                    introSkip = intro,
                    outroSkip = outro,
                    streamingData = state.streamingData?.copy(intro = intro, outro = outro),
                )
            }
            val dur = lastSkipEpisodeLengthSec
            if (dur >= 60.0) clampSkipRangesToDuration(dur)
        }
    }

    private fun syncPreferencesToServer() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                val current = settingsService.getSettings()
                val baseSettings = current ?: to.kuudere.anisuge.data.models.UserSettings()

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
