package to.kuudere.anisuge.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import to.kuudere.anisuge.MainActivity

/**
 * Keeps the process eligible for background work while MAL / AniList / library import sync runs.
 * Actual progress is posted via [to.kuudere.anisuge.platform.updateSyncProgressNotification].
 */
class SyncService : Service() {

    private companion object {
        const val NOTIFICATION_ID = 1004
        const val CHANNEL_ID = "anisurge_sync"
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "Anisurge::SyncWakeLock",
        ).apply { acquire() }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Library sync",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Progress for MAL, AniList, and library import"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        val launch = Intent(this, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = android.app.PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            launch,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                },
        )

        val placeholder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(to.kuudere.anisuge.R.mipmap.ic_launcher_foreground)
            .setContentTitle("Sync in progress")
            .setContentText("This may take a while…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()

        startForeground(NOTIFICATION_ID, placeholder)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
