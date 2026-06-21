package to.kuudere.anisuge.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchTest {
    @Test
    fun findsAliasesAcrossTitleAndKeywords() {
        assertEquals("backup", searchSettings("export library").first().id)
        assertEquals("player-enhancements", searchSettings("anime4k shader").first().id)
        assertEquals("audio-language", searchSettings("dub").first().id)
    }

    @Test
    fun requiresEverySearchTerm() {
        val results = searchSettings("download folder")
        assertTrue(results.any { it.id == "download-path" })
        assertTrue(results.none { it.id == "storage" })
    }

    @Test
    fun blankSearchDoesNotReturnEverything() {
        assertTrue(searchSettings("   ").isEmpty())
    }
}
