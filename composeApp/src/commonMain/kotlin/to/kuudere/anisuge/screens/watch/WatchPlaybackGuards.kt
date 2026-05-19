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
internal fun watchedEnoughForAutoNext(positionSec: Double, durationSec: Double): Boolean {
    if (durationSec < 45.0) return false
    if (positionSec < 20.0) return false
    if (positionSec >= durationSec - 2.5) return true
    return positionSec >= durationSec * 0.88
}

internal fun WatchUiState.effectiveIntroSkip() = introSkip ?: streamingData?.intro

internal fun WatchUiState.effectiveOutroSkip() = outroSkip ?: streamingData?.outro

internal fun isLikelyPlaybackFailure(positionSec: Double, durationSec: Double): Boolean {
    if (durationSec <= 0.0) return true
    if (positionSec < 15.0 && durationSec < 120.0) return true
    if (durationSec >= 45.0 && positionSec < durationSec * 0.05) return true
    return false
}
