package to.kuudere.anisuge.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val NEW_EPISODE = "anisurge_new_episode"
    const val DONATION = "anisurge_donation"
    const val ANNOUNCEMENT = "anisurge_announcement"
    const val MAINTENANCE = "anisurge_maintenance"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(NEW_EPISODE, "New Episodes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications when new episodes are available"
                enableVibration(true)
                setShowBadge(true)
            },
            NotificationChannel(DONATION, "Donation Requests", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Donation requests and support appeals"
                enableVibration(false)
                setShowBadge(true)
            },
            NotificationChannel(ANNOUNCEMENT, "Announcements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "App announcements and news"
                enableVibration(true)
                setShowBadge(true)
            },
            NotificationChannel(MAINTENANCE, "Maintenance", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Scheduled maintenance and downtime alerts"
                enableVibration(false)
                setShowBadge(false)
            }
        )

        manager.createNotificationChannels(channels)
    }
}
