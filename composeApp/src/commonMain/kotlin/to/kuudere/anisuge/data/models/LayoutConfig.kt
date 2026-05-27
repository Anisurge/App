package to.kuudere.anisuge.data.models

/**
 * A single configurable Home Screen row entry.
 *
 * @property id stable identifier for the row
 * @property visible whether the row is rendered on Home Screen
 */
data class LayoutRow(val id: RowId, val visible: Boolean)

/**
 * The user's Home Screen layout: an ordered list of [LayoutRow] entries.
 *
 * Note: [LayoutConfig] is intentionally NOT @Serializable — only the JSON envelope
 * [LayoutConfigJson] is. All operations return new [LayoutConfig] instances
 * (immutable value semantics).
 */
data class LayoutConfig(val rows: List<LayoutRow>) {
    companion object {
        /** Persistence schema version for the current build. (Req 11.2) */
        const val SCHEMA_VERSION: Int = 1

        /** Default_Layout: all four rows visible, in canonical order. (Req 2.1, 2.2) */
        val DEFAULT: LayoutConfig = LayoutConfig(
            rows = listOf(
                LayoutRow(RowId.CONTINUE_WATCHING, visible = true),
                LayoutRow(RowId.LATEST_EPISODES, visible = true),
                LayoutRow(RowId.NEW_ON_APP, visible = true),
                LayoutRow(RowId.UPCOMING, visible = true),
            )
        )
    }

    /**
     * Drop duplicate rows, retaining only the first occurrence of each [RowId].
     *
     * Unknown ids are implicitly impossible because [RowId] is an enum — anything
     * unrecognized is filtered out by [LayoutConfigCodec] during decode before a
     * [LayoutConfig] is constructed. (Req 1.3, 1.5)
     */
    fun sanitize(): LayoutConfig {
        val seen = mutableSetOf<RowId>()
        val kept = mutableListOf<LayoutRow>()
        for (row in rows) {
            if (seen.add(row.id)) kept += row
        }
        return if (kept.size == rows.size) this else LayoutConfig(kept)
    }

    /**
     * Append any [RowId]s not present in [rows], with `visible = true`, in
     * [DEFAULT] order. (Req 2.2, 6.6, 4.8)
     */
    fun mergeWithDefaults(): LayoutConfig {
        val present = rows.mapTo(HashSet()) { it.id }
        val missing = DEFAULT.rows.filter { it.id !in present }
        return if (missing.isEmpty()) this else LayoutConfig(rows + missing)
    }

    /**
     * Move the row with [id] one position toward index 0. No-op if the row is
     * already at index 0 or is not present.
     */
    fun moveUp(id: RowId): LayoutConfig {
        val index = rows.indexOfFirst { it.id == id }
        if (index <= 0) return this
        return reorder(index, index - 1)
    }

    /**
     * Move the row with [id] one position away from index 0. No-op if the row is
     * already at the last position or is not present.
     */
    fun moveDown(id: RowId): LayoutConfig {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0 || index >= rows.size - 1) return this
        return reorder(index, index + 1)
    }

    /**
     * Pure list move: remove the row at [fromIndex] and reinsert it at [toIndex].
     *
     * Returns this unchanged if either index is out of bounds or the indices are
     * equal.
     */
    fun reorder(fromIndex: Int, toIndex: Int): LayoutConfig {
        if (fromIndex == toIndex) return this
        if (fromIndex !in rows.indices || toIndex !in rows.indices) return this
        val mutated = rows.toMutableList()
        val moving = mutated.removeAt(fromIndex)
        mutated.add(toIndex, moving)
        return LayoutConfig(mutated)
    }

    /**
     * Return a new [LayoutConfig] with the [visible] flag set on the row matching
     * [id]. No-op if [id] is not present.
     */
    fun setVisible(id: RowId, visible: Boolean): LayoutConfig {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return this
        if (rows[index].visible == visible) return this
        val mutated = rows.toMutableList()
        mutated[index] = mutated[index].copy(visible = visible)
        return LayoutConfig(mutated)
    }
}
