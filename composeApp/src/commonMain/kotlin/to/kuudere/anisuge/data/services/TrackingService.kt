package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.patch
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class TrackingService(
    private val httpClient: HttpClient,
    private val settingsStore: SettingsStore,
    private val integrationsSyncService: IntegrationsSyncService,
) {
    companion object {
        private const val MAL_API_BASE = "https://api.myanimelist.net/v2"
        private const val MAL_REFRESH_ENDPOINT = "https://www.anisurge.lol/api/mal/refresh"
        private const val ANILIST_GRAPHQL = "https://graphql.anilist.co"
        private const val LUNAR_REFRESH_ENDPOINT = "https://www.anisurge.lol/api/lunar/refresh"
        private val anilistJson = Json { ignoreUnknownKeys = true }
    }

    // ── Login URLs ──────────────────────────────────────────────────────────

    /** Returns the login URL to open in CustomTabs. */
    suspend fun getMalLoginUrl(): String? {
        return try {
            val response = httpClient.get("https://www.anisurge.lol/api/mal/login")
            val body: Map<String, String> = response.body()
            body["url"]
        } catch (e: Exception) {
            null
        }
    }

    /** Returns the login URL to open in CustomTabs. */
    suspend fun getAnilistLoginUrl(): String? {
        return try {
            val response = httpClient.get("https://www.anisurge.lol/api/anilist/login")
            val body: Map<String, String> = response.body()
            body["url"]
        } catch (e: Exception) {
            null
        }
    }

    /** Returns the login URL to open in CustomTabs. */
    suspend fun getLunarLoginUrl(): String? {
        return try {
            val response = httpClient.get("https://www.anisurge.lol/api/lunar/login")
            val body: Map<String, String> = response.body()
            body["url"]
        } catch (e: Exception) {
            null
        }
    }

    // ── MAL Token Refresh ───────────────────────────────────────────────────

    suspend fun refreshMalToken(): Boolean {
        val refreshToken = settingsStore.getMalRefreshToken() ?: return false
        return try {
            val response = httpClient.post(MAL_REFRESH_ENDPOINT) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh_token" to refreshToken))
            }
            if (!response.status.isSuccess()) {
                println("[TrackingService] MAL refresh failed with HTTP ${response.status.value}")
                return false
            }
            val body = response.body<MalTokenRefreshResponse>()
            settingsStore.saveMalTokensWithRefresh(
                accessToken = body.access_token,
                refreshToken = body.refresh_token ?: refreshToken,
                expiresAt = to.kuudere.anisuge.utils.currentTimeMillis() + (body.expires_in * 1000)
            )
            integrationsSyncService.pushFromLocal()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── MAL Sync ────────────────────────────────────────────────────────────

    suspend fun syncMalProgress(malId: Int, episodeNumber: Int, totalEpisodes: Int?): Boolean {
        if (malId <= 0 || episodeNumber <= 0) return false
        // Refresh token if expired
        if (settingsStore.getMalIsExpired()) {
            if (!refreshMalToken()) return false
        }
        val token = settingsStore.getMalAccessToken() ?: return false
        val status = if (totalEpisodes != null && episodeNumber >= totalEpisodes) "completed" else "watching"
        return try {
            val response = httpClient.patch("$MAL_API_BASE/anime/$malId/my_list_status") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody("status=$status&num_watched_episodes=$episodeNumber")
            }
            if (!response.status.isSuccess()) {
                println("[TrackingService] MAL sync failed for $malId: HTTP ${response.status.value}")
                return false
            }
            true
        } catch (e: Exception) {
            println("[TrackingService] MAL sync exception for $malId: ${e.message}")
            false
        }
    }

    // ── AniList Sync ────────────────────────────────────────────────────────

    /** Public AniList GraphQL: map a MAL anime id → AniList media id (no auth). */
    suspend fun lookupAnilistIdFromMal(malId: Int): Int? {
        if (malId <= 0) return null
        val query = """
            query (${'$'}malId: Int) { Media(idMal: ${'$'}malId, type: ANIME) { id } }
        """.trimIndent()
        return try {
            val response = httpClient.post(ANILIST_GRAPHQL) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", query)
                        put("variables", buildJsonObject { put("malId", malId) })
                    }
                )
            }
            if (!response.status.isSuccess()) return null
            val parsed = anilistJson.parseToJsonElement(response.bodyAsText()).jsonObject
            if (parsed["errors"] != null) return null
            parsed["data"]?.jsonObject?.get("Media")?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun syncAnilistProgress(anilistId: Int, episodeNumber: Int, totalEpisodes: Int?): Boolean {
        if (anilistId <= 0 || episodeNumber <= 0) return false
        val token = settingsStore.getAnilistAccessToken() ?: return false
        if (settingsStore.getAnilistIsExpired()) return false // No refresh, prompt relogin
        val status = if (totalEpisodes != null && episodeNumber >= totalEpisodes) "COMPLETED" else "CURRENT"
        val query = """
            mutation(${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                id
                status
                progress
              }
            }
        """.trimIndent()
        return try {
            val response = httpClient.post(ANILIST_GRAPHQL) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", query)
                        put(
                            "variables",
                            buildJsonObject {
                                put("mediaId", anilistId)
                                put("progress", episodeNumber)
                                put("status", status)
                            }
                        )
                    }
                )
            }
            if (!response.status.isSuccess()) {
                println("[TrackingService] AniList sync failed for $anilistId: HTTP ${response.status.value}")
                return false
            }
            val bodyText = response.bodyAsText()
            val envelope = anilistJson.parseToJsonElement(bodyText).jsonObject
            val hasErrors = envelope["errors"] != null
            if (hasErrors) {
                println("[TrackingService] AniList sync API errors for $anilistId: $bodyText")
            }
            !hasErrors
        } catch (e: Exception) {
            println("[TrackingService] AniList sync exception for $anilistId: ${e.message}")
            false
        }
    }

    // ── User Info ──────────────────────────────────────────────────────────

    suspend fun fetchMalUsername(): String? {
        if (settingsStore.getMalIsExpired()) {
            if (!refreshMalToken()) return null
        }
        val token = settingsStore.getMalAccessToken() ?: return null
        return try {
            val response = httpClient.get("$MAL_API_BASE/users/@me") {
                header("Authorization", "Bearer $token")
            }
            val body = response.body<JsonObject>()
            body["name"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchAnilistUsername(): String? {
        val token = settingsStore.getAnilistAccessToken() ?: return null
        return try {
            val response = httpClient.post(ANILIST_GRAPHQL) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"query":"query { Viewer { name } }"}""")
            }
            val body = response.body<JsonObject>()
            body["data"]?.jsonObject?.get("Viewer")?.jsonObject?.get("name")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    // ── Import: Fetch external lists ────────────────────────────────────────

    /**
     * Represents a single anime entry from an external tracker (MAL or AniList).
     * [externalId] is the MAL ID for MAL entries, or the AniList media ID for AniList entries.
     * [status] is the raw status string from the external service.
     * [progress] is the number of episodes watched (may be 0).
     */
    data class ExternalListEntry(
        val externalId: Int,
        val status: String,
        val progress: Int,
        val title: String? = null,
    ) {
        /** Maps external MAL status to Anisurge folder name. */
        fun toAnisurgeFolder(fromAnilist: Boolean): String = if (fromAnilist) {
            when (status.uppercase()) {
                "CURRENT"  -> "WATCHING"
                "PLANNING" -> "PLANNING"
                "COMPLETED" -> "COMPLETED"
                "PAUSED"   -> "PAUSED"
                "DROPPED"  -> "DROPPED"
                else       -> "WATCHING"
            }
        } else {
            when (status.lowercase()) {
                "watching"      -> "WATCHING"
                "plan_to_watch" -> "PLANNING"
                "completed"     -> "COMPLETED"
                "on_hold"       -> "PAUSED"
                "dropped"       -> "DROPPED"
                else            -> "WATCHING"
            }
        }
    }

    /**
     * Fetches all anime in the authenticated user's MAL list.
     * Paginates until all entries are collected (MAL max page size = 1000).
     */
    suspend fun fetchMalList(): Result<List<ExternalListEntry>> {
        if (settingsStore.getMalIsExpired()) {
            if (!refreshMalToken()) return Result.failure(Exception("MAL session expired — reconnect MAL"))
        }
        val token = settingsStore.getMalAccessToken()
            ?: return Result.failure(Exception("MAL not connected"))

        val entries = mutableListOf<ExternalListEntry>()
        var url: String? = "$MAL_API_BASE/users/@me/animelist?fields=list_status&limit=1000&nsfw=true"
        return try {
            while (url != null) {
                val response = httpClient.get(url) {
                    header("Authorization", "Bearer $token")
                }
                if (!response.status.isSuccess()) {
                    return Result.failure(Exception("MAL list fetch failed: HTTP ${response.status.value}"))
                }
                val body = anilistJson.parseToJsonElement(response.bodyAsText()).jsonObject
                val data = body["data"]?.jsonArray ?: break
                for (item in data) {
                    val obj = item.jsonObject
                    val node = obj["node"]?.jsonObject ?: continue
                    val listStatus = obj["list_status"]?.jsonObject ?: continue
                    val malId = node["id"]?.jsonPrimitive?.intOrNull ?: continue
                    val status = listStatus["status"]?.jsonPrimitive?.content ?: "watching"
                    val progress = listStatus["num_episodes_watched"]?.jsonPrimitive?.intOrNull ?: 0
                    val title = node["title"]?.jsonPrimitive?.content
                    entries += ExternalListEntry(malId, status, progress, title)
                }
                // Follow pagination cursor
                val nextUrl = body["paging"]?.jsonObject?.get("next")?.jsonPrimitive?.content
                url = nextUrl
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all anime in the authenticated user's AniList list using MediaListCollection.
     * Returns entries keyed by AniList media ID.
     */
    suspend fun fetchAnilistList(): Result<List<ExternalListEntry>> {
        val token = settingsStore.getAnilistAccessToken()
            ?: return Result.failure(Exception("AniList not connected"))
        if (settingsStore.getAnilistIsExpired()) {
            return Result.failure(Exception("AniList session expired — reconnect AniList"))
        }

        // First get the viewer username
        val viewerQuery = """{"query":"query { Viewer { name } }"}"""
        val username = try {
            val r = httpClient.post(ANILIST_GRAPHQL) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(viewerQuery)
            }
            val parsed = anilistJson.parseToJsonElement(r.bodyAsText()).jsonObject
            parsed["data"]?.jsonObject?.get("Viewer")?.jsonObject?.get("name")?.jsonPrimitive?.content
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to get AniList username: ${e.message}"))
        } ?: return Result.failure(Exception("AniList: could not resolve username"))

        val query = """
            query (${'$'}userName: String) {
              MediaListCollection(userName: ${'$'}userName, type: ANIME) {
                lists {
                  entries {
                    mediaId
                    status
                    progress
                    media { title { romaji english } }
                  }
                }
              }
            }
        """.trimIndent()

        return try {
            val response = httpClient.post(ANILIST_GRAPHQL) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", query)
                        put("variables", buildJsonObject { put("userName", username) })
                    }
                )
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("AniList list fetch failed: HTTP ${response.status.value}"))
            }
            val body = anilistJson.parseToJsonElement(response.bodyAsText()).jsonObject
            if (body["errors"] != null) {
                val errMsg = (body["errors"] as? JsonArray)?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonPrimitive?.content ?: "AniList API error"
                return Result.failure(Exception(errMsg))
            }
            val lists = body["data"]?.jsonObject
                ?.get("MediaListCollection")?.jsonObject
                ?.get("lists")?.jsonArray ?: return Result.success(emptyList())

            val entries = mutableListOf<ExternalListEntry>()
            for (list in lists) {
                val listEntries = list.jsonObject["entries"]?.jsonArray ?: continue
                for (entry in listEntries) {
                    val obj = entry.jsonObject
                    val mediaId = obj["mediaId"]?.jsonPrimitive?.intOrNull ?: continue
                    val status = obj["status"]?.jsonPrimitive?.content ?: "CURRENT"
                    val progress = obj["progress"]?.jsonPrimitive?.intOrNull ?: 0
                    val media = obj["media"]?.jsonObject
                    val title = media?.get("title")?.jsonObject?.let {
                        it["english"]?.jsonPrimitive?.content?.takeIf { t -> t.isNotBlank() }
                            ?: it["romaji"]?.jsonPrimitive?.content
                    }
                    entries += ExternalListEntry(mediaId, status, progress, title)
                }
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Disconnect ──────────────────────────────────────────────────────────

    suspend fun disconnectMal() {
        settingsStore.clearMalTokens()
        integrationsSyncService.clearMalOnServer()
    }

    suspend fun disconnectAnilist() {
        settingsStore.clearAnilistTokens()
        integrationsSyncService.clearAnilistOnServer()
    }

    // ── Lunar ───────────────────────────────────────────────────────────────

    suspend fun refreshLunarToken(): Boolean {
        val refreshToken = settingsStore.getLunarRefreshToken() ?: return false
        return try {
            val response = httpClient.post(LUNAR_REFRESH_ENDPOINT) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh_token" to refreshToken))
            }
            if (!response.status.isSuccess()) {
                println("[TrackingService] Lunar refresh failed with HTTP ${response.status.value}")
                return false
            }
            val body = response.body<LunarTokenRefreshResponse>()
            settingsStore.saveLunarTokens(
                accessToken = body.access_token,
                refreshToken = body.refresh_token ?: refreshToken,
                expiresIn = body.expires_in
            )
            integrationsSyncService.pushFromLocal()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchLunarProfile(token: String): LunarProfile? {
        return try {
            val response = httpClient.get("https://api.lunaranime.ru/api/oauth2/me") {
                header("Authorization", "Bearer $token")
            }
            val body = response.body<LunarProfileResponse>()
            LunarProfile(username = body.username, userId = body.user_id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchLunarWatchlist(): List<LunarWatchlistItem> {
        if (settingsStore.getLunarIsExpired()) {
            if (!refreshLunarToken()) return emptyList()
        }
        val token = settingsStore.getLunarAccessToken() ?: return emptyList()
        return try {
            val response = httpClient.get("https://api.lunaranime.ru/api/oauth2/watchlist") {
                header("Authorization", "Bearer $token")
            }
            if (!response.status.isSuccess()) return emptyList()
            response.body<LunarWatchlistResponse>().anime
        } catch (e: Exception) {
            println("[TrackingService] Lunar watchlist fetch error: ${e.message}")
            emptyList()
        }
    }

    suspend fun toggleLunarWatchlistAnime(slug: String): Boolean {
        if (settingsStore.getLunarIsExpired()) {
            if (!refreshLunarToken()) return false
        }
        val token = settingsStore.getLunarAccessToken() ?: return false
        return try {
            val response = httpClient.post("https://api.lunaranime.ru/api/oauth2/watchlist") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("list_type", "anime")
                        put("slug", slug)
                    }
                )
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("[TrackingService] Lunar watchlist toggle error: ${e.message}")
            false
        }
    }

    suspend fun disconnectLunar() {
        settingsStore.clearLunarTokens()
        integrationsSyncService.clearLunarOnServer()
    }
}

data class LunarProfile(
    val username: String,
    val userId: String,
)

@Serializable
data class LunarWatchlistItem(
    val slug: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    val title: String? = null,
)

@Serializable
private data class LunarTokenRefreshResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long = 0,
)

@Serializable
private data class LunarProfileResponse(
    val user_id: String,
    val username: String,
    val avatar_url: String? = null,
    val bio: String? = null,
)

@Serializable
private data class LunarWatchlistResponse(
    val anime: List<LunarWatchlistItem> = emptyList(),
    val manga: List<LunarWatchlistItem> = emptyList(),
)

@Serializable
private data class MalTokenRefreshResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long = 0,
)
