package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.ContinueWatchingResponse
import to.kuudere.anisuge.data.models.ReanimeExportItem
import to.kuudere.anisuge.data.models.ReanimeExportLibrary
import to.kuudere.anisuge.data.models.SessionInfo
import to.kuudere.anisuge.data.models.WatchlistResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

data class WatchHistorySyncProgress(
    val current: Int,
    val total: Int,
    val malServiceDone: Boolean,
    val anilistServiceDone: Boolean,
    /** SSE message, anime title, etc. */
    val detail: String? = null,
)

data class WatchHistorySyncOutcome(
    val totalEntries: Int,
    val malSynced: Int,
    val anilistSynced: Int,
    val malFailed: Int,
    val anilistFailed: Int,
)

class WatchHistorySyncService(
    private val httpClient: HttpClient,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val malAnilistIdCache: MalAnilistIdCache,
    private val trackingService: TrackingService,
) {
    companion object {
        private const val EXPORT_URL = "https://reanime.to/api/user/export?format=json"
        private const val MAL_API_BASE = "https://api.myanimelist.net/v2"
        private const val ANILIST_GRAPHQL = "https://graphql.anilist.co"
        private const val OFFLINE_PROJECT_R_TOKEN = "project_r_anisurge_offline"
        private const val MAL_DELAY_MS = 200L
        private const val ANILIST_DELAY_MS = 700L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var lastMalAt = 0L
    private var lastAnilistAt = 0L

    private suspend fun waitMal() {
        val now = to.kuudere.anisuge.utils.currentTimeMillis()
        val wait = lastMalAt + MAL_DELAY_MS - now
        if (wait > 0) delay(wait)
        lastMalAt = to.kuudere.anisuge.utils.currentTimeMillis()
    }

    private suspend fun waitAnilist() {
        val now = to.kuudere.anisuge.utils.currentTimeMillis()
        val wait = lastAnilistAt + ANILIST_DELAY_MS - now
        if (wait > 0) delay(wait)
        lastAnilistAt = to.kuudere.anisuge.utils.currentTimeMillis()
    }

    suspend fun sync(
        differential: Boolean = false,
        onProgress: (WatchHistorySyncProgress) -> Unit,
    ): Result<WatchHistorySyncOutcome> {
        lastMalAt = 0L
        lastAnilistAt = 0L

        val session = sessionStore.get() ?: return Result.failure(Exception("Not logged in"))
        val token = session.token
        if (token.isBlank()) return Result.failure(Exception("Not logged in"))

        val wantMal = settingsStore.getMalAccessToken() != null
        val wantAl = settingsStore.getAnilistAccessToken() != null && !settingsStore.getAnilistIsExpired()
        if (!wantMal && !wantAl) {
            return Result.failure(Exception("Connect MAL and/or AniList first"))
        }

        if (wantMal && settingsStore.getMalIsExpired()) {
            if (!trackingService.refreshMalToken()) {
                return Result.failure(Exception("MAL session expired — reconnect MAL"))
            }
        }

        val entries = try {
            fetchSyncEntries(session, onProgress)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val existingMal = if (differential && wantMal) {
            trackingService.fetchMalList().getOrElse { return Result.failure(it) }
                .associateBy { it.externalId }
        } else {
            emptyMap()
        }
        val existingAnilist = if (differential && wantAl) {
            trackingService.fetchAnilistList().getOrElse { return Result.failure(it) }
                .associateBy { it.externalId }
        } else {
            emptyMap()
        }

        if (entries.isEmpty()) {
            onProgress(
                WatchHistorySyncProgress(
                    current = 0,
                    total = 0,
                    malServiceDone = !wantMal,
                    anilistServiceDone = !wantAl,
                    detail = null,
                )
            )
            return Result.success(WatchHistorySyncOutcome(0, 0, 0, 0, 0))
        }

        var malToken = settingsStore.getMalAccessToken()
        val alToken = settingsStore.getAnilistAccessToken()

        var malOk = 0
        var malFail = 0
        var alOk = 0
        var alFail = 0

        val cache = malAnilistIdCache.getAll().toMutableMap()

        val total = entries.size
        entries.forEachIndexed { index, rawEntry ->
            val i = index + 1
            val entry = rawEntry.withResolvedIds()
            onProgress(
                WatchHistorySyncProgress(
                    current = i,
                    total = total,
                    malServiceDone = false,
                    anilistServiceDone = false,
                    detail = entry.title?.takeIf { it.isNotBlank() }
                        ?: entry.malId?.let { "MAL id $it" }
                        ?: entry.anilistId?.let { "AniList id $it" },
                )
            )

            val malNeedsSync = entry.malId?.let { id ->
                !differential || trackerEntryNeedsSync(
                    existing = existingMal[id],
                    expectedStatus = entry.malStatus,
                    expectedProgress = entry.progress,
                )
            } ?: false
            if (wantMal && malToken != null && entry.malId != null && malNeedsSync) {
                waitMal()
                if (settingsStore.getMalIsExpired() && trackingService.refreshMalToken()) {
                    malToken = settingsStore.getMalAccessToken()
                }
                val mt = malToken
                if (mt != null && patchMal(mt, entry)) malOk++ else malFail++
            }

            if (wantAl && alToken != null) {
                var anilistId = entry.anilistId ?: 0
                val malId = entry.malId
                if (anilistId <= 0 && malId != null) {
                    anilistId = cache[malId] ?: 0
                }
                if (anilistId <= 0 && malId != null) {
                    waitAnilist()
                    anilistId = resolveAnilistIdFromMal(malId) ?: 0
                    if (anilistId > 0) {
                        cache[malId] = anilistId
                        malAnilistIdCache.put(malId, anilistId)
                    }
                }
                if (anilistId <= 0) {
                    alFail++
                } else if (
                    differential && !trackerEntryNeedsSync(
                        existing = existingAnilist[anilistId],
                        expectedStatus = entry.anilistStatus,
                        expectedProgress = entry.progress,
                    )
                ) {
                    // Already matches, or the tracker has further progress.
                } else {
                    waitAnilist()
                    if (saveAnilist(alToken, anilistId, entry)) alOk++ else alFail++
                }
            }
        }

        onProgress(
            WatchHistorySyncProgress(
                current = total,
                total = total,
                malServiceDone = wantMal,
                anilistServiceDone = wantAl,
                detail = null,
            )
        )

        return Result.success(
            WatchHistorySyncOutcome(
                totalEntries = total,
                malSynced = malOk,
                anilistSynced = alOk,
                malFailed = malFail,
                anilistFailed = alFail,
            )
        )
    }

    private fun trackerEntryNeedsSync(
        existing: TrackingService.ExternalListEntry?,
        expectedStatus: String,
        expectedProgress: Int?,
    ): Boolean {
        if (existing == null) return true
        val statusMatches = existing.status.equals(expectedStatus, ignoreCase = true)
        val progressNeedsUpdate = expectedProgress?.let { it > existing.progress } ?: false
        return !statusMatches || progressNeedsUpdate
    }

    private suspend fun fetchExportJson(
        sessionToken: String,
        onExportProgress: (WatchHistorySyncProgress) -> Unit,
    ): ReanimeExportLibrary {
        var lastData: ReanimeExportLibrary? = null
        httpClient.prepareGet(EXPORT_URL) {
            header("Cookie", "project_r_token=$sessionToken")
            header("Accept", "text/event-stream")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw Exception("Export failed: HTTP ${response.status.value}")
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) continue
                val payload = trimmed.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                val root = json.parseToJsonElement(payload).jsonObject
                val phase = root["phase"]?.jsonPrimitive?.content
                when (phase) {
                    "starting", "syncing" -> {
                        val current = root["current"]?.jsonPrimitive?.intOrNull
                            ?: root["current"]?.jsonPrimitive?.content?.toIntOrNull()
                            ?: 0
                        val total = root["total"]?.jsonPrimitive?.intOrNull
                            ?: root["total"]?.jsonPrimitive?.content?.toIntOrNull()
                            ?: 0
                        val message = root["message"]?.jsonPrimitive?.content
                        val details = root["details"]?.jsonPrimitive?.content
                        val detail = listOfNotNull(message, details)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { null }
                        onExportProgress(
                            WatchHistorySyncProgress(
                                current = current,
                                total = total,
                                malServiceDone = false,
                                anilistServiceDone = false,
                                detail = detail,
                            )
                        )
                    }
                    "data" -> {
                        val jsonField = root["json"] ?: continue
                        lastData = when (val j = jsonField) {
                            is JsonPrimitive ->
                                if (!j.isString) null
                                else json.decodeFromString<ReanimeExportLibrary>(j.content)
                            is JsonObject ->
                                json.decodeFromJsonElement(ReanimeExportLibrary.serializer(), j)
                            else -> null
                        }
                    }
                    else -> { /* completed, etc. */ }
                }
            }
        }
        return lastData ?: throw Exception("No export data in stream")
    }

    private suspend fun fetchSyncEntries(
        session: SessionInfo,
        onExportProgress: (WatchHistorySyncProgress) -> Unit,
    ): List<NormalizedWatchEntry> {
        if (session.token == OFFLINE_PROJECT_R_TOKEN) {
            return fetchBffLibraryEntries(session, onExportProgress)
        }

        return try {
            flattenExport(fetchExportJson(session.token, onExportProgress))
        } catch (e: Exception) {
            if (e.message?.contains("HTTP 401") == true && !session.anisurgeToken.isNullOrBlank()) {
                fetchBffLibraryEntries(session, onExportProgress)
            } else {
                throw e
            }
        }
    }

    private suspend fun fetchBffLibraryEntries(
        session: SessionInfo,
        onExportProgress: (WatchHistorySyncProgress) -> Unit,
    ): List<NormalizedWatchEntry> {
        onExportProgress(
            WatchHistorySyncProgress(
                current = 0,
                total = 0,
                malServiceDone = false,
                anilistServiceDone = false,
                detail = "Loading Anisurge library",
            )
        )

        val out = linkedMapOf<String, NormalizedWatchEntry>()
        var offset = 0
        val pageSize = 100
        while (true) {
            val page: WatchlistResponse = httpClient.get("${AnisurgeApi.v1Base}/watchlist") {
                applyAnisurgeAuth(session)
                parameter("limit", pageSize)
                if (offset > 0) parameter("offset", offset)
            }.body()
            page.results.forEach { item ->
                val animeId = item.effectiveAnimeId.takeIf { it.isNotBlank() }
                val malId = item.anime.malId?.takeIf { it > 0 }
                val anilistId = item.anime.anilistId?.takeIf { it > 0 }
                if (animeId == null && malId == null && anilistId == null) return@forEach
                val status = folderToWatchListType(item.effectiveFolder)
                val title = item.anime.displayTitle.takeIf { it.isNotBlank() }
                out[entryKey(animeId, malId, anilistId)] = NormalizedWatchEntry(
                    animeId = animeId,
                    malId = malId,
                    anilistId = anilistId,
                    watchListType = status,
                    startedAt = item.createdAt,
                    completedAt = if (status == 5) item.lastUpdated else null,
                    progress = null,
                    title = title,
                )
            }
            if (page.results.isEmpty() || offset + page.results.size >= page.total || page.results.size < pageSize) break
            offset += page.results.size
        }

        offset = 0
        while (true) {
            val page: ContinueWatchingResponse = httpClient.get("${AnisurgeApi.v1Base}/watch/continue") {
                applyAnisurgeAuth(session)
                parameter("limit", pageSize)
                if (offset > 0) parameter("offset", offset)
            }.body()
            page.data.forEach { item ->
                val animeId = item.effectiveAnimeId.takeIf { it.isNotBlank() }
                val malId = item.anime.malId?.takeIf { it > 0 }
                val anilistId = item.anime.anilistId?.takeIf { it > 0 }
                if (animeId == null && malId == null && anilistId == null) return@forEach
                val title = item.displayTitle.takeIf { it.isNotBlank() }
                val key = entryKey(animeId, malId, anilistId)
                val existing = out[key]
                val progress = item.displayEpisode.takeIf { it > 0 }
                out[key] = existing?.copy(
                    animeId = existing.animeId ?: animeId,
                    malId = existing.malId ?: malId,
                    anilistId = existing.anilistId ?: anilistId,
                    progress = maxOf(existing.progress ?: 0, progress ?: 0).takeIf { it > 0 },
                    title = existing.title ?: title,
                ) ?: NormalizedWatchEntry(
                    animeId = animeId,
                    malId = malId,
                    anilistId = anilistId,
                    watchListType = 1,
                    startedAt = null,
                    completedAt = null,
                    progress = progress,
                    title = title,
                )
            }
            if (page.data.isEmpty() || offset + page.data.size >= page.total || page.data.size < pageSize) break
            offset += page.data.size
        }

        onExportProgress(
            WatchHistorySyncProgress(
                current = out.size,
                total = out.size,
                malServiceDone = false,
                anilistServiceDone = false,
                detail = "Loaded Anisurge library",
            )
        )
        return out.values.toList()
    }

    private fun folderToWatchListType(folder: String?): Int {
        return when (folder?.trim()?.uppercase()) {
            "WATCHING", "CURRENT" -> 1
            "PAUSED", "ON_HOLD", "ON-HOLD" -> 2
            "PLANNING", "PLAN_TO_WATCH", "PLAN-TO-WATCH" -> 3
            "DROPPED" -> 4
            "COMPLETED" -> 5
            else -> 1
        }
    }

    private fun entryKey(animeId: String?, malId: Int?, anilistId: Int?): String {
        return animeId?.let { "anime:$it" }
            ?: malId?.let { "mal:$it" }
            ?: "al:${anilistId ?: 0}"
    }

    /** API may send ISO strings, epoch numbers, `null`, or `{ year, month, day }`. */
    private fun exportDateElementToYmdString(el: JsonElement?): String? {
        if (el == null) return null
        if (el is JsonNull) return null
        return when (el) {
            is JsonPrimitive -> when {
                el.isString -> toYmd(el.content)
                el.longOrNull != null -> toYmd(el.longOrNull.toString())
                else -> null
            }
            is JsonObject -> {
                val y = el["year"]?.jsonPrimitive?.intOrNull
                    ?: el["year"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return null
                val m = el["month"]?.jsonPrimitive?.intOrNull
                    ?: el["month"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return null
                val d = el["day"]?.jsonPrimitive?.intOrNull
                    ?: el["day"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return null
                val mo = m.toString().padStart(2, '0')
                val da = d.toString().padStart(2, '0')
                "$y-$mo-$da"
            }
            else -> null
        }
    }

    private fun flattenExport(lib: ReanimeExportLibrary): List<NormalizedWatchEntry> {
        val out = linkedMapOf<Int, NormalizedWatchEntry>()
        fun put(items: List<ReanimeExportItem>, bucketType: Int) {
            for (item in items) {
                val mal = item.mal_id ?: item.malId ?: 0
                if (mal <= 0) continue
                val wlt = item.watchListType?.takeIf { it in 1..5 } ?: bucketType
                val prev = out[mal]
                val title = item.name?.takeIf { it.isNotBlank() } ?: prev?.title
                out[mal] = NormalizedWatchEntry(
                    animeId = null,
                    malId = mal,
                    anilistId = null,
                    watchListType = wlt,
                    startedAt = exportDateElementToYmdString(item.started_at),
                    completedAt = exportDateElementToYmdString(item.completed_at),
                    progress = null,
                    title = title,
                )
            }
        }
        put(lib.Watching, 1)
        put(lib.onHold, 2)
        put(lib.planToWatch, 3)
        put(lib.Dropped, 4)
        put(lib.Completed, 5)
        return out.values.toList()
    }

    private data class NormalizedWatchEntry(
        val animeId: String? = null,
        val malId: Int?,
        val anilistId: Int? = null,
        val watchListType: Int,
        val startedAt: String?,
        val completedAt: String?,
        val progress: Int? = null,
        val title: String? = null,
    ) {
        val malStatus: String
            get() = when (watchListType) {
                1 -> "watching"
                2 -> "on_hold"
                3 -> "plan_to_watch"
                4 -> "dropped"
                5 -> "completed"
                else -> "watching"
            }
        val anilistStatus: String
            get() = when (watchListType) {
                1 -> "CURRENT"
                2 -> "PAUSED"
                3 -> "PLANNING"
                4 -> "DROPPED"
                5 -> "COMPLETED"
                else -> "CURRENT"
            }
    }

    private suspend fun patchMal(token: String, entry: NormalizedWatchEntry): Boolean {
        return try {
            val startYmd = toYmd(entry.startedAt)
            val endYmd = toYmd(entry.completedAt)
            val body = buildString {
                append("status=").append(entry.malStatus.encodeURLParameter())
                entry.progress?.takeIf { it > 0 }?.let {
                    append("&num_watched_episodes=").append(it.toString().encodeURLParameter())
                }
                if (startYmd != null) append("&my_start_date=").append(startYmd.encodeURLParameter())
                if (endYmd != null) append("&my_finish_date=").append(endYmd.encodeURLParameter())
            }
            val response = httpClient.patch("$MAL_API_BASE/anime/${entry.malId}/my_list_status") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
            }
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun NormalizedWatchEntry.withResolvedIds(): NormalizedWatchEntry {
        if (malId != null && anilistId != null) return this
        val id = animeId?.takeIf { it.isNotBlank() } ?: return this
        val details = resolveProjectRAnimeDetails(id) ?: return this
        return copy(
            malId = malId ?: details.malId?.takeIf { it > 0 },
            anilistId = anilistId ?: details.anilistId?.takeIf { it > 0 },
            title = title ?: details.title.displayTitle.takeIf { it.isNotBlank() },
        )
    }

    private suspend fun resolveProjectRAnimeDetails(animeId: String): AnimeDetails? {
        return try {
            val response = httpClient.get("${AppComponent.PROJECT_R_BASE_URL}/anime/$animeId")
            if (!response.status.isSuccess()) return null
            response.body()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveAnilistIdFromMal(malId: Int): Int? {
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
            val parsed = json.parseToJsonElement(response.bodyAsText()).jsonObject
            parsed["errors"]?.let { return null }
            parsed["data"]?.jsonObject?.get("Media")?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun saveAnilist(token: String, mediaId: Int, entry: NormalizedWatchEntry): Boolean {
        val query = """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, progress: ${'$'}progress, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt) { id }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("mediaId", mediaId)
            put("status", entry.anilistStatus)
            entry.progress?.takeIf { it > 0 }?.let { put("progress", it) }
            fuzzyJson(entry.startedAt)?.let { put("startedAt", it) }
            fuzzyJson(entry.completedAt)?.let { put("completedAt", it) }
        }
        return try {
            val response = httpClient.post(ANILIST_GRAPHQL) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", query)
                        put("variables", vars)
                    }
                )
            }
            if (!response.status.isSuccess()) return false
            val parsed = json.parseToJsonElement(response.bodyAsText()).jsonObject
            parsed["errors"] == null
        } catch (_: Exception) {
            false
        }
    }

    private fun fuzzyJson(raw: String?): JsonObject? {
        val ymd = toYmd(raw) ?: return null
        val parts = ymd.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return buildJsonObject {
            put("year", y)
            put("month", m)
            put("day", d)
        }
    }

    private fun toYmd(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (s.length >= 10 && s[4] == '-' && s[7] == '-') {
            val t = s.take(10)
            if (t.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return t
        }
        val num = s.toLongOrNull()
        if (num != null && num > 0) {
            val ms = if (num < 1_000_000_000_000L) num * 1000 else num
            val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
            val mo = date.monthNumber.toString().padStart(2, '0')
            val da = date.dayOfMonth.toString().padStart(2, '0')
            return "${date.year}-$mo-$da"
        }
        return null
    }
}
