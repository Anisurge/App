package to.kuudere.anisuge.data.services

import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.AnisurgeBackup
import to.kuudere.anisuge.data.models.BackupRestoreResult
import to.kuudere.anisuge.data.models.BackupSettings
import to.kuudere.anisuge.data.models.ContinueWatchingItem
import to.kuudere.anisuge.data.models.WatchlistEntry
import to.kuudere.anisuge.platform.AppVersion

class BackupService(
    private val settingsStore: SettingsStore,
    private val watchlistService: WatchlistService,
    private val homeService: HomeService,
    private val infoService: InfoService,
    private val extensionManager: to.kuudere.anisuge.extensions.ExtensionManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportJson(): String {
        val settings = BackupSettings(
            autoPlay = settingsStore.autoPlayFlow.first(),
            autoNext = settingsStore.autoNextFlow.first(),
            autoSkipIntro = settingsStore.autoSkipIntroFlow.first(),
            autoSkipOutro = settingsStore.autoSkipOutroFlow.first(),
            defaultDub = settingsStore.defaultLangFlow.first(),
            syncPercentage = settingsStore.syncPercentageFlow.first(),
            subtitleSize = settingsStore.subtitleSizeFlow.first(),
            serverPriority = settingsStore.serverPriorityFlow.first(),
            hiddenServerIds = settingsStore.hiddenServerIdsFlow.first().toList(),
            notificationsEnabled = settingsStore.notificationsEnabledFlow.first(),
            notificationsNewEpisode = settingsStore.notificationsNewEpisodeFlow.first(),
            notificationsAnnouncement = settingsStore.notificationsAnnouncementFlow.first(),
            notificationReminderMinutes = settingsStore.notificationReminderMinutesFlow.first(),
            trackerAutoSync = settingsStore.trackerAutoSyncFlow.first(),
            floatingBottomNav = settingsStore.floatingBottomNavFlow.first(),
            liquidGlassBottomNav = settingsStore.liquidGlassBottomNavFlow.first(),
            expandedHeroCarousel = settingsStore.expandedHeroCarouselFlow.first(),
            homeDesign = settingsStore.homeDesignFlow.first(),
            quickActionMenu = settingsStore.quickActionMenuFlow.first(),
            appLocale = settingsStore.appLocaleFlow.first(),
            preferRomajiTitles = settingsStore.preferRomajiAnimeTitlesFlow.first(),
            showFullTitles = settingsStore.showFullAnimeTitlesFlow.first(),
            videoScaleMode = settingsStore.videoScaleModeFlow.first(),
            themeId = settingsStore.themeIdFlow.first(),
            legacyScheduleUi = settingsStore.legacyScheduleUiFlow.first(),
            homeLayoutJson = LayoutConfigCodec.encode(settingsStore.homeLayoutFlow.first()),
            playerEnhancements = settingsStore.playerEnhancementsFlow.first(),
            playerUtilities = settingsStore.playerUtilitiesFlow.first(),
        )
        return json.encodeToString(
            AnisurgeBackup(
                createdAt = Clock.System.now().toString(),
                appVersion = AppVersion,
                settings = settings,
                watchlist = fetchAllWatchlist(),
                continueWatching = homeService.fetchAllContinueWatching(),
                extensions = extensionManager.exportConfig(),
            )
        )
    }

    suspend fun restore(raw: String): BackupRestoreResult {
        val backup = json.decodeFromString<AnisurgeBackup>(raw)
        require(backup.formatVersion in 1..2) { "Unsupported backup format ${backup.formatVersion}" }
        restoreSettings(backup.settings)
        extensionManager.restoreConfig(backup.extensions)

        val existingWatchlist = fetchAllWatchlist().associateBy { it.effectiveAnimeId }
        var watchlistImported = 0
        var progressImported = 0
        var skipped = 0
        var failed = 0

        backup.watchlist.forEach { entry ->
            val animeId = entry.effectiveAnimeId
            val folder = entry.displayFolder
            val existing = existingWatchlist[animeId]
            if (animeId.isBlank() || folder.isNullOrBlank()) {
                failed++
            } else if (existing != null && !isNewer(entry.lastUpdated, existing.lastUpdated)) {
                skipped++
            } else {
                val result = watchlistService.updateStatus(
                    animeId = animeId,
                    folder = folder,
                    notes = entry.notes,
                    anilistId = entry.anime.anilistId,
                    malId = entry.anime.malId,
                    suppressTrackerSync = true,
                )
                if (result != null) watchlistImported++ else failed++
            }
        }

        val existingProgress = homeService.fetchAllContinueWatching()
            .associateBy { progressKey(it) }
        backup.continueWatching.forEach { item ->
            val animeId = item.effectiveAnimeId
            val episodeId = item.episodeId
            val existing = existingProgress[progressKey(item)]
            val shouldImport = existing == null ||
                item.progress > existing.progress ||
                isNewer(item.updatedAt, existing.updatedAt)
            if (animeId.isBlank() || episodeId.isBlank()) {
                failed++
            } else if (!shouldImport) {
                skipped++
            } else {
                val result = infoService.saveProgress(
                    animeId = animeId,
                    anilistId = item.anime.anilistId,
                    malId = item.anime.malId,
                    episodeId = episodeId,
                    currentTime = maxOf(item.progress, existing?.progress ?: 0.0),
                    duration = maxOf(item.duration, existing?.duration ?: 0.0),
                    server = item.server?.takeIf { it.isNotBlank() } ?: "surge",
                    language = item.language?.takeIf { it.isNotBlank() } ?: "sub",
                )
                if (result != null) progressImported++ else failed++
            }
        }

        return BackupRestoreResult(
            settingsImported = 25,
            watchlistImported = watchlistImported,
            progressImported = progressImported,
            skipped = skipped,
            failed = failed,
        )
    }

    private suspend fun fetchAllWatchlist(): List<WatchlistEntry> {
        val pageSize = 100
        val first = watchlistService.getWatchlist(limit = pageSize) ?: return emptyList()
        val all = first.entries.toMutableList()
        var offset = pageSize
        while (all.size < first.total) {
            val page = watchlistService.getWatchlist(limit = pageSize, offset = offset) ?: break
            if (page.entries.isEmpty()) break
            all += page.entries
            offset += pageSize
        }
        return all
    }

    private suspend fun restoreSettings(value: BackupSettings) {
        settingsStore.setAutoPlay(value.autoPlay)
        settingsStore.setAutoNext(value.autoNext)
        settingsStore.setAutoSkipIntro(value.autoSkipIntro)
        settingsStore.setAutoSkipOutro(value.autoSkipOutro)
        settingsStore.setDefaultLang(value.defaultDub)
        settingsStore.setSyncPercentage(value.syncPercentage)
        settingsStore.setSubtitleSize(value.subtitleSize)
        settingsStore.setServerPriority(value.serverPriority)
        settingsStore.setHiddenServerIds(value.hiddenServerIds.map { it.lowercase() }.toSet())
        settingsStore.setNotificationsEnabled(value.notificationsEnabled)
        settingsStore.setNotificationsNewEpisode(value.notificationsNewEpisode)
        settingsStore.setNotificationsAnnouncement(value.notificationsAnnouncement)
        settingsStore.setNotificationReminderMinutes(value.notificationReminderMinutes)
        settingsStore.setTrackerAutoSync(value.trackerAutoSync)
        settingsStore.setFloatingBottomNav(value.floatingBottomNav)
        settingsStore.setLiquidGlassBottomNav(value.liquidGlassBottomNav)
        settingsStore.setExpandedHeroCarousel(value.expandedHeroCarousel)
        settingsStore.setHomeDesign(value.homeDesign)
        settingsStore.setQuickActionMenu(value.quickActionMenu)
        settingsStore.setAppLocale(value.appLocale)
        settingsStore.setPreferRomajiAnimeTitles(value.preferRomajiTitles)
        settingsStore.setShowFullAnimeTitles(value.showFullTitles)
        settingsStore.setVideoScaleMode(value.videoScaleMode)
        settingsStore.setThemeId(value.themeId)
        settingsStore.setLegacyScheduleUi(value.legacyScheduleUi)
        val layout = when (val decoded = LayoutConfigCodec.decode(value.homeLayoutJson)) {
            is DecodeResult.Success -> decoded.config
            else -> to.kuudere.anisuge.data.models.LayoutConfig.DEFAULT
        }
        settingsStore.setHomeLayout(layout)
        settingsStore.setPlayerEnhancements(value.playerEnhancements)
        settingsStore.setPlayerUtilities(value.playerUtilities)
    }

    private fun progressKey(item: ContinueWatchingItem): String =
        "${item.effectiveAnimeId.lowercase()}|${item.episodeId.lowercase()}"

    private fun isNewer(candidate: String?, existing: String?): Boolean =
        !candidate.isNullOrBlank() && (existing.isNullOrBlank() || candidate > existing)
}
