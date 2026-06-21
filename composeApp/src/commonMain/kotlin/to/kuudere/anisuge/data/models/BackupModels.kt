package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable
import to.kuudere.anisuge.player.PlayerEnhancementSettings
import to.kuudere.anisuge.player.PlayerUtilitySettings

@Serializable
data class AnisurgeBackup(
    val formatVersion: Int = 1,
    val createdAt: String,
    val appVersion: String,
    val settings: BackupSettings,
    val watchlist: List<WatchlistEntry>,
    val continueWatching: List<ContinueWatchingItem>,
)

@Serializable
data class BackupSettings(
    val autoPlay: Boolean,
    val autoNext: Boolean,
    val autoSkipIntro: Boolean,
    val autoSkipOutro: Boolean,
    val defaultDub: Boolean,
    val syncPercentage: Int,
    val subtitleSize: Int,
    val serverPriority: List<String>,
    val notificationsEnabled: Boolean,
    val notificationsNewEpisode: Boolean,
    val notificationsAnnouncement: Boolean,
    val floatingBottomNav: Boolean,
    val liquidGlassBottomNav: Boolean,
    val expandedHeroCarousel: Boolean,
    val quickActionMenu: Boolean,
    val appLocale: String,
    val preferRomajiTitles: Boolean,
    val showFullTitles: Boolean,
    val videoScaleMode: String,
    val themeId: String,
    val legacyScheduleUi: Boolean,
    val homeLayoutJson: String,
    val playerEnhancements: PlayerEnhancementSettings,
    val playerUtilities: PlayerUtilitySettings,
)

data class BackupRestoreResult(
    val settingsImported: Int,
    val watchlistImported: Int,
    val progressImported: Int,
    val skipped: Int,
    val failed: Int,
)
