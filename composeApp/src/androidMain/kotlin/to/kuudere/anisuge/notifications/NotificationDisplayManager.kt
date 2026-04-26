package to.kuudere.anisuge.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import to.kuudere.anisuge.MainActivity
import to.kuudere.anisuge.R
import to.kuudere.anisuge.utils.hasNotificationPermission

object NotificationDisplayManager {

    private const val NOTIF_ID_BASE = 3000

    fun showNotification(
        context: Context,
        type: String,
        title: String,
        body: String,
        animeId: String? = null,
        episodeNumber: Int? = null
    ) {
        if (!hasNotificationPermission()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = when (type) {
            "NEW_EPISODE" -> NotificationChannels.NEW_EPISODE
            "DONATION" -> NotificationChannels.DONATION
            "ANNOUNCEMENT" -> NotificationChannels.ANNOUNCEMENT
            "MAINTENANCE" -> NotificationChannels.MAINTENANCE
            else -> NotificationChannels.ANNOUNCEMENT
        }

        val deepLink = buildDeepLink(type, animeId, episodeNumber)
        val notifId = NOTIF_ID_BASE + (title.hashCode() and 0xFFFF)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        when (type) {
            "NEW_EPISODE" -> {
                builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                if (animeId != null) {
                    val watchUri = "anisurge://anime/$animeId/episode/${episodeNumber ?: 1}"
                    val watchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUri), context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val watchPending = PendingIntent.getActivity(
                        context, notifId + 1, watchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    )
                    builder.addAction(0, "Watch Now", watchPending)
                }
            }
            "DONATION" -> builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            "ANNOUNCEMENT" -> builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            "MAINTENANCE" -> builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        manager.notify(notifId, builder.build())
    }

    private fun buildDeepLink(type: String, animeId: String?, episodeNumber: Int?): String = when {
        animeId != null && episodeNumber != null -> "anisurge://anime/$animeId/episode/$episodeNumber"
        animeId != null -> "anisurge://anime/$animeId"
        type == "DONATION" -> "anisurge://donate"
        type == "MAINTENANCE" -> "anisurge://maintenance"
        else -> "anisurge://announcement"
    }
}
