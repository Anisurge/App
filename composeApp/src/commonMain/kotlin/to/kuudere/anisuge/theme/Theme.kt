package to.kuudere.anisuge.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

enum class AppThemeId(val id: String, val label: String, val description: String) {
    Light("light", "Light", "Clean light theme"),
    Default("default", "Anisurge", "Default Anisurge dark theme"),
    Amoled("amoled", "AMOLED", "Pure black with soft gray surfaces"),
    Purple("purple", "Purple", "Kuudere purple accent"),
    Red("red", "Cinema Red", "Streaming-style red accent"),
    Ocean("ocean", "Ocean", "Deep blue with cyan accent"),
    Midnight("midnight", "Midnight", "Indigo night with soft violet accent"),
    HighContrast("high_contrast", "High Contrast", "Brighter borders and controls"),
    Dantotsu("dantotsu", "Dantotsu", "Dantotsu reference style with violet accent"),
    ReDantotsu("redantotsu", "ReDantotsu", "ReDantotsu reference style with liquid glass and blue accent");

    companion object {
        fun fromId(id: String?): AppThemeId = entries.firstOrNull { it.id == id } ?: Default
    }
}

// ── Legacy palette aliases ────────────────────────────────────────────────────
// These used to be fixed colours. They are now theme-aware getters that resolve from the
// active [AppColors] so the few screens importing them follow the selected theme.
val Background: Color get() = AppColors.background
val Surface: Color get() = AppColors.surface
val SurfaceVar: Color get() = AppColors.surfaceVariant
val DarkSurface: Color get() = AppColors.surface
val OnBackground: Color get() = AppColors.text
val OnSurface: Color get() = AppColors.text
val Muted: Color get() = AppColors.textMuted
val Border: Color get() = AppColors.border
val Accent: Color get() = AppColors.accent
val Error: Color get() = AppColors.error

/** Brand purple — kept fixed for places that intentionally want the Kuudere purple. */
val KuudereRed = Color(0xFFBF80FF)

@Composable
fun AnisugTheme(
    themeId: AppThemeId = AppThemeId.Default,
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { paletteFor(themeId) }
    // Push the palette into the global holder so legacy screens recompose on theme change.
    applyAppPalette(palette)
    val colorScheme = remember(themeId) { palette.toColorScheme() }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnisugTypography(),
        content = content,
    )
}
