package to.kuudere.anisuge.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun LiquidGlassBottomBar(
    selectedTab: AnisugTab,
    onTabSelect: (AnisugTab) -> Unit,
    modifier: Modifier
) {
    // Desktop does not render the mobile floating bottom bar path.
}
