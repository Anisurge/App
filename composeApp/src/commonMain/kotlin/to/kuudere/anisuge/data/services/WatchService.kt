package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

@Serializable
data class ProgressRequest(
    val animeId: String,
    val episodeId: String,
    val currentTime: Double,
    val duration: Double,
    val server: String? = null,
    val language: String? = null,
)

class WatchService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun getContinueWatching(
        q: String? = null,
        format: String? = null,
        status: String? = null,
        season: String? = null,
        year: Int? = null,
        genre: String? = null,
        tag: String? = null,
        sort: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): ContinueWatchingResponse? {
        val stored = sessionStore.get() ?: return null
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/watch/continue") {
                bearer(stored.token)
                q?.let { parameter("q", it) }
                format?.let { parameter("format", it) }
                status?.let { parameter("status", it) }
                season?.let { parameter("season", it) }
                year?.let { parameter("year", it) }
                genre?.let { parameter("genre", it) }
                tag?.let { parameter("tag", it) }
                sort?.let { parameter("sort", it) }
                parameter("limit", limit)
                parameter("offset", offset)
            }
            response.body()
        } catch (e: Exception) {
            println("[WatchService] getContinueWatching error: ${e.message}")
            null
        }
    }

    suspend fun removeFromContinueWatching(animeId: String, episodeId: String): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.delete("${ApiConfig.API_BASE}/watch/continue/$animeId/$episodeId") {
                bearer(stored.token)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[WatchService] removeFromContinueWatching error: ${e.message}")
            false
        }
    }

    suspend fun saveProgress(request: ProgressRequest): Boolean {
        val stored = sessionStore.get() ?: return false
        return try {
            val response = httpClient.post("${ApiConfig.API_BASE}/watch/progress") {
                bearer(stored.token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[WatchService] saveProgress error: ${e.message}")
            false
        }
    }

    suspend fun getWatchInfo(
        slug: String,
        nid: String? = null,
        ep: String? = null,
        tz: String? = null,
    ): WatchInfoResponse? {
        val stored = sessionStore.get()
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/watch/$slug") {
                if (stored != null) bearer(stored.token)
                nid?.let { parameter("nid", it) }
                ep?.let { parameter("ep", it) }
                if (tz != null) parameter("tz", tz)
            }
            response.body()
        } catch (e: Exception) {
            println("[WatchService] getWatchInfo error: ${e.message}")
            null
        }
    }
}

@Serializable
data class ContinueWatchingResponse(
    val data: List<ContinueWatchingEntry> = emptyList(),
    val items: List<ContinueWatchingEntry> = emptyList(),
    val results: List<ContinueWatchingEntry> = emptyList(),
    @SerialName("continue_watching") val continueWatching: List<ContinueWatchingEntry> = emptyList(),
    val total: Int = 0,
    @SerialName("has_more") val hasMoreSnake: Boolean = false,
    val hasMore: Boolean = false,
) {
    val entries: List<ContinueWatchingEntry> get() = data.ifEmpty { items.ifEmpty { results.ifEmpty { continueWatching } } }
    fun hasMore(limit: Int, offset: Int): Boolean = hasMore || hasMoreSnake || (offset + limit < total)
}

@Serializable
data class ContinueWatchingEntry(
    @SerialName("anime_id")
    val animeId: String = "",
    @SerialName("animeId")
    val animeIdCamel: String = "",
    @SerialName("itemId")
    val itemId: String? = null,
    val anime: JsonElement? = null,
    val media: JsonElement? = null,
    val title: JsonElement? = null,
    val cover: String = "",
    val thumbnail: String? = null,
    @SerialName("cover_image")
    val coverImage: JsonElement? = null,
    @SerialName("banner_image")
    val bannerImage: String? = null,
    @SerialName("episode_id")
    val episodeId: String = "",
    @SerialName("episodeId")
    val episodeIdCamel: String = "",
    @SerialName("episode_number")
    val episodeNumber: Int = 0,
    @SerialName("episodeNumber")
    val episodeNumberCamel: Int = 0,
    val episode: JsonElement? = null,
    val progress: Double = 0.0,
    @SerialName("current_time")
    val currentTime: Double? = null,
    @SerialName("currentTime")
    val currentTimeCamel: Double? = null,
    val duration: Double = 0.0,
    @SerialName("last_watched")
    val lastWatched: String? = null,
    val server: String? = null,
    val language: String? = null,
    val link: String = "",
) {
    val activeAnimeId: String get() = animeId.ifBlank {
        animeIdCamel.ifBlank {
            itemId ?: anime.objectString("anime_id")
                ?: anime.objectString("animeId")
                ?: anime.objectString("id")
                ?: media.objectString("anime_id")
                ?: media.objectString("animeId")
                ?: media.objectString("id")
                ?: ""
        }
    }
    val activeEpisodeId: String get() = episodeId.ifBlank { episodeIdCamel.ifBlank { episode.objectString("episodeId") ?: episode.objectString("episode_id") ?: "" } }
    val activeEpisodeNumber: Int get() = if (episodeNumber > 0) episodeNumber else if (episodeNumberCamel > 0) episodeNumberCamel else episode.objectInt("episode_number") ?: episode.objectInt("episodeNumber") ?: episode.objectInt("number") ?: 0
    val activeProgress: Double get() = currentTime ?: currentTimeCamel ?: progress
    val displayTitle: String get() = title.stringOrTitle()
        ?: anime.objectElement("title").stringOrTitle()
        ?: media.objectElement("title").stringOrTitle()
        ?: anime.objectString("title")
        ?: media.objectString("title")
        ?: "Unknown"
    val imageUrl: String get() = thumbnail?.ifBlank { null }
        ?: cover.ifBlank { null }
        ?: coverImage.stringOrCoverUrl()
        ?: anime.objectElement("cover_image").stringOrCoverUrl()
        ?: anime.objectString("cover")
        ?: anime.objectString("thumbnail")
        ?: media.objectElement("cover_image").stringOrCoverUrl()
        ?: media.objectString("cover")
        ?: media.objectString("thumbnail")
        ?: bannerImage.orEmpty()
}

private fun JsonElement?.objectElement(key: String): JsonElement? = (this as? JsonObject)?.get(key)
private fun JsonElement?.objectString(key: String): String? = objectElement(key).primitiveString()
private fun JsonElement?.objectInt(key: String): Int? = (objectElement(key) as? JsonPrimitive)?.intOrNull

private fun JsonElement?.stringOrCoverUrl(): String? {
    return when (val element = this) {
        null, JsonNull -> null
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> {
            element["extra_large"].primitiveString()
                ?: element["large"].primitiveString()
                ?: element["medium"].primitiveString()
        }
        else -> null
    }
}

private fun JsonElement?.stringOrTitle(): String? {
    return when (val element = this) {
        null, JsonNull -> null
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> {
            element["user_preferred"].primitiveString()
                ?: element["english"].primitiveString()
                ?: element["romaji"].primitiveString()
                ?: element["native"].primitiveString()
        }
        else -> null
    }
}

private fun JsonElement?.primitiveString(): String? {
    return (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

@Serializable
data class WatchInfoResponse(
    val animeId: String = "",
    val title: String = "",
    val folder: String? = null,
    val currentEpisode: Int? = null,
    val progress: Double? = null,
    val server: String? = null,
    val episodes: List<WatchEpisodeItem> = emptyList(),
)

@Serializable
data class WatchEpisodeItem(
    val id: String = "",
    val number: Int = 0,
    val title: String? = null,
)
