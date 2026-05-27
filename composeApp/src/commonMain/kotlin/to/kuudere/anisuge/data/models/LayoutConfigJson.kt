package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

/**
 * Persisted JSON envelope for the Home Screen layout configuration.
 *
 * Stored in `SettingsStore` under `home_layout_v1` as a single JSON-encoded string
 * containing a schema [version] and an ordered list of [rows]. The shape is intentionally
 * flat and stable so a future Anisurge BFF settings sync endpoint can adopt it without
 * changes (Requirement 11.1, 11.7).
 *
 * Validates: Requirements 11.1, 11.2
 */
@Serializable
data class LayoutConfigJson(
    val version: Int,
    val rows: List<LayoutRowJson>,
)

/**
 * Single persisted row entry in [LayoutConfigJson.rows].
 *
 * [id] is the case-sensitive `RowId.storageId` literal (e.g. `"continue_watching"`).
 * [visible] is the user-set visibility flag for the row.
 *
 * Validates: Requirements 11.1, 11.2
 */
@Serializable
data class LayoutRowJson(
    val id: String,
    val visible: Boolean,
)
