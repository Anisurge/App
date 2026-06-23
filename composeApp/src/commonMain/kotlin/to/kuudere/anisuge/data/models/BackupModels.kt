package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable
import to.kuudere.anisuge.player.PlayerEnhancementSettings
import to.kuudere.anisuge.player.PlayerUtilitySettings
import to.kuudere.anisuge.extensions.ExtensionBackupConfig

@Serializable
data class AnisurgeBackup(
    val formatVersion: Int = 2,
    val createdAt: String,
    val appVersion: String,
    val settings: BackupSettings,
    val watchlist: List<WatchlistEntry>,
    val continueWatching: List<ContinueWatchingItem>,
    val extensions: ExtensionBackupConfig = ExtensionBackupConfig(),
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
    val hiddenServerIds: List<String> = emptyList(),
    val notificationsEnabled: Boolean,
    val notificationsNewEpisode: Boolean,
    val notificationsAnnouncement: Boolean,
    val notificationReminderMinutes: Int = 0,
    val trackerAutoSync: Boolean = false,
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
