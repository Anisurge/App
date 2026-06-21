package to.kuudere.anisuge.data.models

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class FranchiseModelsTest {
    @Test
    fun relationRefsHandleFlatAndNestedShapesAndDeduplicate() {
        val details = AnimeDetails(
            relations = listOf(
                buildJsonObject {
                    put("anime_id", "season-two")
                    put("relation_type", "SEQUEL")
                },
                buildJsonObject {
                    put("relation_type", "PREQUEL")
                    put("node", buildJsonObject { put("id", "season-one") })
                },
                buildJsonObject {
                    put("anime_id", "season-two")
                    put("relation_type", "SEQUEL")
                },
            )
        )

        assertEquals(
            listOf(
                FranchiseRelationRef("season-two", "SEQUEL"),
                FranchiseRelationRef("season-one", "PREQUEL"),
            ),
            details.franchiseRelationRefs(),
        )
    }
}
