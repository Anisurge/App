package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.patch
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import to.kuudere.anisuge.AppComponent

class TrackingService(
    private val httpClient: HttpClient,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val MAL_API_BASE = "https://api.myanimelist.net/v2"
        private const val MAL_OAUTH_TOKEN = "https://myanimelist.net/v1/oauth2/token"
        private const val ANILIST_GRAPHQL = "https://graphql.anilist.co"
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
            val response = httpClient.post(MAL_OAUTH_TOKEN) {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody("grant_type=refresh_token&refresh_token=$refreshToken")
            }
            val body = response.body<MalTokenRefreshResponse>()
            settingsStore.saveMalTokensWithRefresh(
                accessToken = body.access_token,
                refreshToken = body.refresh_token ?: refreshToken,
                expiresAt = System.currentTimeMillis() + (body.expires_in * 1000)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── MAL Sync ────────────────────────────────────────────────────────────

    suspend fun syncMalProgress(malId: Int, episodeNumber: Int, totalEpisodes: Int?): Boolean {
        // Refresh token if expired
        if (settingsStore.getMalIsExpired()) {
            if (!refreshMalToken()) return false
        }
        val token = settingsStore.getMalAccessToken() ?: return false
        val status = if (totalEpisodes != null && episodeNumber >= totalEpisodes) "completed" else "watching"
        return try {
            httpClient.patch("$MAL_API_BASE/anime/$malId/my_list_status") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody("status=$status&num_watched_episodes=$episodeNumber")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── AniList Sync ────────────────────────────────────────────────────────

    suspend fun syncAnilistProgress(anilistId: Int, episodeNumber: Int, totalEpisodes: Int?): Boolean {
        val token = settingsStore.getAnilistAccessToken() ?: return false
        if (settingsStore.getAnilistIsExpired()) return false // No refresh, prompt relogin
        val status = if (totalEpisodes != null && episodeNumber >= totalEpisodes) "COMPLETED" else "CURRENT"
        val query = "mutation(\$mediaId: Int, \$progress: Int, \$status: MediaListStatus) { SaveMediaListEntry(mediaId: \$mediaId, progress: \$progress, status: \$status) { id status progress } }"
        return try {
            httpClient.post(ANILIST_GRAPHQL) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"query":"$query","variables":{"mediaId":$anilistId,"progress":$episodeNumber,"status":"$status"}}""")
            }
            true
        } catch (e: Exception) {
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
