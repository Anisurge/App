package to.kuudere.anisuge.platform

actual object DiscordRichPresenceManager {
    actual fun update(activity: DiscordPresenceActivity) = Unit
    actual fun clear() = Unit
    actual fun shutdown() = Unit
}
