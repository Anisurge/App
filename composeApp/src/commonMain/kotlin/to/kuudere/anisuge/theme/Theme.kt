package to.kuudere.anisuge.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeId(val id: String, val label: String, val description: String) {
    Default("default", "Default", "Clean black and white"),
    Amoled("amoled", "AMOLED", "Pure black with soft gray surfaces"),
    Purple("purple", "Purple", "Kuudere purple accent"),
    Red("red", "Cinema Red", "Streaming-style red accent"),
    HighContrast("high_contrast", "High Contrast", "Brighter borders and controls");

    companion object {
        fun fromId(id: String?): AppThemeId = entries.firstOrNull { it.id == id } ?: Default
    }
}

// ── Palette ─────────────────────────────────────────────────────────────────
val Background = Color(0xFF000000)
val Surface = Color(0xFF000000)
val SurfaceVar = Color(0xFF0C0C0C)
val DarkSurface = Color(0xFF080808)
val OnBackground = Color(0xFFFFFFFF)
val OnSurface = Color(0xFFFFFFFF)
val Muted = Color(0xFF999999)
val Border = Color(0xFF1F1F1F)
val Accent = Color(0xFFFFFFFF)
val Error = Color(0xFFBF80FF)
val KuudereRed = Color(0xFFBF80FF)

private fun colorSchemeFor(themeId: AppThemeId) = when (themeId) {
    AppThemeId.Default -> darkColorScheme(
        primary = Accent,
        onPrimary = Color.Black,
        secondary = Muted,
        onSecondary = Color.White,
        tertiary = SurfaceVar,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVar,
        onSurfaceVariant = Muted,
        error = Error,
        outline = Border,
    )

    AppThemeId.Amoled -> darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        secondary = Color(0xFFBDBDBD),
        onSecondary = Color.Black,
        tertiary = Color(0xFF050505),
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF050505),
        onSurfaceVariant = Color(0xFFBDBDBD),
        error = Color(0xFFFF8A80),
        outline = Color(0xFF2A2A2A),
    )

    AppThemeId.Purple -> darkColorScheme(
        primary = Color(0xFFBF80FF),
        onPrimary = Color.Black,
        secondary = Color(0xFFD8B4FE),
        onSecondary = Color.Black,
        tertiary = Color(0xFF211331),
        background = Color(0xFF08050C),
        onBackground = Color.White,
        surface = Color(0xFF0D0714),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1A1025),
        onSurfaceVariant = Color(0xFFC7B7D9),
        error = Color(0xFFFF8A80),
        outline = Color(0xFF352147),
    )

    AppThemeId.Red -> darkColorScheme(
        primary = Color(0xFFE50914),
        onPrimary = Color.White,
        secondary = Color(0xFFFF8A80),
        onSecondary = Color.Black,
        tertiary = Color(0xFF270406),
        background = Color(0xFF090000),
        onBackground = Color.White,
        surface = Color(0xFF110102),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF250507),
        onSurfaceVariant = Color(0xFFFFC1C1),
        error = Color(0xFFFFB4AB),
        outline = Color(0xFF451013),
    )

    AppThemeId.HighContrast -> darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        secondary = Color(0xFFFFFF00),
        onSecondary = Color.Black,
        tertiary = Color(0xFF1E1E1E),
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF050505),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color.White,
        error = Color(0xFFFF5252),
        outline = Color(0xFF777777),
    )
}

@Composable
fun AnisugTheme(
    themeId: AppThemeId = AppThemeId.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorSchemeFor(themeId),
        typography = AnisugTypography(),
        content = content,
    )
}
