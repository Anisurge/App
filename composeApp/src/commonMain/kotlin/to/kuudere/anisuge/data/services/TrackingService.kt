package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.patch
import io.ktor.client.request.delete
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

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TrackingService(
    private val httpClient: HttpClient,
    private val sessionStore: SessionStore,
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

    private fun currentYmd(): String {
        val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val m = d.monthNumber.toString().padStart(2, '0')
        val day = d.dayOfMonth.toString().padStart(2, '0')
        return "${d.year}-$m-$day"
    }

    // ── Login URLs ──────────────────────────────────────────────────────────

    /** Returns the login URL to open in CustomTabs. */
    suspend fun getMalLoginUrl(): String? {
        return try {
            val session = sessionStore.get()
            val response = httpClient.get("https://www.anisurge.lol/api/mal/login") {
                session?.anisurgeToken?.takeIf { it.isNotBlank() }?.let {
                    header("Authorization", "Bearer $it")
                }
            }
            val body: Map<String, String> = response.body()
            body["url"]
        } catch (e: Exception) {
            null
        }
    }

    /** Returns the login URL to open in CustomTabs. */
    suspend fun getAnilistLoginUrl(): String? {
        return try {
            val session = sessionStore.get()
            val response = httpClient.get("https://www.anisurge.lol/api/anilist/login") {
                session?.anisurgeToken?.takeIf { it.isNotBlank() }?.let {
                    header("Authorization", "Bearer $it")
                }
            }
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
            val session = sessionStore.get()
            val response = httpClient.post(MAL_REFRESH_ENDPOINT) {
                contentType(ContentType.Application.Json)
                session?.anisurgeToken?.takeIf { it.isNotBlank() }?.let {
                    header("Authorization", "Bearer $it")
                }
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
            if (!integrationsSyncService.restoreFromServer()) {
                integrationsSyncService.pushFromLocal()
            }
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
        val completing = totalEpisodes != null && episodeNumber >= totalEpisodes
        val status = if (completing) "completed" else "watching"
        val now = currentYmd()
        val startedAt = if (episodeNumber <= 1) now else null
        val completedAt = if (completing) now else null
        return updateMalEntry(malId, status, episodeNumber, totalEpisodes, startedAt, completedAt)
    }

    suspend fun updateMalEntry(
        malId: Int,
        status: String?,
        progress: Int?,
        totalEpisodes: Int?,
        startedAt: String?,
        completedAt: String?,
    ): Boolean {
        if (malId <= 0) return false
        if (settingsStore.getMalIsExpired() && !refreshMalToken()) return false
        val token = settingsStore.getMalAccessToken() ?: return false
        val normalizedStatus = when (status?.uppercase()) {
            "WATCHING", "CURRENT" -> "watching"
            "PLANNING", "PLAN_TO_WATCH" -> "plan_to_watch"
            "PAUSED", "ON_HOLD" -> "on_hold"
            "DROPPED" -> "dropped"
            "COMPLETED" -> "completed"
            else -> if (progress != null && progress > 0) "watching" else null
        }
        val effectiveStatus = if (
            totalEpisodes != null && progress != null && progress >= totalEpisodes
        ) "completed" else normalizedStatus
        val fields = buildList {
            effectiveStatus?.let { add("status=$it") }
            progress?.takeIf { it >= 0 }?.let { add("num_watched_episodes=$it") }
            startedAt?.take(10)?.let { add("start_date=$it") }
            completedAt?.take(10)?.let { add("finish_date=$it") }
        }
        if (fields.isEmpty()) return true
        return try {
            httpClient.patch("$MAL_API_BASE/anime/$malId/my_list_status") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(fields.joinToString("&"))
            }.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteMalEntry(malId: Int): Boolean {
        if (malId <= 0) return false
        if (settingsStore.getMalIsExpired() && !refreshMalToken()) return false
        val token = settingsStore.getMalAccessToken() ?: return false
        return try {
            httpClient.delete("$MAL_API_BASE/anime/$malId/my_list_status") {
                header("Authorization", "Bearer $token")
            }.status.isSuccess()
        } catch (_: Exception) {
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
        val completing = totalEpisodes != null && episodeNumber >= totalEpisodes
        val status = if (completing) "COMPLETED" else "CURRENT"
        val now = currentYmd()
        val startedAt = if (episodeNumber <= 1) now else null
        val completedAt = if (completing) now else null
        return updateAnilistEntry(anilistId, status, episodeNumber, totalEpisodes, startedAt, completedAt)
    }

    suspend fun updateAnilistEntry(
        anilistId: Int,
        status: String?,
        progress: Int?,
        totalEpisodes: Int?,
        startedAt: String?,
        completedAt: String?,
    ): Boolean {
        if (anilistId <= 0) return false
        val token = settingsStore.getAnilistAccessToken() ?: return false
        if (settingsStore.getAnilistIsExpired()) return false
        val normalizedStatus = when (status?.uppercase()) {
            "WATCHING", "CURRENT" -> "CURRENT"
            "PLANNING", "PLAN_TO_WATCH" -> "PLANNING"
            "PAUSED", "ON_HOLD" -> "PAUSED"
            "DROPPED" -> "DROPPED"
            "COMPLETED" -> "COMPLETED"
            else -> if (progress != null && progress > 0) "CURRENT" else null
        }
        val effectiveStatus = if (
            totalEpisodes != null && progress != null && progress >= totalEpisodes
        ) "COMPLETED" else normalizedStatus
        val query = """
            mutation(${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus,
                     ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status,
                startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt) { id }
            }
        """.trimIndent()
        return try {
            val response = httpClient.post(ANILIST_GRAPHQL) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject {
                        put("mediaId", anilistId)
                        progress?.takeIf { it >= 0 }?.let { put("progress", it) }
                        effectiveStatus?.let { put("status", it) }
                        parseFuzzyDate(startedAt)?.let { put("startedAt", it) }
                        parseFuzzyDate(completedAt)?.let { put("completedAt", it) }
                    })
                })
            }
            response.status.isSuccess() &&
                anilistJson.parseToJsonElement(response.bodyAsText()).jsonObject["errors"] == null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteAnilistEntry(anilistId: Int): Boolean {
        if (anilistId <= 0) return false
        val token = settingsStore.getAnilistAccessToken() ?: return false
        if (settingsStore.getAnilistIsExpired()) return false
        return try {
            val viewer = anilistRequest(token, "query { Viewer { id } }")
                ?.get("data")?.jsonObject?.get("Viewer")?.jsonObject
                ?.get("id")?.jsonPrimitive?.intOrNull ?: return false
            val listQuery = """
                query(${'$'}userId: Int, ${'$'}mediaId: Int) {
                  MediaList(userId: ${'$'}userId, mediaId: ${'$'}mediaId, type: ANIME) { id }
                }
            """.trimIndent()
            val listId = anilistRequest(
                token,
                listQuery,
                buildJsonObject { put("userId", viewer); put("mediaId", anilistId) },
            )?.get("data")?.jsonObject?.get("MediaList")?.jsonObject
                ?.get("id")?.jsonPrimitive?.intOrNull ?: return true
            val deleteQuery = """
                mutation(${'$'}id: Int) { DeleteMediaListEntry(id: ${'$'}id) { deleted } }
            """.trimIndent()
            val result = anilistRequest(token, deleteQuery, buildJsonObject { put("id", listId) })
                ?: return false
            result["errors"] == null
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun anilistRequest(
        token: String,
        query: String,
        variables: JsonObject = buildJsonObject {},
    ): JsonObject? {
        val response = httpClient.post(ANILIST_GRAPHQL) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("query", query)
                put("variables", variables)
            })
        }
        if (!response.status.isSuccess()) return null
        return anilistJson.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun parseFuzzyDate(value: String?): JsonObject? {
        val parts = value?.take(10)?.split("-") ?: return null
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return buildJsonObject {
            put("year", year)
            put("month", month)
            put("day", day)
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
        val malId: Int? = null,
        val anilistId: Int? = null,
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
                    entries += ExternalListEntry(
                        externalId = malId,
                        status = status,
                        progress = progress,
                        title = title,
                        malId = malId,
                    )
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
                    media { idMal title { romaji english } }
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
                    val malId = media?.get("idMal")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                    val title = media?.get("title")?.jsonObject?.let {
                        it["english"]?.jsonPrimitive?.content?.takeIf { t -> t.isNotBlank() }
                            ?: it["romaji"]?.jsonPrimitive?.content
                    }
                    entries += ExternalListEntry(
                        externalId = mediaId,
                        status = status,
                        progress = progress,
                        title = title,
                        malId = malId,
                        anilistId = mediaId,
                    )
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
