package to.kuudere.anisuge.data.services

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.LayoutConfig
import to.kuudere.anisuge.data.models.LayoutConfigJson
import to.kuudere.anisuge.data.models.LayoutRow
import to.kuudere.anisuge.data.models.LayoutRowJson
import to.kuudere.anisuge.data.models.RowId

/**
 * Discriminated outcome of [LayoutConfigCodec.decode].
 *
 * The decoder reports schema-version drift and structural failures separately so
 * `SettingsStore` can apply the asymmetric healing policy specified in the design:
 * `Invalid` is overwritten with `Default_Layout` while `VersionTooNew` preserves
 * the stored bytes byte-for-byte (Req 6.4, 11.3, 11.5, 11.6).
 */
sealed interface DecodeResult {
    /** Successfully decoded a v1 [LayoutConfig]. Unknown row ids are already dropped. */
    data class Success(val config: LayoutConfig) : DecodeResult

    /** Stored JSON parsed but `version > LayoutConfig.SCHEMA_VERSION`. Preserve bytes. */
    data class VersionTooNew(val version: Int) : DecodeResult

    /** Stored JSON was malformed, missing required fields, or had wrong types. */
    data class Invalid(val reason: String) : DecodeResult
}

/**
 * Pure (de)serializer for the persisted Home Screen layout JSON envelope.
 *
 * Encodes a [LayoutConfig] into the v1 [LayoutConfigJson] schema and decodes back
 * into a [DecodeResult]. Sanitization, deduping, and merging with defaults happen
 * in `SettingsStore` (`sanitize().mergeWithDefaults()`), not here — the codec
 * stays pure so it can be unit-/property-tested without DataStore.
 *
 * Validates: Requirements 6.4, 6.5, 11.3, 11.5, 11.6
 */
object LayoutConfigCodec {
    /**
     * Workspace convention: tolerate unknown JSON keys so future BFF-added fields
     * don't break v1 readers (see AGENTS.md learned facts on `ignoreUnknownKeys`).
     */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Encode [config] into the v1 JSON envelope. Always emits
     * `version = LayoutConfig.SCHEMA_VERSION`.
     */
    fun encode(config: LayoutConfig): String {
        val envelope = LayoutConfigJson(
            version = LayoutConfig.SCHEMA_VERSION,
            rows = config.rows.map { row ->
                LayoutRowJson(id = row.id.storageId, visible = row.visible)
            },
        )
        return json.encodeToString(LayoutConfigJson.serializer(), envelope)
    }

    /**
     * Decode [jsonString] from the v1 schema. Unknown row ids are dropped silently;
     * deduping/merge-with-defaults is the caller's responsibility.
     */
    fun decode(jsonString: String): DecodeResult {
        val envelope = try {
            json.decodeFromString(LayoutConfigJson.serializer(), jsonString)
        } catch (e: SerializationException) {
            return DecodeResult.Invalid(reason = e.message ?: "decode failed")
        } catch (e: Exception) {
            return DecodeResult.Invalid(reason = e.message ?: "decode failed")
        }

        if (envelope.version > LayoutConfig.SCHEMA_VERSION) {
            return DecodeResult.VersionTooNew(envelope.version)
        }

        val rows = envelope.rows.mapNotNull { jsonRow ->
            val rowId = RowId.fromStorageId(jsonRow.id) ?: return@mapNotNull null
            LayoutRow(id = rowId, visible = jsonRow.visible)
        }
        return DecodeResult.Success(LayoutConfig(rows))
    }
}
