package to.kuudere.anisuge.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent

class AnisurgeMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AnisurgeFCM"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: ${token.take(10)}...")
        CoroutineScope(Dispatchers.IO).launch {
            AppComponent.notificationService.sync(
                enabled = AppComponent.settingsStore.notificationsEnabledFlow.first(),
                newEpisodes = AppComponent.settingsStore.notificationsNewEpisodeFlow.first(),
                reminderMinutes = AppComponent.settingsStore.notificationReminderMinutesFlow.first(),
                knownToken = token,
            )
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.d(TAG, "Received message: ${data.keys}")

        val type = data["type"] ?: return
        val title = data["title"] ?: message.notification?.title ?: "Anisurge"
        val body = data["body"] ?: message.notification?.body ?: return
        val settingsStore = AppComponent.settingsStore
        if (!settingsStore.notificationsEnabledBlocking()) {
            Log.d(TAG, "Notifications disabled, skipping")
            return
        }
        if (type.equals("new_episode", ignoreCase = true) &&
            !settingsStore.notificationsNewEpisodeBlocking()
        ) {
            Log.d(TAG, "New episode notifications disabled, skipping")
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
            campaign = data["campaign"],
        )
    }
}
