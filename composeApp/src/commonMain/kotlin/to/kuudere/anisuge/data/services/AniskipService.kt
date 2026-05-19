package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import to.kuudere.anisuge.data.models.AniskipRelationRulesResponse
import to.kuudere.anisuge.data.models.AniskipSkipTimesResponse
import to.kuudere.anisuge.data.models.SkipData

/**
 * Community intro/outro timestamps via [Aniskip](https://api.aniskip.com) (MAL-keyed).
 * Resolves MAL from catalog [malId] or AniList [idMal] when needed.
 */
class AniskipService(
    private val httpClient: HttpClient,
    private val malAnilistIdCache: MalAnilistIdCache,
) {
    companion object {
        private const val ANISKIP_BASE = "https://api.aniskip.com"
        private const val ANILIST_GRAPHQL = "https://graphql.anilist.co"
        private const val DEFAULT_EPISODE_LENGTH_SEC = 1440.0
        private val json = Json { ignoreUnknownKeys = true }
    }

    data class IntroOutroSkip(
        val intro: SkipData? = null,
        val outro: SkipData? = null,
    )

    suspend fun resolveIntroOutro(
        malId: Int?,
        anilistId: Int?,
        episodeNumber: Int,
        episodeLengthSec: Double = DEFAULT_EPISODE_LENGTH_SEC,
    ): IntroOutroSkip {
        val resolvedMal = resolveMalId(malId, anilistId)
        if (resolvedMal == null) {
            println("[Aniskip] no MAL id (mal=$malId anilist=$anilistId)")
            return IntroOutroSkip()
        }
        if (episodeNumber <= 0) return IntroOutroSkip()

        val (mappedMal, mappedEpisode) = applyRelationRules(resolvedMal, episodeNumber)
        if (mappedMal != resolvedMal || mappedEpisode != episodeNumber) {
            println("[Aniskip] relation map mal $resolvedMal→$mappedMal ep $episodeNumber→$mappedEpisode")
        }
        return fetchSkipTimes(mappedMal, mappedEpisode, episodeLengthSec)
    }

    suspend fun resolveMalId(malId: Int?, anilistId: Int?): Int? {
        if (malId != null && malId > 0) return malId
        if (anilistId == null || anilistId <= 0) return null

        malAnilistIdCache.getMalForAnilist(anilistId)?.let { return it }

        val fromAnilist = lookupMalIdFromAnilist(anilistId)
        if (fromAnilist != null && fromAnilist > 0) {
            malAnilistIdCache.put(fromAnilist, anilistId)
            return fromAnilist
        }
        return null
    }

    /** Public AniList GraphQL: AniList media id → MAL id (idMal). */
    suspend fun lookupMalIdFromAnilist(anilistId: Int): Int? {
        if (anilistId <= 0) return null
        val query = """
            query (${'$'}id: Int) { Media(id: ${'$'}id, type: ANIME) { idMal } }
        """.trimIndent()
        return try {
            val response = httpClient.post(ANILIST_GRAPHQL) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", query)
                        put("variables", buildJsonObject { put("id", anilistId) })
                    },
                )
            }
            if (!response.status.isSuccess()) return null
            val parsed = json.parseToJsonElement(response.bodyAsText()).jsonObject
            if (parsed["errors"] != null) return null
            parsed["data"]?.jsonObject
                ?.get("Media")?.jsonObject
                ?.get("idMal")?.jsonPrimitive
                ?.content
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        } catch (e: Exception) {
            println("[AniskipService] AniList idMal lookup failed: ${e.message}")
            null
        }
    }

    /**
     * Remaps split-cour / continuous episode numbering (same logic as the Aniskip extension).
     */
    private suspend fun applyRelationRules(malId: Int, rawEpisodeNumber: Int): Pair<Int, Int> {
        val rules = runCatching { fetchRelationRules(malId) }.getOrElse { emptyList() }
        if (rules.isEmpty()) return malId to rawEpisodeNumber

        var mappedMal = malId
        var episode = rawEpisodeNumber

        for (rule in rules) {
            val start = rule.from.start
            val end = rule.from.end ?: Int.MAX_VALUE
            val toMalId = rule.to.resolvedMalId
            if (toMalId <= 0) continue

            if (mappedMal == toMalId && episode > end) {
                val seasonLength = end - (start - 1)
                val overflow = episode - end
                episode = overflow + seasonLength
            }

            if (episode in start..end) {
                mappedMal = toMalId
                episode = episode - (start - 1)
            }
        }

        return mappedMal to episode.coerceAtLeast(1)
    }

    private suspend fun fetchRelationRules(malId: Int): List<to.kuudere.anisuge.data.models.AniskipRelationRule> {
        val response = httpClient.get("$ANISKIP_BASE/v2/relation-rules/$malId")
        if (!response.status.isSuccess()) return emptyList()
        val body = response.body<AniskipRelationRulesResponse>()
        return if (body.found) body.rules else emptyList()
    }

    private fun skipTimesRequestUrl(malId: Int, episodeNumber: Int, episodeLengthSec: Double): String {
        val length = episodeLengthSec.coerceIn(60.0, 60.0 * 180.0)
        val query = buildString {
            append("types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed")
            append("&episodeLength=")
            append(length)
        }
        return "$ANISKIP_BASE/v2/skip-times/$malId/$episodeNumber?$query"
    }

    private suspend fun fetchSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLengthSec: Double,
    ): IntroOutroSkip {
        val requestUrl = skipTimesRequestUrl(malId, episodeNumber, episodeLengthSec)
        return try {
            val response = httpClient.get(requestUrl)
            if (!response.status.isSuccess()) {
                val errBody = runCatching { response.bodyAsText() }.getOrNull()?.take(200)
                println("[Aniskip] HTTP ${response.status.value} mal=$malId ep=$episodeNumber body=$errBody")
                return IntroOutroSkip()
            }
            val body = response.body<AniskipSkipTimesResponse>()
            if (!body.found || body.results.isEmpty()) {
                println("[Aniskip] no skips mal=$malId ep=$episodeNumber found=${body.found} n=${body.results.size}")
                return IntroOutroSkip()
            }
            val mapped = body.results.toIntroOutro()
            println(
                "[Aniskip] ok mal=$malId ep=$episodeNumber " +
                    "intro=${mapped.intro?.start}-${mapped.intro?.end} " +
                    "outro=${mapped.outro?.start}-${mapped.outro?.end}",
            )
            mapped
        } catch (e: Exception) {
            println("[Aniskip] skip-times failed mal=$malId ep=$episodeNumber: ${e.message}")
            IntroOutroSkip()
        }
    }

    private fun List<to.kuudere.anisuge.data.models.AniskipSkipResult>.toIntroOutro(): IntroOutroSkip {
        var intro: SkipData? = null
        var outro: SkipData? = null
        for (result in this) {
            val start = result.interval.startTime
            val end = result.interval.endTime
            if (end <= start) continue
            val skip = SkipData(start = start, end = end)
            when (result.skipType.lowercase()) {
                "op", "mixed-op", "recap" -> if (intro == null) intro = skip
                "ed", "mixed-ed" -> if (outro == null) outro = skip
            }
        }
        return IntroOutroSkip(intro = intro, outro = outro)
    }
}
