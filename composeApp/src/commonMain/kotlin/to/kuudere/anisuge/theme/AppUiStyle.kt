package to.kuudere.anisuge.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-level UI packages inspired by reference apps in `/ref`.
 * Selecting a style applies palette, navigation, and shape metrics together.
 */
enum class AppUiStyle(
    val id: String,
    val label: String,
    val description: String,
    val referenceApp: String,
) {
    Anisurge(
        id = "anisurge",
        label = "Anisurge",
        description = "Default Anisurge look",
        referenceApp = "Anisurge",
    ),
    Dantotsu(
        id = "dantotsu",
        label = "Dantotsu",
        description = "Rounded cards, violet accent, solid floating pill nav",
        referenceApp = "Dantotsu",
    ),
    ReDantotsu(
        id = "redantotsu",
        label = "ReDantotsu",
        description = "Liquid glass nav, frosted surfaces, iOS-blue accent",
        referenceApp = "ReDantotsu",
    );

    companion object {
        fun fromId(id: String?): AppUiStyle = entries.firstOrNull { it.id == id } ?: Anisurge
    }
}

/** Reactive layout metrics driven by the active [AppUiStyle]. */
object AppUiMetrics {
    var cardRadius by mutableStateOf(12.dp)
        internal set
    var sheetRadius by mutableStateOf(24.dp)
        internal set
    var navPillRadius by mutableStateOf(28.dp)
        internal set
    var settingsTitleSize by mutableStateOf(42)
        internal set
    var elevatedCards by mutableStateOf(false)
        internal set
}

data class UiStyleBundle(
    val themeId: AppThemeId,
    val floatingBottomNav: Boolean,
    val liquidGlassBottomNav: Boolean,
    val cardRadius: Dp,
    val sheetRadius: Dp,
    val navPillRadius: Dp,
    val settingsTitleSize: Int,
    val elevatedCards: Boolean,
)

fun bundleForStyle(style: AppUiStyle): UiStyleBundle = when (style) {
    AppUiStyle.Anisurge -> UiStyleBundle(
        themeId = AppThemeId.Default,
        floatingBottomNav = true,
        liquidGlassBottomNav = false,
        cardRadius = 12.dp,
        sheetRadius = 24.dp,
        navPillRadius = 28.dp,
        settingsTitleSize = 42,
        elevatedCards = false,
    )
    AppUiStyle.Dantotsu -> UiStyleBundle(
        themeId = AppThemeId.Dantotsu,
        floatingBottomNav = true,
        liquidGlassBottomNav = false,
        cardRadius = 16.dp,
        sheetRadius = 20.dp,
        navPillRadius = 40.dp,
        settingsTitleSize = 28,
        elevatedCards = true,
    )
    AppUiStyle.ReDantotsu -> UiStyleBundle(
        themeId = AppThemeId.ReDantotsu,
        floatingBottomNav = true,
        liquidGlassBottomNav = true,
        cardRadius = 24.dp,
        sheetRadius = 28.dp,
        navPillRadius = 40.dp,
        settingsTitleSize = 32,
        elevatedCards = false,
    )
}

/** Apply style metrics to the global holder (does not persist settings). */
fun applyUiStyleMetrics(style: AppUiStyle) {
    val bundle = bundleForStyle(style)
    AppUiMetrics.cardRadius = bundle.cardRadius
    AppUiMetrics.sheetRadius = bundle.sheetRadius
    AppUiMetrics.navPillRadius = bundle.navPillRadius
    AppUiMetrics.settingsTitleSize = bundle.settingsTitleSize
    AppUiMetrics.elevatedCards = bundle.elevatedCards
}