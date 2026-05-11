package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ReanimeExportLibrary(
    val Completed: List<ReanimeExportItem> = emptyList(),
    val Watching: List<ReanimeExportItem> = emptyList(),
    val Dropped: List<ReanimeExportItem> = emptyList(),
    @SerialName("On Hold") val onHold: List<ReanimeExportItem> = emptyList(),
    @SerialName("Plan To Watch") val planToWatch: List<ReanimeExportItem> = emptyList(),
)

@Serializable
data class ReanimeExportItem(
    val name: String? = null,
    @SerialName("mal_id") val mal_id: Int? = null,
    val malId: Int? = null,
    val watchListType: Int? = null,
    /** ISO string, epoch number, or `{ "year", "month", "day" }` from API. */
    @SerialName("started_at") val started_at: JsonElement? = null,
    @SerialName("completed_at") val completed_at: JsonElement? = null,
)
