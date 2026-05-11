package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
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
import to.kuudere.anisuge.data.models.ReanimeExportItem
import to.kuudere.anisuge.data.models.ReanimeExportLibrary

data class WatchHistorySyncProgress(
    val current: Int,
    val total: Int,
    val malServiceDone: Boolean,
    val anilistServiceDone: Boolean,
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
        val now = System.currentTimeMillis()
        val wait = lastMalAt + MAL_DELAY_MS - now
        if (wait > 0) delay(wait)
        lastMalAt = System.currentTimeMillis()
    }

    private suspend fun waitAnilist() {
        val now = System.currentTimeMillis()
        val wait = lastAnilistAt + ANILIST_DELAY_MS - now
        if (wait > 0) delay(wait)
        lastAnilistAt = System.currentTimeMillis()
    }

    suspend fun sync(
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

        val library = try {
            fetchExportJson(session.token, onProgress)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val entries = flattenExport(library)
        if (entries.isEmpty()) {
            onProgress(
                WatchHistorySyncProgress(
                    current = 0,
                    total = 0,
                    malServiceDone = !wantMal,
                    anilistServiceDone = !wantAl,
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
        entries.forEachIndexed { index, entry ->
            val i = index + 1
            onProgress(
                WatchHistorySyncProgress(
                    current = i,
                    total = total,
                    malServiceDone = false,
                    anilistServiceDone = false,
                )
            )

            if (wantMal && malToken != null) {
                waitMal()
                if (settingsStore.getMalIsExpired() && trackingService.refreshMalToken()) {
                    malToken = settingsStore.getMalAccessToken()
                }
                val mt = malToken
                if (mt != null && patchMal(mt, entry)) malOk++ else malFail++
            }

            if (wantAl && alToken != null) {
                var anilistId = cache[entry.malId] ?: 0
                if (anilistId <= 0) {
                    waitAnilist()
                    anilistId = resolveAnilistIdFromMal(entry.malId) ?: 0
                    if (anilistId > 0) {
                        cache[entry.malId] = anilistId
                        malAnilistIdCache.put(entry.malId, anilistId)
                    }
                }
                if (anilistId <= 0) {
                    alFail++
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
                        onExportProgress(
                            WatchHistorySyncProgress(
                                current = current,
                                total = total,
                                malServiceDone = false,
                                anilistServiceDone = false,
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
                out[mal] = NormalizedWatchEntry(
                    malId = mal,
                    watchListType = wlt,
                    startedAt = exportDateElementToYmdString(item.started_at),
                    completedAt = exportDateElementToYmdString(item.completed_at),
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
        val malId: Int,
        val watchListType: Int,
        val startedAt: String?,
        val completedAt: String?,
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
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt) { id }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("mediaId", mediaId)
            put("status", entry.anilistStatus)
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
