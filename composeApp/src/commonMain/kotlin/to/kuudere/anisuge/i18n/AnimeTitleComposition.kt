package to.kuudere.anisuge.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import to.kuudere.anisuge.data.models.AnimeDetails
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.AnimeTitle
import to.kuudere.anisuge.data.models.ContinueWatchingItem
import to.kuudere.anisuge.data.models.RecommendationItem
import to.kuudere.anisuge.data.models.ScheduleAnime

val LocalPreferRomajiAnimeTitles = compositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun AnimeTitle.resolveDisplayTitle(): String =
    displayTitle(preferRomajiTitles = LocalPreferRomajiAnimeTitles.current)

@Composable
@ReadOnlyComposable
fun AnimeItem.resolveDisplayTitle(): String = title.resolveDisplayTitle()

@Composable
@ReadOnlyComposable
fun AnimeDetails.resolveDisplayTitle(): String = title.resolveDisplayTitle()

@Composable
@ReadOnlyComposable
fun ContinueWatchingItem.resolveDisplayTitle(): String = anime.resolveDisplayTitle()

@Composable
@ReadOnlyComposable
fun ScheduleAnime.resolveDisplayTitle(): String = title.resolveDisplayTitle()

@Composable
@ReadOnlyComposable
fun RecommendationItem.resolveDisplayTitle(): String = title.resolveDisplayTitle()
