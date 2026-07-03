package to.kuudere.anisuge.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationManager
import android.os.Build
import android.app.NotificationChannel
import android.content.Context
import androidx.core.app.NotificationCompat
import to.kuudere.anisuge.MainActivity

class DownloadService : Service() {
    private companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "anisurge_downloads"
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        // Acquire wake lock early; the actual foreground promotion happens in onStartCommand
        // (the recommended place after a startForegroundService call).
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock =
            powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Anisurge::DownloadWakeLock").apply {
                acquire()
            }
    }

    override fun onDestroy() {
        // Remove FG status explicitly and the associated notification.
        stopForeground(true)
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannelIfNeeded()

        val count = intent?.getIntExtra("count", 1) ?: 1
        val prog = intent?.getFloatExtra("progress", 0f) ?: 0f
        val progressInt = (prog * 100).toInt().coerceIn(0, 100)

        val contentTitle = if (count == 1) "Downloading Anime" else "Downloading $count items"
        val contentText = "$progressInt% total progress"

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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(to.kuudere.anisuge.R.mipmap.ic_launcher_foreground)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setProgress(100, progressInt, progressInt <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Active Downloads",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Shows progress of active anime downloads"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
