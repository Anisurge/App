package to.kuudere.anisuge.platform

actual object DiscordRichPresenceManager {
    actual val availability = DiscordPresenceAvailability(false, "Discord Rich Presence is unavailable on iOS")
    actual val isAuthenticated: Boolean get() = false
    actual fun configure(enabled: Boolean) = Unit
    actual fun authenticate(token: String) = Unit
    actual fun logout() = Unit
    actual fun update(activity: DiscordPresenceActivity) = Unit
    actual fun clear() = Unit
    actual fun shutdown() = Unit
}
