package to.kuudere.anisuge.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

@Composable
actual fun LiquidGlassBottomBar(
    selectedTab: AnisugTab,
    onTabSelect: (AnisugTab) -> Unit,
    modifier: Modifier
) {
    val tabs = remember {
        listOf(
            BottomNavItem(AnisugTab.Calendar, Icons.Outlined.CalendarToday),
            BottomNavItem(AnisugTab.Home, Icons.Outlined.Home),
            BottomNavItem(AnisugTab.Search, Icons.Default.Search),
            BottomNavItem(AnisugTab.Bookmarks, Icons.Outlined.Bookmarks),
            BottomNavItem(AnisugTab.Settings, Icons.Outlined.Settings)
        )
    }
    val backdrop = rememberLayerBackdrop()
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidTabsSurface(
            selectedIndex = selectedIndex,
            tabCount = tabs.size,
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            tabs.forEachIndexed { index, item ->
                LiquidBottomBarIcon(
                    icon = item.icon,
                    isSelected = index == selectedIndex,
                    onClick = { onTabSelect(item.tab) }
                )
            }
        }
    }
}

@Composable
private fun LiquidTabsSurface(
    selectedIndex: Int,
    tabCount: Int,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val accentColor = Color.White
    val containerColor = Color(0xFFFAFAFA).copy(alpha = 0.18f)
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabCount
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val transition = updateTransition(targetState = selectedIndex, label = "liquidBottomBar")
        val animatedIndex by transition.animateFloat(
            transitionSpec = { spring(dampingRatio = 0.78f, stiffness = 520f) },
            label = "liquidBottomBarIndex"
        ) { it.toFloat() }
        val selectedOffsetPx by remember(animatedIndex, tabWidth, isLtr) {
            derivedStateOf {
                if (isLtr) animatedIndex * tabWidth else constraints.maxWidth - ((animatedIndex + 1f) * tabWidth)
            }
        }
        val selectedOffsetDp = with(density) { selectedOffsetPx.toDp() }

        Row(
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(26.dp.toPx(), 26.dp.toPx())
                    },
                    shadow = { Shadow(alpha = 0.18f, radius = 22.dp) },
                    innerShadow = { InnerShadow(radius = 14.dp, alpha = 0.18f) },
                    onDrawSurface = {
                        drawRect(containerColor)
                        drawRect(Color.White.copy(alpha = 0.07f))
                    }
                )
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { if (selectedIndex >= 0) 1.08f else 1f }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics { }
                    .layerBackdrop(tabsBackdrop)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { colorFilter = ColorFilter.tint(accentColor) },
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .offset(x = selectedOffsetDp)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        lens(14.dp.toPx(), 16.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.55f) },
                    shadow = { Shadow(alpha = 0.22f, radius = 18.dp) },
                    innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.16f) },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.14f))
                        drawRect(Color.Black.copy(alpha = 0.04f))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabCount)
        )
    }
}

@Composable
internal fun RowScope.LiquidBottomBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val animatedTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.62f),
        animationSpec = tween(durationMillis = 220)
    )
    val scaleProvider = LocalLiquidBottomTabScale.current
    val targetScale = if (isSelected) scaleProvider() else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = animatedTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

internal data class BottomNavItem(
    val tab: AnisugTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
