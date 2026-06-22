package to.kuudere.anisuge.data.services

import to.kuudere.anisuge.data.models.TrackerSyncAction
import to.kuudere.anisuge.data.models.TrackerSyncJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoTrackingSyncServiceTest {
    @Test
    fun coalescingKeepsKnownIdsAndLatestState() {
        val old = TrackerSyncJob(
            key = "anilist:10",
            action = TrackerSyncAction.UPSERT,
            animeId = "show",
            malId = 20,
            anilistId = 10,
            status = "PLANNING",
            progress = 2,
            totalEpisodes = 12,
            pendingMal = true,
            pendingAnilist = true,
            updatedAt = 1,
        )
        val latest = TrackerSyncJob(
            key = old.key,
            action = TrackerSyncAction.UPSERT,
            animeId = "show",
            status = "WATCHING",
            progress = 3,
            pendingMal = true,
            pendingAnilist = true,
            updatedAt = 2,
        )

        val result = coalesceTrackerJobs(old, latest)

        assertEquals(20, result.malId)
        assertEquals(10, result.anilistId)
        assertEquals("WATCHING", result.status)
        assertEquals(3, result.progress)
        assertEquals(12, result.totalEpisodes)
    }

    @Test
    fun deleteDoesNotCarryOldStatus() {
        val old = TrackerSyncJob(
            key = "mal:20",
            action = TrackerSyncAction.UPSERT,
            animeId = "show",
            malId = 20,
            status = "WATCHING",
            progress = 4,
            pendingMal = true,
            updatedAt = 1,
        )
        val latest = TrackerSyncJob(
            key = old.key,
            action = TrackerSyncAction.DELETE,
            animeId = "show",
            malId = 20,
            pendingMal = true,
            updatedAt = 2,
        )

        val result = coalesceTrackerJobs(old, latest)

        assertEquals(TrackerSyncAction.DELETE, result.action)
        assertNull(result.status)
        assertEquals(4, result.progress)
    }
}
