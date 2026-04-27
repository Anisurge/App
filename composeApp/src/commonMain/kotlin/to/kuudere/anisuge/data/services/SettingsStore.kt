package to.kuudere.anisuge.data.services

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    companion object {
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
        val NOTIFICATIONS_DONATION_KEY = booleanPreferencesKey("notifications_donation")
        val NOTIFICATIONS_ANNOUNCEMENT_KEY = booleanPreferencesKey("notifications_announcement")
        val NOTIFICATIONS_MAINTENANCE_KEY = booleanPreferencesKey("notifications_maintenance")
        val FLOATING_BOTTOM_NAV_KEY = booleanPreferencesKey("floating_bottom_nav")
        val LIQUID_GLASS_BOTTOM_NAV_KEY = booleanPreferencesKey("liquid_glass_bottom_nav")

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

    // Notification preferences
    val notificationsEnabledFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED_KEY] ?: true }
    val notificationsNewEpisodeFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_NEW_EPISODE_KEY] ?: true }
    val notificationsDonationFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_DONATION_KEY] ?: true }
    val notificationsAnnouncementFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ANNOUNCEMENT_KEY] ?: true }
    val notificationsMaintenanceFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_MAINTENANCE_KEY] ?: true }
    val floatingBottomNavFlow: Flow<Boolean> = dataStore.data.map { it[FLOATING_BOTTOM_NAV_KEY] ?: true }
    val liquidGlassBottomNavFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_GLASS_BOTTOM_NAV_KEY] ?: false }

    /**
     * Flow of user-defined server priority list.
     * Returns empty list if not set (use default priority).
     */
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

    /**
     * Get current server priority synchronously
     */
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

    /**
     * Save server priority list
     */
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

    // Notification setters
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED_KEY] = enabled }
    }

    suspend fun setNotificationsNewEpisode(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_NEW_EPISODE_KEY] = enabled }
    }

    suspend fun setNotificationsDonation(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_DONATION_KEY] = enabled }
    }

    suspend fun setNotificationsAnnouncement(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ANNOUNCEMENT_KEY] = enabled }
    }

    suspend fun setNotificationsMaintenance(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_MAINTENANCE_KEY] = enabled }
    }

    suspend fun setFloatingBottomNav(enabled: Boolean) {
        dataStore.edit { it[FLOATING_BOTTOM_NAV_KEY] = enabled }
    }

    suspend fun setLiquidGlassBottomNav(enabled: Boolean) {
        dataStore.edit { it[LIQUID_GLASS_BOTTOM_NAV_KEY] = enabled }
    }

    // Blocking reads for FCM service (runs on worker thread, safe to block)
    fun notificationsEnabledBlocking(): Boolean {
        return kotlinx.coroutines.runBlocking { notificationsEnabledFlow.first() }
    }
}
