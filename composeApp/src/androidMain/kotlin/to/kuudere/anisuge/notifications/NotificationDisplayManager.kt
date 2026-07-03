package to.kuudere.anisuge.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
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
        episodeNumber: Int? = null,
        actionUrl: String? = null,
        actionLabel: String? = null,
        mediaType: String? = null,
        mediaUrl: String? = null,
        imageUrl: String? = null,
        referenceId: String? = null,
        campaign: String? = null,
    ) {
        if (!hasNotificationPermission()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val normalizedType = type.uppercase()
        val channelId = when (normalizedType) {
            "NEW_EPISODE" -> NotificationChannels.NEW_EPISODE
            "DONATION" -> NotificationChannels.DONATION
            "ANNOUNCEMENT" -> NotificationChannels.ANNOUNCEMENT
            "MAINTENANCE" -> NotificationChannels.MAINTENANCE
            else -> NotificationChannels.ANNOUNCEMENT
        }

        val deepLink = buildDeepLink(normalizedType, animeId, episodeNumber, actionUrl)
        val notifId = NOTIF_ID_BASE + (title.hashCode() and 0xFFFF)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("type", type)
            putExtra("title", title)
            putExtra("body", body)
            animeId?.let { putExtra("animeId", it) }
            episodeNumber?.let { putExtra("episodeNumber", it.toString()) }
            actionUrl?.let { putExtra("actionUrl", it) }
            actionLabel?.let { putExtra("actionLabel", it) }
            mediaType?.let { putExtra("mediaType", it) }
            mediaUrl?.let { putExtra("mediaUrl", it) }
            imageUrl?.let { putExtra("imageUrl", it) }
            referenceId?.let { putExtra("referenceId", it) }
            campaign?.let { putExtra("campaign", it) }
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

        loadBitmap(imageUrl)?.let { bitmap ->
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(body)
            )
        }

        when (normalizedType) {
            "NEW_EPISODE" -> {
                builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                if (animeId != null) {
                    val watchUri = "anisurge://anime/$animeId/episode/${episodeNumber ?: 1}"
                    val watchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUri), context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("type", type)
                        putExtra("title", title)
                        putExtra("body", body)
                        putExtra("animeId", animeId)
                        putExtra("episodeNumber", (episodeNumber ?: 1).toString())
                        actionUrl?.let { putExtra("actionUrl", it) }
                        actionLabel?.let { putExtra("actionLabel", it) }
                        mediaType?.let { putExtra("mediaType", it) }
                        mediaUrl?.let { putExtra("mediaUrl", it) }
                        imageUrl?.let { putExtra("imageUrl", it) }
                        referenceId?.let { putExtra("referenceId", it) }
                        campaign?.let { putExtra("campaign", it) }
                    }
                    val watchPending = PendingIntent.getActivity(
                        context, notifId + 1, watchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    )
                    builder.addAction(0, actionLabel ?: "Watch Now", watchPending)
                }
            }
            "DONATION" -> builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            "ANNOUNCEMENT" -> builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            "MAINTENANCE" -> builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        manager.notify(notifId, builder.build())
    }

    private fun loadBitmap(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank() || !imageUrl.startsWith("http", ignoreCase = true)) return null
        return runCatching {
            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
            val targetW = 512
            val targetH = 512
            val scaleFactor = maxOf(opts.outWidth / targetW, opts.outHeight / targetH).coerceAtLeast(1)
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = scaleFactor
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val conn2 = URL(imageUrl).openConnection() as HttpURLConnection
            conn2.connectTimeout = 5_000
            conn2.readTimeout = 5_000
            conn2.instanceFollowRedirects = true
            conn2.inputStream.use { BitmapFactory.decodeStream(it, null, opts2) }
        }.getOrNull()
    }

    private fun buildDeepLink(type: String, animeId: String?, episodeNumber: Int?, actionUrl: String?): String = when {
        !actionUrl.isNullOrBlank() -> actionUrl
        animeId != null && episodeNumber != null -> "anisurge://anime/$animeId/episode/$episodeNumber"
        animeId != null -> "anisurge://anime/$animeId"
        type == "DONATION" -> "anisurge://donate"
        type == "MAINTENANCE" -> "anisurge://maintenance"
        else -> "anisurge://announcement"
    }
}
