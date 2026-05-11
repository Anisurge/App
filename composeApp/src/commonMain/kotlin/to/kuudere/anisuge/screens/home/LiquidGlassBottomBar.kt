package to.kuudere.anisuge.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState

@Composable
expect fun LiquidGlassBottomBar(
    selectedTab: AnisugTab,
    onTabSelect: (AnisugTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
)
