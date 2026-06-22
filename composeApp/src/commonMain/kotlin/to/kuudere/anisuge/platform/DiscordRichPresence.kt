package to.kuudere.anisuge.platform

data class DiscordPresenceActivity(
    val details: String,
    val state: String,
    val startTimestampMillis: Long? = null,
    val endTimestampMillis: Long? = null,
    val largeImageText: String = "Anisurge",
    val smallImageText: String? = null,
)

data class DiscordPresenceAvailability(
    val supported: Boolean,
    val status: String,
)

expect object DiscordRichPresenceManager {
    val availability: DiscordPresenceAvailability
    val isAuthenticated: Boolean
    fun configure(enabled: Boolean)
    fun authenticate(token: String)
    fun logout()
    fun update(activity: DiscordPresenceActivity)
    fun clear()
    fun shutdown()
}
