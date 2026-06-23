package to.kuudere.anisuge.extensions

import kotlin.math.max

internal object ExtensionSourceMapper {
    const val AUTO_PICK_THRESHOLD = 0.92

    fun formatSearchTitles(
        titles: List<String>,
        synonyms: List<String> = emptyList(),
    ): List<String> = buildList {
        titles.filter { it.isNotBlank() && !isInvalidTitle(it) }.forEach { add(it.trim()) }
        synonyms.take(5).filter { it.isNotBlank() && !isInvalidTitle(it) }.forEach { add(it.trim()) }
    }.distinct()

    suspend fun mapMedia(
        source: ExtensionSource,
        runtime: ExtensionRuntime,
        searchTitles: List<String>,
        savedTitle: String? = null,
        chooseCandidate: suspend (List<Pair<ExtensionMedia, Double>>, String) -> ExtensionMedia?,
    ): Pair<ExtensionMedia, Double>? {
        var bestScore = 0.0
        var bestMatch: ExtensionMedia? = null
        var fallbackResults = emptyList<Pair<ExtensionMedia, Double>>()

        suspend fun runSearch(query: String, sourceTitle: String, heavy: Boolean) {
            if (bestScore >= 0.98) return
            val results = runtime.search(source, query).distinctBy { it.url }
            if (results.isEmpty()) return

            if (!savedTitle.isNullOrBlank() &&
                results.any { normalizeLight(it.title) == normalizeLight(savedTitle) }
            ) {
                val hit = results.first { normalizeLight(it.title) == normalizeLight(savedTitle) }
                bestScore = 2.0
                bestMatch = hit
                fallbackResults = results.map { media ->
                    media to scoreTitles(searchTitles + savedTitle, media.title, heavy)
                }
                return
            }

            for (result in results) {
                val resultTitle = result.title
                val resultSeason = extractSeasonNumber(resultTitle)
                for (target in searchTitles) {
                    val score = scoreTitles(listOf(target), resultTitle, heavy, extractSeasonNumber(target), resultSeason)
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = result
                        fallbackResults = results.map { media ->
                            media to scoreTitles(searchTitles, media.title, heavy)
                        }
                    }
                    if (bestScore >= 0.98) return
                }
            }
        }

        if (!savedTitle.isNullOrBlank()) {
            runSearch(savedTitle, savedTitle, heavy = false)
            if (bestScore >= 0.7 && bestMatch != null) return bestMatch!! to bestScore
        }

        val english = searchTitles.firstOrNull().orEmpty()
        val romaji = searchTitles.getOrNull(1).orEmpty()
        if (english.isNotBlank()) {
            runSearch(english, english, heavy = false)
            if (bestScore >= AUTO_PICK_THRESHOLD && bestMatch != null) return bestMatch!! to bestScore
        }
        if (bestScore < 0.95 && romaji.isNotBlank() && normalizeLight(romaji) != normalizeLight(english)) {
            runSearch(romaji, romaji, heavy = false)
            if (bestScore >= AUTO_PICK_THRESHOLD && bestMatch != null) return bestMatch!! to bestScore
        }
        if (bestScore < 0.9) {
            for (synonym in searchTitles.drop(2)) {
                if (bestScore >= 0.95) break
                runSearch(synonym, synonym, heavy = false)
            }
            if (bestScore >= AUTO_PICK_THRESHOLD && bestMatch != null) return bestMatch!! to bestScore
        }
        if (bestScore < 0.7 && english.isNotBlank()) {
            runSearch(normalizeHeavy(english), english, heavy = true)
        }

        if (bestMatch == null) return null

        if (bestScore >= AUTO_PICK_THRESHOLD || fallbackResults.size <= 1) {
            return bestMatch!! to bestScore
        }

        val chosen = chooseCandidate(fallbackResults.sortedByDescending { it.second }.take(8), english)
            ?: return null
        val confidence = fallbackResults.firstOrNull { it.first.url == chosen.url }?.second ?: bestScore
        return chosen to confidence
    }

    private fun scoreTitles(
        targets: List<String>,
        candidate: String,
        heavy: Boolean,
        targetSeason: Int? = null,
        candidateSeason: Int? = null,
    ): Double = targets.maxOfOrNull { target ->
        val normalizedTarget = if (heavy) normalizeHeavy(target) else normalizeLight(target)
        val normalizedCandidate = if (heavy) normalizeHeavy(candidate) else normalizeLight(candidate)
        calculateMatchScore(
            normalizedTarget,
            normalizedCandidate,
            targetSeason ?: extractSeasonNumber(target),
            candidateSeason ?: extractSeasonNumber(candidate),
        )
    } ?: 0.0

    private fun calculateMatchScore(
        sourceTitle: String,
        targetTitle: String,
        sourceSeason: Int?,
        targetSeason: Int?,
    ): Double {
        if (sourceTitle.isEmpty() || targetTitle.isEmpty()) return 0.0
        if (sourceTitle == targetTitle) return 1.0

        val sourceTokens = sourceTitle.split(" ").filter { it.isNotBlank() }.toSet()
        val targetTokens = targetTitle.split(" ").filter { it.isNotBlank() }.toSet()
        val tokenScore = if (sourceTokens.isEmpty() || targetTokens.isEmpty()) {
            0.0
        } else {
            sourceTokens.intersect(targetTokens).size.toDouble() / max(sourceTokens.size, targetTokens.size)
        }
        val containsScore = when {
            targetTitle.contains(sourceTitle) || sourceTitle.contains(targetTitle) -> 0.88
            else -> tokenScore
        }
        val partialScore = longestCommonSubstringRatio(sourceTitle, targetTitle)
        var score = (tokenScore * 0.4) + (containsScore * 0.35) + (partialScore * 0.25)
        if (targetSeason != null && sourceSeason != null) {
            score += if (targetSeason == sourceSeason) 0.12 else -0.08
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun longestCommonSubstringRatio(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dp = IntArray(b.length + 1)
        var best = 0
        for (i in a.indices) {
            var prev = 0
            for (j in b.indices) {
                val temp = dp[j + 1]
                dp[j + 1] = if (a[i] == b[j]) prev + 1 else 0
                prev = temp
                best = max(best, dp[j + 1])
            }
        }
        return best.toDouble() / max(a.length, b.length)
    }

    private fun normalizeLight(value: String) = value.trim().lowercase()

    private fun normalizeHeavy(value: String): String =
        value.replace(Regex("""\bseason\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .trim()
            .lowercase()

    private fun extractSeasonNumber(title: String): Int? {
        val patterns = listOf(
            Regex("""\b(\d+)(?:th|st|nd|rd)?\s*season\b""", RegexOption.IGNORE_CASE),
            Regex("""\bseason\s*(\d+)\b""", RegexOption.IGNORE_CASE),
            Regex("""\s(\d+)\b(?!\s*[a-zA-Z])"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(title) ?: continue
            val group = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
            group.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun isInvalidTitle(value: String): Boolean {
        val trimmed = value.trim().lowercase()
        return trimmed.isEmpty() || trimmed == "?" || trimmed == "??" || trimmed == "null"
    }
}