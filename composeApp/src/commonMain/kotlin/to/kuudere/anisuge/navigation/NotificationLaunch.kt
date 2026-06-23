package to.kuudere.anisuge.navigation

data class NotificationLaunch(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val animeId: String? = null,
    val episodeNumber: Int? = null,
    val server: String? = null,
    val lang: String? = null,
    val actionUrl: String? = null,
    val actionLabel: String? = null,
    val mediaType: String? = null,
    val mediaUrl: String? = null,
    val imageUrl: String? = null,
    val referenceId: String? = null,
    val campaign: String? = null,
)
