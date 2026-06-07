package to.kuudere.anisuge.data.services

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.LayoutConfig
import to.kuudere.anisuge.data.models.AiChatUiMessage
import to.kuudere.anisuge.platform.randomInstallUuid

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val ANALYTICS_INSTALL_ID_KEY = stringPreferencesKey("analytics_install_id")
        private val ANALYTICS_LAST_PING_MS_KEY = longPreferencesKey("analytics_last_ping_ms")

        val AUTO_PLAY_KEY = booleanPreferencesKey("auto_play")
        val AUTO_NEXT_KEY = booleanPreferencesKey("auto_next")
        val AUTO_SKIP_INTRO_KEY = booleanPreferencesKey("auto_skip_intro")
        val AUTO_SKIP_OUTRO_KEY = booleanPreferencesKey("auto_skip_outro")
        val DEFAULT_LANG_KEY = booleanPreferencesKey("default_lang")
        val SYNC_PERCENTAGE_KEY = androidx.datastore.preferences.core.intPreferencesKey("sync_percentage")
        val SUBTITLE_SIZE_KEY = androidx.datastore.preferences.core.intPreferencesKey("subtitle_size")
        val SERVER_PRIORITY_KEY = stringPreferencesKey("server_priority")
        val DOWNLOAD_PATH_KEY = stringPreferencesKey("download_path")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATIONS_NEW_EPISODE_KEY = booleanPreferencesKey("notifications_new_episode")
        val NOTIFICATIONS_ANNOUNCEMENT_KEY = booleanPreferencesKey("notifications_announcement")
        val FLOATING_BOTTOM_NAV_KEY = booleanPreferencesKey("floating_bottom_nav")
        val LIQUID_GLASS_BOTTOM_NAV_KEY = booleanPreferencesKey("liquid_glass_bottom_nav")
        val EXPANDED_HERO_CAROUSEL_KEY = booleanPreferencesKey("expanded_hero_carousel")
        val QUICK_ACTION_MENU_KEY = booleanPreferencesKey("quick_action_menu")
        val APP_LOCALE_KEY = stringPreferencesKey("app_locale")
        val PREFER_ROMAJI_ANIME_TITLES_KEY = booleanPreferencesKey("prefer_romaji_anime_titles")
        val SHOW_FULL_ANIME_TITLES_KEY = booleanPreferencesKey("show_full_anime_titles")
        val VIDEO_SCALE_MODE_KEY = stringPreferencesKey("video_scale_mode")
        val THEME_ID_KEY = stringPreferencesKey("theme_id")
        val LEGACY_SCHEDULE_UI_KEY = booleanPreferencesKey("legacy_schedule_ui")
        val HOME_LAYOUT_KEY = stringPreferencesKey("home_layout_v1")
        val AI_CHAT_HISTORY_KEY = stringPreferencesKey("ai_chat_history")

        // MAL tokens
        val MAL_ACCESS_TOKEN_KEY = stringPreferencesKey("mal_access_token")
        val MAL_REFRESH_TOKEN_KEY = stringPreferencesKey("mal_refresh_token")
        val MAL_EXPIRES_AT_KEY = longPreferencesKey("mal_expires_at")
        val MAL_USERNAME_KEY = stringPreferencesKey("mal_username")

        // AniList tokens
        val ANILIST_ACCESS_TOKEN_KEY = stringPreferencesKey("anilist_access_token")
        val ANILIST_EXPIRES_AT_KEY = longPreferencesKey("anilist_expires_at")
        val ANILIST_USERNAME_KEY = stringPreferencesKey("anilist_username")

        // Lunar tokens
        val LUNAR_ACCESS_TOKEN_KEY = stringPreferencesKey("lunar_access_token")
        val LUNAR_REFRESH_TOKEN_KEY = stringPreferencesKey("lunar_refresh_token")
        val LUNAR_EXPIRES_AT_KEY = longPreferencesKey("lunar_expires_at")
        val LUNAR_USERNAME_KEY = stringPreferencesKey("lunar_username")
        val LUNAR_USER_ID_KEY = stringPreferencesKey("lunar_user_id")

        private val json = Json { ignoreUnknownKeys = true }
    }

    val autoPlayFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_PLAY_KEY] ?: false }
    val autoNextFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_NEXT_KEY] ?: true }
    val autoSkipIntroFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_SKIP_INTRO_KEY] ?: false }
    val autoSkipOutroFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_SKIP_OUTRO_KEY] ?: false }
    val defaultLangFlow: Flow<Boolean> = dataStore.data.map { it[DEFAULT_LANG_KEY] ?: false }
    val syncPercentageFlow: Flow<Int> = dataStore.data.map { it[SYNC_PERCENTAGE_KEY] ?: 80 }
    val subtitleSizeFlow: Flow<Int> = dataStore.data.map { it[SUBTITLE_SIZE_KEY] ?: 100 }
    val downloadPathFlow: Flow<String> = dataStore.data.map { it[DOWNLOAD_PATH_KEY] ?: "" }
    val notificationsEnabledFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED_KEY] ?: true }
    val notificationsNewEpisodeFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_NEW_EPISODE_KEY] ?: true }
    val notificationsAnnouncementFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ANNOUNCEMENT_KEY] ?: true }
    val floatingBottomNavFlow: Flow<Boolean> = dataStore.data.map { it[FLOATING_BOTTOM_NAV_KEY] ?: true }
    val liquidGlassBottomNavFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_GLASS_BOTTOM_NAV_KEY] ?: false }
    val expandedHeroCarouselFlow: Flow<Boolean> = dataStore.data.map { it[EXPANDED_HERO_CAROUSEL_KEY] ?: false }
    val quickActionMenuFlow: Flow<Boolean> = dataStore.data.map { it[QUICK_ACTION_MENU_KEY] ?: true }
    val appLocaleFlow: Flow<String> = dataStore.data.map { it[APP_LOCALE_KEY] ?: "en" }
    val preferRomajiAnimeTitlesFlow: Flow<Boolean> = dataStore.data.map { it[PREFER_ROMAJI_ANIME_TITLES_KEY] ?: false }
    val showFullAnimeTitlesFlow: Flow<Boolean> = dataStore.data.map { it[SHOW_FULL_ANIME_TITLES_KEY] ?: false }
    val videoScaleModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[VIDEO_SCALE_MODE_KEY]?.takeIf { it == "Fit" || it == "Zoom" || it == "Stretch" } ?: "Fit"
    }
    val themeIdFlow: Flow<String> = dataStore.data.map { it[THEME_ID_KEY] ?: "default" }
    val legacyScheduleUiFlow: Flow<Boolean> = dataStore.data.map { it[LEGACY_SCHEDULE_UI_KEY] ?: false }
    val homeLayoutFlow: Flow<LayoutConfig> = dataStore.data
        .catch { e ->
            println("[SettingsStore] Failed to read home layout: ${e.message}")
            emit(emptyPreferences())
        }
        .map { preferences ->
            val raw = preferences[HOME_LAYOUT_KEY]
            if (raw.isNullOrBlank()) {
                LayoutConfig.DEFAULT
            } else {
                when (val decoded = LayoutConfigCodec.decode(raw)) {
                    is DecodeResult.Success -> decoded.config.sanitize().mergeWithDefaults()
                    is DecodeResult.VersionTooNew -> LayoutConfig.DEFAULT
                    is DecodeResult.Invalid -> LayoutConfig.DEFAULT
                }
            }
        }
        .distinctUntilChanged()

    val serverPriorityFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        val jsonStr = preferences[SERVER_PRIORITY_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<List<String>>(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun getServerPriority(): List<String> {
        return dataStore.data.map { preferences ->
            val jsonStr = preferences[SERVER_PRIORITY_KEY]
            if (jsonStr != null) {
                try {
                    json.decodeFromString<List<String>>(jsonStr)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }.first()
    }

    suspend fun setServerPriority(priority: List<String>) {
        dataStore.edit { it[SERVER_PRIORITY_KEY] = json.encodeToString(priority) }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        dataStore.edit { it[AUTO_PLAY_KEY] = enabled }
    }

    suspend fun setAutoNext(enabled: Boolean) {
        dataStore.edit { it[AUTO_NEXT_KEY] = enabled }
    }

    suspend fun setAutoSkipIntro(enabled: Boolean) {
        dataStore.edit { it[AUTO_SKIP_INTRO_KEY] = enabled }
    }

    suspend fun setAutoSkipOutro(enabled: Boolean) {
        dataStore.edit { it[AUTO_SKIP_OUTRO_KEY] = enabled }
    }

    suspend fun hasPlaybackPreferenceValues(): Boolean {
        val preferences = dataStore.data.first()
        return preferences[AUTO_PLAY_KEY] != null ||
                preferences[AUTO_NEXT_KEY] != null ||
                preferences[AUTO_SKIP_INTRO_KEY] != null ||
                preferences[AUTO_SKIP_OUTRO_KEY] != null ||
                preferences[DEFAULT_LANG_KEY] != null ||
                preferences[SYNC_PERCENTAGE_KEY] != null
    }

    suspend fun setDefaultLang(enabled: Boolean) {
        dataStore.edit { it[DEFAULT_LANG_KEY] = enabled }
    }

    suspend fun setSyncPercentage(percentage: Int) {
        dataStore.edit { it[SYNC_PERCENTAGE_KEY] = percentage }
    }

    suspend fun setSubtitleSize(sizePercent: Int) {
        dataStore.edit { it[SUBTITLE_SIZE_KEY] = sizePercent.coerceIn(60, 200) }
    }

    suspend fun setDownloadPath(path: String) {
        dataStore.edit { it[DOWNLOAD_PATH_KEY] = path }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED_KEY] = enabled }
    }

    suspend fun setNotificationsNewEpisode(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_NEW_EPISODE_KEY] = enabled }
    }

    suspend fun setNotificationsAnnouncement(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ANNOUNCEMENT_KEY] = enabled }
    }

    suspend fun setFloatingBottomNav(enabled: Boolean) {
        dataStore.edit { it[FLOATING_BOTTOM_NAV_KEY] = enabled }
    }

    suspend fun setLiquidGlassBottomNav(enabled: Boolean) {
        dataStore.edit { it[LIQUID_GLASS_BOTTOM_NAV_KEY] = enabled }
    }

    suspend fun setExpandedHeroCarousel(enabled: Boolean) {
        dataStore.edit { it[EXPANDED_HERO_CAROUSEL_KEY] = enabled }
    }

    suspend fun setQuickActionMenu(enabled: Boolean) {
        dataStore.edit { it[QUICK_ACTION_MENU_KEY] = enabled }
    }

    suspend fun setAppLocale(localeCode: String) {
        dataStore.edit { it[APP_LOCALE_KEY] = localeCode }
    }

    suspend fun setPreferRomajiAnimeTitles(enabled: Boolean) {
        dataStore.edit { it[PREFER_ROMAJI_ANIME_TITLES_KEY] = enabled }
    }

    suspend fun setShowFullAnimeTitles(enabled: Boolean) {
        dataStore.edit { it[SHOW_FULL_ANIME_TITLES_KEY] = enabled }
    }

    suspend fun setVideoScaleMode(mode: String) {
        dataStore.edit {
            it[VIDEO_SCALE_MODE_KEY] = when (mode) {
                "Zoom" -> "Zoom"
                "Stretch" -> "Stretch"
                else -> "Fit"
            }
        }
    }

    suspend fun setThemeId(themeId: String) {
        dataStore.edit { it[THEME_ID_KEY] = themeId }
    }

    suspend fun setLegacyScheduleUi(enabled: Boolean) {
        dataStore.edit { it[LEGACY_SCHEDULE_UI_KEY] = enabled }
    }

    suspend fun setHomeLayout(config: LayoutConfig) {
        dataStore.edit { it[HOME_LAYOUT_KEY] = LayoutConfigCodec.encode(config) }
    }

    suspend fun healHomeLayoutIfNeeded(seen: LayoutConfig) {
        val raw = dataStore.data.first()[HOME_LAYOUT_KEY] ?: return
        when (val decoded = LayoutConfigCodec.decode(raw)) {
            is DecodeResult.Invalid -> {
                dataStore.edit { it[HOME_LAYOUT_KEY] = LayoutConfigCodec.encode(LayoutConfig.DEFAULT) }
            }

            is DecodeResult.Success -> {
                val healed = decoded.config.sanitize().mergeWithDefaults()
                if (healed != decoded.config || healed != seen) {
                    dataStore.edit { it[HOME_LAYOUT_KEY] = LayoutConfigCodec.encode(healed) }
                }
            }

            is DecodeResult.VersionTooNew -> Unit
        }
    }

    suspend fun getOrCreateAnalyticsInstallId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[ANALYTICS_INSTALL_ID_KEY]
        if (!existing.isNullOrBlank()) return existing
        val id = randomInstallUuid()
        dataStore.edit { it[ANALYTICS_INSTALL_ID_KEY] = id }
        return id
    }

    suspend fun getAnalyticsLastPingMs(): Long = dataStore.data.first()[ANALYTICS_LAST_PING_MS_KEY] ?: 0L

    suspend fun setAnalyticsLastPingMs(epochMs: Long) {
        dataStore.edit { it[ANALYTICS_LAST_PING_MS_KEY] = epochMs }
    }

    fun notificationsEnabledBlocking(): Boolean {
        return kotlinx.coroutines.runBlocking { notificationsEnabledFlow.first() }
    }

    // ── MAL Tokens ─────────────────────────────────────────────────────────────
    suspend fun getMalAccessToken(): String? = dataStore.data.first()[MAL_ACCESS_TOKEN_KEY]
    suspend fun getMalRefreshToken(): String? = dataStore.data.first()[MAL_REFRESH_TOKEN_KEY]
    suspend fun getMalExpiresAt(): Long = dataStore.data.first()[MAL_EXPIRES_AT_KEY] ?: 0L
    suspend fun getMalUsername(): String? = dataStore.data.first()[MAL_USERNAME_KEY]
    suspend fun getMalIsExpired(): Boolean {
        val expiresAt = getMalExpiresAt()
        return expiresAt > 0 && to.kuudere.anisuge.utils.currentTimeMillis() > expiresAt - 60000
    }

    suspend fun saveMalTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        dataStore.edit {
            it[MAL_ACCESS_TOKEN_KEY] = accessToken
            it[MAL_REFRESH_TOKEN_KEY] = refreshToken
            it[MAL_EXPIRES_AT_KEY] = to.kuudere.anisuge.utils.currentTimeMillis() + (expiresIn * 1000)
        }
    }

    suspend fun saveMalTokensWithRefresh(accessToken: String, refreshToken: String, expiresAt: Long) {
        dataStore.edit {
            it[MAL_ACCESS_TOKEN_KEY] = accessToken
            it[MAL_REFRESH_TOKEN_KEY] = refreshToken
            it[MAL_EXPIRES_AT_KEY] = expiresAt
        }
    }

    suspend fun saveMalUsername(username: String) {
        dataStore.edit { it[MAL_USERNAME_KEY] = username }
    }

    suspend fun clearMalTokens() {
        dataStore.edit {
            it.remove(MAL_ACCESS_TOKEN_KEY)
            it.remove(MAL_REFRESH_TOKEN_KEY)
            it.remove(MAL_EXPIRES_AT_KEY)
            it.remove(MAL_USERNAME_KEY)
        }
    }

    // ── AniList Tokens ─────────────────────────────────────────────────────────
    suspend fun getAnilistAccessToken(): String? = dataStore.data.first()[ANILIST_ACCESS_TOKEN_KEY]
    suspend fun getAnilistExpiresAt(): Long = dataStore.data.first()[ANILIST_EXPIRES_AT_KEY] ?: 0L
    suspend fun getAnilistUsername(): String? = dataStore.data.first()[ANILIST_USERNAME_KEY]
    suspend fun getAnilistIsExpired(): Boolean {
        val expiresAt = getAnilistExpiresAt()
        return expiresAt > 0 && to.kuudere.anisuge.utils.currentTimeMillis() > expiresAt
    }

    suspend fun saveAnilistTokens(accessToken: String, expiresIn: Long) {
        dataStore.edit {
            it[ANILIST_ACCESS_TOKEN_KEY] = accessToken
            it[ANILIST_EXPIRES_AT_KEY] = to.kuudere.anisuge.utils.currentTimeMillis() + (expiresIn * 1000)
        }
    }

    suspend fun saveAnilistUsername(username: String) {
        dataStore.edit { it[ANILIST_USERNAME_KEY] = username }
    }

    suspend fun clearAnilistTokens() {
        dataStore.edit {
            it.remove(ANILIST_ACCESS_TOKEN_KEY)
            it.remove(ANILIST_EXPIRES_AT_KEY)
            it.remove(ANILIST_USERNAME_KEY)
        }
    }

    // ── Lunar Tokens ────────────────────────────────────────────────────────────
    suspend fun getLunarAccessToken(): String? = dataStore.data.first()[LUNAR_ACCESS_TOKEN_KEY]
    suspend fun getLunarRefreshToken(): String? = dataStore.data.first()[LUNAR_REFRESH_TOKEN_KEY]
    suspend fun getLunarExpiresAt(): Long = dataStore.data.first()[LUNAR_EXPIRES_AT_KEY] ?: 0L
    suspend fun getLunarUsername(): String? = dataStore.data.first()[LUNAR_USERNAME_KEY]
    suspend fun getLunarUserId(): String? = dataStore.data.first()[LUNAR_USER_ID_KEY]
    suspend fun getLunarIsExpired(): Boolean {
        val expiresAt = getLunarExpiresAt()
        return expiresAt > 0 && to.kuudere.anisuge.utils.currentTimeMillis() > expiresAt - 60000
    }

    suspend fun saveLunarTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        dataStore.edit {
            it[LUNAR_ACCESS_TOKEN_KEY] = accessToken
            it[LUNAR_REFRESH_TOKEN_KEY] = refreshToken
            it[LUNAR_EXPIRES_AT_KEY] = to.kuudere.anisuge.utils.currentTimeMillis() + (expiresIn * 1000)
        }
    }

    suspend fun saveLunarUsernameAndId(username: String, userId: String) {
        dataStore.edit {
            it[LUNAR_USERNAME_KEY] = username
            it[LUNAR_USER_ID_KEY] = userId
        }
    }

    suspend fun clearLunarTokens() {
        dataStore.edit {
            it.remove(LUNAR_ACCESS_TOKEN_KEY)
            it.remove(LUNAR_REFRESH_TOKEN_KEY)
            it.remove(LUNAR_EXPIRES_AT_KEY)
            it.remove(LUNAR_USERNAME_KEY)
            it.remove(LUNAR_USER_ID_KEY)
        }
    }

    // ── AI Chat History ─────────────────────────────────────────────────────────
    val aiChatHistoryFlow: Flow<List<AiChatUiMessage>> = dataStore.data.map { preferences ->
        val jsonStr = preferences[AI_CHAT_HISTORY_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<List<AiChatUiMessage>>(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun getAiChatHistory(): List<AiChatUiMessage> {
        return dataStore.data.map { preferences ->
            val jsonStr = preferences[AI_CHAT_HISTORY_KEY]
            if (jsonStr != null) {
                try {
                    json.decodeFromString<List<AiChatUiMessage>>(jsonStr)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }.first()
    }

    suspend fun setAiChatHistory(history: List<AiChatUiMessage>) {
        dataStore.edit { it[AI_CHAT_HISTORY_KEY] = json.encodeToString(history) }
    }

    suspend fun clearAiChatHistory() {
        dataStore.edit { it.remove(AI_CHAT_HISTORY_KEY) }
    }
}
