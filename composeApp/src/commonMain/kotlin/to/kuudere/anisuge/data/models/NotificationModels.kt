package to.kuudere.anisuge.data.models

enum class NotificationType(val channelId: String, val label: String) {
    NEW_EPISODE("new_episode", "New Episodes"),
    DONATION("donation", "Donations"),
    ANNOUNCEMENT("announcement", "Announcements"),
    MAINTENANCE("maintenance", "Maintenance")
}
