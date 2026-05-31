package to.kuudere.anisuge.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour roles for the whole app.
 *
 * Screens historically hardcoded their own black/white/accent colours, so swapping the
 * theme only repainted Material3 controls. [AppColors] is a single, snapshot-state backed
 * holder every screen reads through, so changing the active [AppThemeId] recomposes the
 * entire UI — including the legacy screens that used fixed surfaces.
 */
data class AppPalette(
    val isDark: Boolean,
    /** Root / screen background. */
    val background: Color,
    /** Card, sheet and elevated container background. */
    val surface: Color,
    /** Hovered / selected / more-elevated surface. */
    val surfaceVariant: Color,
    /** Hairline borders and dividers. */
    val border: Color,
    /** Primary text and icons. */
    val text: Color,
    /** Secondary / muted text. */
    val textMuted: Color,
    /** Tertiary / dim text (timestamps, separators). */
    val textDim: Color,
    /** Brand accent used for highlights, active states and primary buttons. */
    val accent: Color,
    /** Content colour drawn on top of [accent]. */
    val onAccent: Color,
    /** Error / destructive colour. */
    val error: Color,
)

/**
 * Global, reactive colour holder. Reading any property inside a composable subscribes that
 * composable to theme changes; writes only happen from [AnisugTheme] / [applyAppPalette].
 */
object AppColors {
    var isDark by mutableStateOf(true)
        internal set
    var background by mutableStateOf(Color(0xFF000000))
        internal set
    var surface by mutableStateOf(Color(0xFF0A0A0A))
        internal set
    var surfaceVariant by mutableStateOf(Color(0xFF141414))
        internal set
    var border by mutableStateOf(Color(0x14FFFFFF))
        internal set
    var text by mutableStateOf(Color(0xFFFFFFFF))
        internal set
    var textMuted by mutableStateOf(Color(0xFF9E9E9E))
        internal set
    var textDim by mutableStateOf(Color(0xFF6E6E6E))
        internal set
    var accent by mutableStateOf(Color(0xFFBF80FF))
        internal set
    var onAccent by mutableStateOf(Color(0xFF000000))
        internal set
    var error by mutableStateOf(Color(0xFFFF6B6B))
        internal set
}

/** Push [palette] into the global [AppColors] holder. No-ops for unchanged values. */
internal fun applyAppPalette(palette: AppPalette) {
    AppColors.isDark = palette.isDark
    AppColors.background = palette.background
    AppColors.surface = palette.surface
    AppColors.surfaceVariant = palette.surfaceVariant
    AppColors.border = palette.border
    AppColors.text = palette.text
    AppColors.textMuted = palette.textMuted
    AppColors.textDim = palette.textDim
    AppColors.accent = palette.accent
    AppColors.onAccent = palette.onAccent
    AppColors.error = palette.error
}

// ── Theme palettes ───────────────────────────────────────────────────────────

private val LightPalette = AppPalette(
    isDark = false,
    background = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAEBEF),
    border = Color(0x14000000),
    text = Color(0xFF121316),
    textMuted = Color(0xFF5F636B),
    textDim = Color(0xFF9AA0A8),
    accent = Color(0xFF7C3AED),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFD32F2F),
)

private val DefaultDarkPalette = AppPalette(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF161616),
    border = Color(0x14FFFFFF),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9E9E9E),
    textDim = Color(0xFF6E6E6E),
    accent = Color(0xFFBF80FF),
    onAccent = Color(0xFF000000),
    error = Color(0xFFFF6B6B),
)

private val AmoledPalette = AppPalette(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0B0B0B),
    border = Color(0x1FFFFFFF),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFBDBDBD),
    textDim = Color(0xFF7A7A7A),
    accent = Color(0xFFFFFFFF),
    onAccent = Color(0xFF000000),
    error = Color(0xFFFF8A80),
)

private val PurplePalette = AppPalette(
    isDark = true,
    background = Color(0xFF08050C),
    surface = Color(0xFF0D0714),
    surfaceVariant = Color(0xFF1A1025),
    border = Color(0xFF352147),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFC7B7D9),
    textDim = Color(0xFF8A7AA0),
    accent = Color(0xFFBF80FF),
    onAccent = Color(0xFF000000),
    error = Color(0xFFFF8A80),
)

private val RedPalette = AppPalette(
    isDark = true,
    background = Color(0xFF090000),
    surface = Color(0xFF110102),
    surfaceVariant = Color(0xFF250507),
    border = Color(0xFF451013),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFE0B3B3),
    textDim = Color(0xFF9A6B6B),
    accent = Color(0xFFE50914),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFFFB4AB),
)

private val OceanPalette = AppPalette(
    isDark = true,
    background = Color(0xFF03080F),
    surface = Color(0xFF071521),
    surfaceVariant = Color(0xFF0E2436),
    border = Color(0xFF173447),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFA9C4D6),
    textDim = Color(0xFF6E8C9E),
    accent = Color(0xFF38BDF8),
    onAccent = Color(0xFF00121E),
    error = Color(0xFFFF8A80),
)

private val MidnightPalette = AppPalette(
    isDark = true,
    background = Color(0xFF05060F),
    surface = Color(0xFF0B0E1F),
    surfaceVariant = Color(0xFF161A33),
    border = Color(0xFF272C4D),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFB4B8D6),
    textDim = Color(0xFF7A7FA3),
    accent = Color(0xFF818CF8),
    onAccent = Color(0xFF0A0B16),
    error = Color(0xFFFF8A80),
)

private val HighContrastPalette = AppPalette(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF050505),
    surfaceVariant = Color(0xFF1E1E1E),
    border = Color(0xFF777777),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFE0E0E0),
    textDim = Color(0xFFBDBDBD),
    accent = Color(0xFFFFFF00),
    onAccent = Color(0xFF000000),
    error = Color(0xFFFF5252),
)

/** Resolve the palette for a [AppThemeId]. */
fun paletteFor(themeId: AppThemeId): AppPalette = when (themeId) {
    AppThemeId.Light -> LightPalette
    AppThemeId.Default -> DefaultDarkPalette
    AppThemeId.Amoled -> AmoledPalette
    AppThemeId.Purple -> PurplePalette
    AppThemeId.Red -> RedPalette
    AppThemeId.Ocean -> OceanPalette
    AppThemeId.Midnight -> MidnightPalette
    AppThemeId.HighContrast -> HighContrastPalette
}

/** Build a Material3 [ColorScheme] from an [AppPalette] so Material controls stay in sync. */
fun AppPalette.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = textMuted,
        onSecondary = onAccent,
        tertiary = surfaceVariant,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textMuted,
        error = error,
        outline = border,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = textMuted,
        onSecondary = Color.White,
        tertiary = surfaceVariant,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textMuted,
        error = error,
        outline = border,
    )
}
