package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.BffIntegrationsPayload
import to.kuudere.anisuge.data.models.BffIntegrationsResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

/**
 * Mirrors MAL / AniList tokens between local [SettingsStore] and the Anisurge BFF Postgres row
 * so links survive app reinstall when the user signs in again.
 */
class IntegrationsSyncService(
    private val httpClient: HttpClient,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
) {
    private val patchJson = Json {
        explicitNulls = true
        encodeDefaults = false
    }

    /** Upload current local tracking tokens to the server (no-op if not signed in). */
    suspend fun pushFromLocal(): Boolean {
        val session = sessionStore.get() ?: return false
        if (!sessionStore.isValid(session)) return false
        return patch(session, payloadFromLocal())
    }

    suspend fun clearMalOnServer(): Boolean {
        val session = sessionStore.get() ?: return false
        if (!sessionStore.isValid(session)) return false
        return patch(
            session,
            BffIntegrationsPayload(
                malAccessToken = null,
                malRefreshToken = null,
                malExpiresAt = null,
                malUsername = null,
            ),
        )
    }

    suspend fun clearAnilistOnServer(): Boolean {
        val session = sessionStore.get() ?: return false
        if (!sessionStore.isValid(session)) return false
        return patch(
            session,
            BffIntegrationsPayload(
                anilistAccessToken = null,
                anilistExpiresAt = null,
                anilistUsername = null,
            ),
        )
    }

    /**
     * Pull server credentials into local storage. Server wins for each provider:
     * empty server token clears local; non-empty restores local.
     */
    suspend fun restoreFromServer(): Boolean {
        val session = sessionStore.get() ?: return false
        if (!sessionStore.isValid(session)) return false
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/me/integrations") {
                applyAnisurgeAuth(session)
            }
            if (!response.status.isSuccess()) {
                println("[IntegrationsSync] restore failed: HTTP ${response.status.value}")
                return false
            }
            val body: BffIntegrationsResponse = response.body()
            val remote = body.integrations
            val localHasMal = !settingsStore.getMalAccessToken().isNullOrBlank()
            val localHasAnilist = !settingsStore.getAnilistAccessToken().isNullOrBlank()
            val remoteHasMal = !remote.malAccessToken.isNullOrBlank()
            val remoteHasAnilist = !remote.anilistAccessToken.isNullOrBlank()

            // One-time migration: local links from before server sync → upload instead of wiping.
            if ((localHasMal && !remoteHasMal) || (localHasAnilist && !remoteHasAnilist)) {
                pushFromLocal()
                return true
            }

            applyToLocal(remote)
            true
        } catch (e: Exception) {
            println("[IntegrationsSync] restore error: ${e.message}")
            false
        }
    }

    private suspend fun applyToLocal(remote: BffIntegrationsPayload) {
        val malToken = remote.malAccessToken?.trim().orEmpty()
        if (malToken.isEmpty()) {
            settingsStore.clearMalTokens()
        } else {
            val refresh = remote.malRefreshToken?.trim().orEmpty()
            val expiresAt = remote.malExpiresAt ?: 0L
            if (expiresAt > 0) {
                settingsStore.saveMalTokensWithRefresh(malToken, refresh, expiresAt)
            } else {
                settingsStore.saveMalTokens(malToken, refresh, 0L)
            }
            remote.malUsername?.trim()?.takeIf { it.isNotEmpty() }?.let {
                settingsStore.saveMalUsername(it)
            }
        }

        val alToken = remote.anilistAccessToken?.trim().orEmpty()
        if (alToken.isEmpty()) {
            settingsStore.clearAnilistTokens()
        } else {
            val expiresAt = remote.anilistExpiresAt ?: 0L
            val expiresInSec = if (expiresAt > 0) {
                ((expiresAt - to.kuudere.anisuge.utils.currentTimeMillis()) / 1000).coerceAtLeast(0)
            } else {
                0L
            }
            settingsStore.saveAnilistTokens(alToken, expiresInSec)
            remote.anilistUsername?.trim()?.takeIf { it.isNotEmpty() }?.let {
                settingsStore.saveAnilistUsername(it)
            }
        }
    }

    private suspend fun payloadFromLocal(): BffIntegrationsPayload {
        val malAccess = settingsStore.getMalAccessToken()
        val malRefresh = settingsStore.getMalRefreshToken()
        val malExpires = settingsStore.getMalExpiresAt().takeIf { it > 0 }
        val malUser = settingsStore.getMalUsername()
        val alAccess = settingsStore.getAnilistAccessToken()
        val alExpires = settingsStore.getAnilistExpiresAt().takeIf { it > 0 }
        val alUser = settingsStore.getAnilistUsername()
        return BffIntegrationsPayload(
            malAccessToken = malAccess,
            malRefreshToken = malRefresh,
            malExpiresAt = malExpires,
            malUsername = malUser,
            anilistAccessToken = alAccess,
            anilistExpiresAt = alExpires,
            anilistUsername = alUser,
        )
    }

    private suspend fun patch(session: to.kuudere.anisuge.data.models.SessionInfo, payload: BffIntegrationsPayload): Boolean {
        return try {
            val response = httpClient.patch("${AnisurgeApi.v1Base}/me/integrations") {
                applyAnisurgeAuth(session)
                contentType(ContentType.Application.Json)
                setBody(patchJson.encodeToString(payload))
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                println("[IntegrationsSync] patch failed: HTTP ${response.status.value}")
                false
            }
        } catch (e: Exception) {
            println("[IntegrationsSync] patch error: ${e.message}")
            false
        }
    }
}
