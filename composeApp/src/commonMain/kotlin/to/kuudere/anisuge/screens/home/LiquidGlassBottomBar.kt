package to.kuudere.anisuge.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun LiquidGlassBottomBar(
    selectedTab: AnisugTab,
    onTabSelect: (AnisugTab) -> Unit,
    modifier: Modifier = Modifier
)
