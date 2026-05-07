package to.kuudere.anisuge.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AnisurgeMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AnisurgeFCM"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: ${token.take(10)}...")
        // Token is managed by FCM — no need to send it to a server
        // since we use topics for broadcast notifications.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.d(TAG, "Received message: ${data.keys}")

        val type = data["type"] ?: return
        val title = data["title"] ?: message.notification?.title ?: "Anisurge"
        val body = data["body"] ?: message.notification?.body ?: return

        // Check if notifications are enabled (master toggle).
        val settingsStore = to.kuudere.anisuge.AppComponent.settingsStore
        if (!settingsStore.notificationsEnabledBlocking()) {
            Log.d(TAG, "Notifications disabled, skipping")
            return
        }

        NotificationDisplayManager.showNotification(
            context = this,
            type = type,
            title = title,
            body = body,
            animeId = data["animeId"],
            episodeNumber = data["episodeNumber"]?.toIntOrNull(),
            actionUrl = data["actionUrl"],
            actionLabel = data["actionLabel"],
            mediaType = data["mediaType"],
            mediaUrl = data["mediaUrl"],
            imageUrl = data["imageUrl"],
            referenceId = data["referenceId"],
            campaign = data["campaign"]
        )
    }
}
