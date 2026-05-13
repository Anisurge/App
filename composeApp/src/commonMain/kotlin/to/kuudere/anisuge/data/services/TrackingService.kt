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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class TrackingService(
    private val httpClient: HttpClient,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val MAL_API_BASE = "https://api.myanimelist.net/v2"
        private const val MAL_REFRESH_ENDPOINT = "https://www.anisurge.lol/api/mal/refresh"
        private const val ANILIST_GRAPHQL = "https://graphql.anilist.co"
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

    // ── Disconnect ──────────────────────────────────────────────────────────

    suspend fun disconnectMal() {
        settingsStore.clearMalTokens()
    }

    suspend fun disconnectAnilist() {
        settingsStore.clearAnilistTokens()
    }
}

@Serializable
private data class MalTokenRefreshResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long = 0,
)
