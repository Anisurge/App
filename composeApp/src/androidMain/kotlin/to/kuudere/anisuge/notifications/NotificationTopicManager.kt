package to.kuudere.anisuge.notifications

import android.util.Log
import to.kuudere.anisuge.platform.isAndroidTvPlatform

object NotificationTopicManager {

    private const val TAG = "NotificationTopics"
    private val topics = listOf(
        "anisurge_all",
        "anisurge_new_episode",
        "anisurge_donation",
        "anisurge_announcement",
        "anisurge_maintenance"
    )

    fun subscribeToDefaultTopics() {
        if (isAndroidTvPlatform) {
            Log.d(TAG, "TV platform detected; skipping topic subscription")
            return
        }

        for (topic in topics) {
            runFirebaseTopicCall(topic = topic, subscribe = true)
        }
    }

    fun unsubscribeFromAllTopics() {
        if (isAndroidTvPlatform) {
            Log.d(TAG, "TV platform detected; skipping topic unsubscription")
            return
        }

        for (topic in topics) {
            runFirebaseTopicCall(topic = topic, subscribe = false)
        }
    }

    private fun runFirebaseTopicCall(topic: String, subscribe: Boolean) {
        val action = if (subscribe) "subscribeToTopic" else "unsubscribeFromTopic"
        runCatching {
            val firebaseMessagingClass = Class.forName("com.google.firebase.messaging.FirebaseMessaging")
            val getInstance = firebaseMessagingClass.getMethod("getInstance")
            val firebaseMessaging = getInstance.invoke(null)
            val topicMethod = firebaseMessagingClass.getMethod(action, String::class.java)
            topicMethod.invoke(firebaseMessaging, topic)
        }.onSuccess {
            Log.d(TAG, "${if (subscribe) "Subscribed" else "Unsubscribed"} to $topic")
        }.onFailure { error ->
            Log.w(TAG, "Failed to $action for $topic: ${error.message}")
        }
    }
}
