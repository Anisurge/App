package to.kuudere.anisuge.platform

actual object DiscordRichPresenceManager {
    actual fun configureMobile(enabled: Boolean, token: String) = Unit
    actual fun update(activity: DiscordPresenceActivity) = Unit
    actual fun clear() = Unit
    actual fun shutdown() = Unit
}
