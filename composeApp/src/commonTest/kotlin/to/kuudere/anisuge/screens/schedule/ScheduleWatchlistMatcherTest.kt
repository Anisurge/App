package to.kuudere.anisuge.screens.schedule

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import to.kuudere.anisuge.data.models.ScheduleAnime

class ScheduleWatchlistMatcherTest {
    @Test
    fun matchesAniListMalAndSlugIdentifiers() {
        assertTrue(
            matchesScheduleWatchlist(
                anime = ScheduleAnime(animeId = "other", anilistId = 21),
                animeIds = emptySet(),
                anilistIds = setOf(21),
                malIds = emptySet(),
            )
        )
        assertTrue(
            matchesScheduleWatchlist(
                anime = ScheduleAnime(animeId = "other", malId = 20),
                animeIds = emptySet(),
                anilistIds = emptySet(),
                malIds = setOf(20),
            )
        )
        assertTrue(
            matchesScheduleWatchlist(
                anime = ScheduleAnime(animeId = "Solo-Leveling-1"),
                animeIds = setOf("solo-leveling-1"),
                anilistIds = emptySet(),
                malIds = emptySet(),
            )
        )
    }

    @Test
    fun rejectsUnrelatedAnime() {
        assertFalse(
            matchesScheduleWatchlist(
                anime = ScheduleAnime(animeId = "unrelated", anilistId = 99, malId = 100),
                animeIds = setOf("solo-leveling-1"),
                anilistIds = setOf(21),
                malIds = setOf(20),
            )
        )
    }
}
