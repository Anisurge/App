package to.kuudere.anisuge.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import to.kuudere.anisuge.AppComponent

object NotificationTopicManager {

    private const val TAG = "NotificationTopics"

    fun subscribeToDefaultTopics() {
        val topics = listOf(
            AnisurgeMessagingService.TOPIC_ALL,
            AnisurgeMessagingService.TOPIC_NEW_EPISODE,
            AnisurgeMessagingService.TOPIC_DONATION,
            AnisurgeMessagingService.TOPIC_ANNOUNCEMENT,
            AnisurgeMessagingService.TOPIC_MAINTENANCE
        )

        for (topic in topics) {
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed to $topic")
                    } else {
                        Log.w(TAG, "Failed to subscribe to $topic: ${task.exception?.message}")
                    }
                }
        }
    }

    fun unsubscribeFromAllTopics() {
        val topics = listOf(
            AnisurgeMessagingService.TOPIC_ALL,
            AnisurgeMessagingService.TOPIC_NEW_EPISODE,
            AnisurgeMessagingService.TOPIC_DONATION,
            AnisurgeMessagingService.TOPIC_ANNOUNCEMENT,
            AnisurgeMessagingService.TOPIC_MAINTENANCE
        )

        for (topic in topics) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Unsubscribed from $topic")
                    } else {
                        Log.w(TAG, "Failed to unsubscribe from $topic: ${task.exception?.message}")
                    }
                }
        }
    }
}
