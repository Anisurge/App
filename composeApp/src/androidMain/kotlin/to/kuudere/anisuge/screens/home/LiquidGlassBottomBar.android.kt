package to.kuudere.anisuge.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import to.kuudere.anisuge.ui.tvFocusableClick

@Composable
actual fun LiquidGlassBottomBar(
    selectedTab: AnisugTab,
    onTabSelect: (AnisugTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier
) {
    val tabs = remember {
        listOf(
            BottomNavItem(AnisugTab.Calendar, Icons.Outlined.CalendarToday),
            BottomNavItem(AnisugTab.Home,     Icons.Outlined.Home),
            BottomNavItem(AnisugTab.Search,   Icons.Default.Search),
            BottomNavItem(AnisugTab.Bookmarks,Icons.Outlined.Bookmarks),
            BottomNavItem(AnisugTab.Settings, Icons.Outlined.Settings)
        )
    }
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BlurPillSurface(
            selectedIndex = selectedIndex,
            tabCount = tabs.size,
            hazeState = hazeState,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            tabs.forEachIndexed { index, item ->
                BlurNavIcon(
                    icon = item.icon,
                    isSelected = index == selectedIndex,
                    onClick = { onTabSelect(item.tab) }
                )
            }
        }
    }
}

@Composable
private fun BlurPillSurface(
    selectedIndex: Int,
    tabCount: Int,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val navShape = RoundedCornerShape(30.dp)
    val pillShape = RoundedCornerShape(22.dp)

    val transition = updateTransition(targetState = selectedIndex, label = "blurNav")
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabCount
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animatedIndex by transition.animateFloat(
            transitionSpec = { spring(dampingRatio = 0.78f, stiffness = 520f) },
            label = "blurNavIndex"
        ) { it.toFloat() }
        val selectedOffsetPx by remember(animatedIndex, tabWidth, isLtr) {
            derivedStateOf {
                if (isLtr) animatedIndex * tabWidth else constraints.maxWidth - ((animatedIndex + 1f) * tabWidth)
            }
        }
        val selectedOffsetDp = with(density) { selectedOffsetPx.toDp() }
        val pillWidthDp = with(density) { tabWidth.toDp() }

        // ── Outer pill container with 20% blur ─────────────────────────
        Row(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth()
                .clip(navShape)
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.20f))),
                        blurRadius = 20.dp,
                        noiseFactor = 0.04f,
                    )
                )
                .background(Color(0xFF141414).copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), navShape)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // ── Sliding active indicator pill ───────────────────────────────
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .offset(x = selectedOffsetDp)
                .height(56.dp)
                .then(Modifier.fillMaxWidth(1f / tabCount))
                .clip(pillShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), pillShape)
        )
    }
}

@Composable
internal fun RowScope.BlurNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val animatedTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.48f),
        animationSpec = tween(durationMillis = 200)
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
            .tvFocusableClick(shape = RoundedCornerShape(22.dp), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = animatedTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

internal data class BottomNavItem(
    val tab: AnisugTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
