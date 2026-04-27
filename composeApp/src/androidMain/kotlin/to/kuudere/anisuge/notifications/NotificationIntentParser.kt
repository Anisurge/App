package to.kuudere.anisuge.notifications

import android.content.Intent
import to.kuudere.anisuge.navigation.NotificationLaunch

object NotificationIntentParser {
    fun parse(intent: Intent?): NotificationLaunch? {
        intent ?: return null
        val data = intent.data ?: return parseExtras(intent)
        if (data.scheme != "anisurge") return parseExtras(intent)

        val segments = data.pathSegments
        val animeId = when {
            data.host == "anime" && segments.isNotEmpty() -> segments[0]
            else -> intent.getStringExtra("animeId")
        }
        val episodeNumber = when {
            data.host == "anime" && segments.size >= 3 && segments[1] == "episode" -> segments[2].toIntOrNull()
            else -> intent.getStringExtra("episodeNumber")?.toIntOrNull()
        }

        val type = intent.getStringExtra("type") ?: when {
            animeId != null && episodeNumber != null -> "NEW_EPISODE"
            animeId != null -> "ANIME_INFO"
            data.host == "donate" -> "DONATION"
            data.host == "maintenance" -> "MAINTENANCE"
            else -> "ANNOUNCEMENT"
        }

        return NotificationLaunch(
            id = System.nanoTime(),
            type = type,
            title = intent.getStringExtra("title") ?: defaultTitle(type),
            body = intent.getStringExtra("body") ?: "",
            animeId = animeId,
            episodeNumber = episodeNumber,
            actionUrl = intent.getStringExtra("actionUrl") ?: data.toString(),
            actionLabel = intent.getStringExtra("actionLabel"),
            mediaType = intent.getStringExtra("mediaType"),
            mediaUrl = intent.getStringExtra("mediaUrl"),
            imageUrl = intent.getStringExtra("imageUrl"),
            referenceId = intent.getStringExtra("referenceId"),
            campaign = intent.getStringExtra("campaign"),
        )
    }

    private fun parseExtras(intent: Intent): NotificationLaunch? {
        val type = intent.getStringExtra("type") ?: return null
        return NotificationLaunch(
            id = System.nanoTime(),
            type = type,
            title = intent.getStringExtra("title") ?: defaultTitle(type),
            body = intent.getStringExtra("body") ?: "",
            animeId = intent.getStringExtra("animeId"),
            episodeNumber = intent.getStringExtra("episodeNumber")?.toIntOrNull(),
            actionUrl = intent.getStringExtra("actionUrl"),
            actionLabel = intent.getStringExtra("actionLabel"),
            mediaType = intent.getStringExtra("mediaType"),
            mediaUrl = intent.getStringExtra("mediaUrl"),
            imageUrl = intent.getStringExtra("imageUrl"),
            referenceId = intent.getStringExtra("referenceId"),
            campaign = intent.getStringExtra("campaign"),
        )
    }

    private fun defaultTitle(type: String): String = when (type) {
        "DONATION" -> "Support Anisurge"
        "MAINTENANCE" -> "Maintenance"
        "NEW_EPISODE" -> "New Episode"
        else -> "Anisurge"
    }
}
