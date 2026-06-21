package to.kuudere.anisuge.data.models

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class FranchiseEntry(
    val animeId: String,
    val title: String,
    val coverUrl: String?,
    val relationType: String?,
    val format: String?,
    val startDate: DateObject?,
    val isCurrent: Boolean,
)

data class FranchiseRelationRef(
    val animeId: String,
    val relationType: String?,
)

fun AnimeDetails.toFranchiseEntry(
    relationType: String? = null,
    isCurrent: Boolean = false,
): FranchiseEntry = FranchiseEntry(
    animeId = animeId.ifBlank { id },
    title = displayTitle,
    coverUrl = imageUrl.takeIf { it.isNotBlank() },
    relationType = relationType?.prettyRelation(),
    format = format.takeIf { it.isNotBlank() },
    startDate = startDate,
    isCurrent = isCurrent,
)

fun AnimeDetails.franchiseRelationRefs(): List<FranchiseRelationRef> =
    relations.orEmpty().mapNotNull { relation ->
        val node = relation["node"] as? JsonObject ?: relation
        val id = node.stringValue("anime_id")
            ?: node.stringValue("id")
            ?: relation.stringValue("anime_id")
            ?: relation.stringValue("id")
            ?: return@mapNotNull null
        val type = relation.stringValue("relation_type")
            ?: relation.stringValue("relationType")
            ?: relation.stringValue("type")
            ?: node.stringValue("relation_type")
            ?: node.stringValue("relationType")
        FranchiseRelationRef(id, type)
    }.distinctBy { it.animeId }

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

private fun String.prettyRelation(): String =
    replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
