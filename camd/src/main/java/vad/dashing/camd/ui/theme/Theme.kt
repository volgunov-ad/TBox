package vad.dashing.camd.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Day/night for camd mirrors companion apps (`:mqtt`, `:dataresize`, …):
 * system [Configuration.UI_MODE_NIGHT_MASK] / Compose [androidx.compose.foundation.isSystemInDarkTheme].
 * On the HU that tracks the same night flag Monitor follows when “follow system” is on.
 * Palette and type sizes match Monitor settings cards ([vad.dashing.tbox.ui.theme]).
 */

private val LightBackground = Color(0xFFF8F9FA)
private val LightSurface = Color(0xFFFFFFFF)
private val DarkBackground = Color(0xFF292F3B)
private val DarkSurface = Color(0xFF131C2D)
private val Primary = Color(0xFF0066CC)

internal val CamdLightColors: ColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Primary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E3FF),
    onSecondaryContainer = Color(0xFF001C3A),
    background = LightBackground,
    onBackground = Color(0xFF1A1C1E),
    surface = LightSurface,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF211F1F),
    error = Color(0xFFBA1A1A),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
)

internal val CamdDarkColors: ColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF004586),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFA8C7FF),
    onSecondary = Color(0xFF002F5F),
    secondaryContainer = Color(0xFF004586),
    onSecondaryContainer = Color(0xFFD6E3FF),
    background = DarkBackground,
    onBackground = Color(0xFFE2E2E6),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
)

internal val CamdTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 44.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 34.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 31.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 31.sp),
)

/** True when [uiMode] has night bit set — same mask Compose [isSystemInDarkTheme] uses. */
fun camdDarkFromUiMode(uiMode: Int): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

fun camdColorScheme(dark: Boolean): ColorScheme =
    if (dark) CamdDarkColors else CamdLightColors

@Composable
fun CamdTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = camdColorScheme(dark),
        typography = CamdTypography,
        content = content,
    )
}
