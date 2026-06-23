package to.kuudere.anisuge.screens.settings

data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val category: String,
    val keywords: List<String>,
    val tab: SettingsTab,
)

val SETTINGS_SEARCH_ENTRIES = listOf(
    SettingsSearchEntry("default-audio", "Default audio", "Preferences", listOf("sub", "dub", "language", "playback"), SettingsTab.Preferences),
    SettingsSearchEntry("autoplay", "Auto play", "Preferences", listOf("playback", "episode"), SettingsTab.Preferences),
    SettingsSearchEntry("autonext", "Auto next episode", "Preferences", listOf("playback", "next"), SettingsTab.Preferences),
    SettingsSearchEntry("skip-intro", "Skip intro", "Preferences", listOf("opening", "aniskip"), SettingsTab.Preferences),
    SettingsSearchEntry("skip-outro", "Skip outro", "Preferences", listOf("ending", "credits"), SettingsTab.Preferences),
    SettingsSearchEntry("audio-language", "Default audio language", "Preferences", listOf("dub", "sub"), SettingsTab.Preferences),
    SettingsSearchEntry("subtitle-size", "Subtitle size", "Preferences", listOf("captions", "text"), SettingsTab.Preferences),
    SettingsSearchEntry("player-enhancements", "Player enhancements", "Preferences", listOf("anime4k", "shader", "brightness", "contrast"), SettingsTab.Preferences),
    SettingsSearchEntry("player-utilities", "Player utilities", "Preferences", listOf("screenshot", "sleep timer", "subtitle delay"), SettingsTab.Preferences),
    SettingsSearchEntry("download-path", "Download path", "Preferences", listOf("folder", "offline"), SettingsTab.Preferences),
    SettingsSearchEntry("ui-style", "UI style", "Appearance", listOf("dantotsu", "redantotsu", "layout", "glass"), SettingsTab.Appearance),
    SettingsSearchEntry("theme", "App theme", "Appearance", listOf("color", "amoled", "light"), SettingsTab.Appearance),
    SettingsSearchEntry("navigation", "Navigation style", "Appearance", listOf("bottom bar", "liquid glass"), SettingsTab.Appearance),
    SettingsSearchEntry("home-layout", "Home layout", "Appearance", listOf("rows", "reorder"), SettingsTab.Appearance),
    SettingsSearchEntry("dantotsu-header", "Dantotsu home header", "Appearance", listOf("dantotsu", "banner", "header", "kenburns"), SettingsTab.Appearance),
    SettingsSearchEntry("compact-cards", "Compact cards", "Appearance", listOf("dantotsu", "compact", "small card", "score"), SettingsTab.Appearance),
    SettingsSearchEntry("score-badges", "Score badges", "Appearance", listOf("score", "rating", "star"), SettingsTab.Appearance),
    SettingsSearchEntry("titles", "Anime title language", "Appearance", listOf("romaji", "english", "full title"), SettingsTab.Appearance),
    SettingsSearchEntry("backup", "Backup and restore", "Backup", listOf("export", "import", "library", "settings"), SettingsTab.Backup),
    SettingsSearchEntry("mal", "MyAnimeList", "Sync", listOf("MAL", "tracker", "import"), SettingsTab.Sync),
    SettingsSearchEntry("anilist", "AniList", "Sync", listOf("tracker", "import"), SettingsTab.Sync),
    SettingsSearchEntry("autosync", "Auto Sync", "Sync", listOf("automatic", "MAL", "AniList", "tracking"), SettingsTab.Sync),
    SettingsSearchEntry("reanime", "ReAnime connection", "Connect", listOf("project r", "library sync"), SettingsTab.Connect),
    SettingsSearchEntry("discord-rpc", "Discord Rich Presence", "Discord", listOf("connect", "activity", "watching", "episode", "RPC"), SettingsTab.Discord),
    SettingsSearchEntry("servers", "Streaming server priority", "Servers", listOf("source", "provider", "fallback"), SettingsTab.Servers),
    SettingsSearchEntry("extensions", "Extensions", "Extensions", listOf("Aniyomi", "CloudStream", "Mangayomi", "Sora", "repository", "plugin"), SettingsTab.Extensions),
    SettingsSearchEntry("storage", "Storage manager", "Storage", listOf("downloads", "cache", "delete"), SettingsTab.Storage),
    SettingsSearchEntry("playback-buffer", "Playback buffer", "Preferences", listOf("cache", "buffer size", "streaming", "stutter"), SettingsTab.Preferences),
    SettingsSearchEntry("notifications", "Notifications", "Notifications", listOf("episode", "announcement", "airing", "reminder", "release alert"), SettingsTab.Notifications),
    SettingsSearchEntry("profile", "Profile", "Profile", listOf("username", "avatar", "bio"), SettingsTab.Profile),
    SettingsSearchEntry("store", "Store", "Store", listOf("frames", "cosmetics"), SettingsTab.Shop),
    SettingsSearchEntry("berries", "Berries", "Berries", listOf("daily claim", "redeem"), SettingsTab.Berries),
)

fun searchSettings(query: String): List<SettingsSearchEntry> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return emptyList()
    return SETTINGS_SEARCH_ENTRIES.filter { entry ->
        val haystack = buildString {
            append(entry.title.lowercase())
            append(' ')
            append(entry.category.lowercase())
            append(' ')
            append(entry.keywords.joinToString(" ").lowercase())
        }
        terms.all(haystack::contains)
    }.take(12)
}
