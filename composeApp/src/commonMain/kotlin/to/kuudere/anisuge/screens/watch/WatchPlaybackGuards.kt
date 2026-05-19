package to.kuudere.anisuge.screens.watch

import to.kuudere.anisuge.data.models.SkipData

internal fun SkipData?.isPositionInRange(positionSec: Double): Boolean {
    val start = this?.start ?: return false
    val end = this?.end ?: return false
    return positionSec >= start + 0.25 && positionSec < end - 0.25
}

/** Seek target (seconds) when [positionSec] is inside intro or outro, else null. Intro wins if both match. */
internal fun skipSeekTargetForPosition(
    intro: SkipData?,
    outro: SkipData?,
    positionSec: Double,
    durationSec: Double,
): Double? {
    if (durationSec <= 0) return null
    if (intro.isPositionInRange(positionSec)) {
        val end = intro?.end ?: return null
        return (end + 0.5).coerceAtMost(durationSec - 0.25)
    }
    if (outro.isPositionInRange(positionSec)) {
        val end = outro?.end ?: return null
        return (end + 0.5).coerceAtMost(durationSec - 0.25)
    }
    return null
}

internal fun clampSkipToDuration(skip: SkipData?, durationSec: Double): SkipData? {
    if (skip == null || durationSec <= 0) return skip
    val start = skip.start ?: return null
    val end = skip.end ?: return null
    if (end <= start) return null
    val clampedStart = start.coerceIn(0.0, durationSec)
    val clampedEnd = end.coerceIn(clampedStart + 0.5, durationSec)
    if (clampedEnd <= clampedStart) return null
    return SkipData(start = clampedStart, end = clampedEnd)
}

/**
 * Auto-next must not run when mpv ends a failed/broken stream (common on Suzu token expiry).
 */
internal fun watchedEnoughForAutoNext(
    positionSec: Double,
    durationSec: Double,
    peakPositionSec: Double = positionSec,
): Boolean {
    if (durationSec < 45.0) return false
    if (positionSec < 20.0) return false
    // User scrubbed back from the end — do not treat stale "near end" samples as finished.
    val atEndNow = positionSec >= durationSec - 2.5 || positionSec >= durationSec * 0.88
    if (!atEndNow) return false
    // Must have played most of the episode forward at least once (not a seek-to-end glitch).
    if (peakPositionSec < durationSec * 0.75) return false
    return true
}

/** After a manual seek, block auto-skip / auto-next so scrubbing back from the end does not chain to the next ep. */
internal const val USER_SEEK_AUTO_BLOCK_MS = 12_000L

internal fun isWithinUserSeekCooldown(lastUserSeekAtMs: Long, nowMs: Long): Boolean =
    lastUserSeekAtMs > 0L && nowMs - lastUserSeekAtMs < USER_SEEK_AUTO_BLOCK_MS

internal fun WatchUiState.effectiveIntroSkip() = introSkip ?: streamingData?.intro

internal fun WatchUiState.effectiveOutroSkip() = outroSkip ?: streamingData?.outro

internal fun isLikelyPlaybackFailure(positionSec: Double, durationSec: Double): Boolean {
    if (durationSec <= 0.0) return true
    if (positionSec < 15.0 && durationSec < 120.0) return true
    if (durationSec >= 45.0 && positionSec < durationSec * 0.05) return true
    return false
}
