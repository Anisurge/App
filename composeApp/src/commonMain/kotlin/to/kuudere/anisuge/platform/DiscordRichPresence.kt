package to.kuudere.anisuge.platform

data class DiscordPresenceActivity(
    val details: String,
    val state: String,
    val startTimestampMillis: Long? = null,
    val endTimestampMillis: Long? = null,
    val largeImageText: String = "Anisurge",
    val smallImageText: String? = null,
)

expect object DiscordRichPresenceManager {
    fun configureMobile(enabled: Boolean, token: String)
    fun update(activity: DiscordPresenceActivity)
    fun clear()
    fun shutdown()
}
