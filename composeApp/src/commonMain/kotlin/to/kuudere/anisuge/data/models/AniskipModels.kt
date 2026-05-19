package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AniskipSkipTimesResponse(
    val found: Boolean = false,
    val results: List<AniskipSkipResult> = emptyList(),
    val message: String? = null,
    val statusCode: Int? = null,
)

@Serializable
data class AniskipSkipResult(
    val interval: AniskipInterval = AniskipInterval(),
    val skipType: String = "",
)

@Serializable
data class AniskipInterval(
    val startTime: Double = 0.0,
    val endTime: Double = 0.0,
)

@Serializable
data class AniskipRelationRulesResponse(
    val found: Boolean = false,
    val rules: List<AniskipRelationRule> = emptyList(),
)

@Serializable
data class AniskipRelationRule(
    val from: AniskipRelationRange = AniskipRelationRange(),
    val to: AniskipRelationTarget = AniskipRelationTarget(),
)

@Serializable
data class AniskipRelationRange(
    val start: Int = 0,
    val end: Int? = null,
)

@Serializable
data class AniskipRelationTarget(
    @SerialName("malId") val malId: Int = 0,
    @SerialName("animeId") val animeId: Int = 0,
) {
    val resolvedMalId: Int get() = malId.takeIf { it > 0 } ?: animeId
}
