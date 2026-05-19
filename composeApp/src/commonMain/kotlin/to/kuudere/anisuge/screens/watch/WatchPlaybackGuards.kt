package to.kuudere.anisuge.screens.watch

/**
 * Auto-next must not run when mpv ends a failed/broken stream (common on Suzu token expiry).
 */
internal fun watchedEnoughForAutoNext(positionSec: Double, durationSec: Double): Boolean {
    if (durationSec < 45.0) return false
    if (positionSec < 20.0) return false
    if (positionSec >= durationSec - 2.5) return true
    return positionSec >= durationSec * 0.88
}

internal fun isLikelyPlaybackFailure(positionSec: Double, durationSec: Double): Boolean {
    if (durationSec <= 0.0) return true
    if (positionSec < 15.0 && durationSec < 120.0) return true
    if (durationSec >= 45.0 && positionSec < durationSec * 0.05) return true
    return false
}
