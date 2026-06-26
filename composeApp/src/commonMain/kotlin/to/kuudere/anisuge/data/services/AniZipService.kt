package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Episode thumbnails and anime artwork from [ani.zip](https://api.ani.zip).
 */
class AniZipService(private val httpClient: HttpClient) {

    companion object {
        private const val BASE_URL = "https://api.ani.zip/mappings"
    }

    /**
     * Fetch episode thumbnails for an anime by its AniList ID.
     * Returns a map of episode number (as string) -> thumbnail URL.
     */
    suspend fun getEpisodeThumbnails(anilistId: Int): Map<String, String> {
        return try {
            val response = httpClient.get("$BASE_URL?anilist_id=$anilistId")
            if (!response.status.isSuccess()) return emptyMap()
            val body = response.bodyAsText()
            val parsed = Json.parseToJsonElement(body).jsonObject
            val episodes = parsed["episodes"]?.jsonObject ?: return emptyMap()
            episodes.entries.mapNotNull { (key, value) ->
                val ep = value.jsonObject
                val imageUrl = ep["image"]?.jsonPrimitive?.content
                if (imageUrl.isNullOrBlank()) null else key to imageUrl
            }.toMap()
        } catch (e: Exception) {
            println("[AniZip] Failed to fetch thumbnails for anilist=$anilistId: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Fetch anime-level artwork (banner, poster, fanart) from ani.zip.
     */
    suspend fun getAnimeArtwork(anilistId: Int): Map<String, String> {
        return try {
            val response = httpClient.get("$BASE_URL?anilist_id=$anilistId")
            if (!response.status.isSuccess()) return emptyMap()
            val body = response.bodyAsText()
            val parsed = Json.parseToJsonElement(body).jsonObject
            val images = parsed["images"]?.jsonObject ?: return emptyMap()
            val entries = images["entries"]?.jsonObject ?: return emptyMap()
            entries.entries.mapNotNull { (key, value) ->
                val obj = value.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content
                val coverType = obj["coverType"]?.jsonPrimitive?.content
                if (url.isNullOrBlank() || coverType.isNullOrBlank()) null else coverType to url
            }.toMap()
        } catch (e: Exception) {
            println("[AniZip] Failed to fetch artwork for anilist=$anilistId: ${e.message}")
            emptyMap()
        }
    }
}
