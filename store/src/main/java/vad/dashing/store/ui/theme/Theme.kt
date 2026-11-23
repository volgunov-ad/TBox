package vad.dashing.store.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Calm blue accent. Layers: background → surface (cards) → surfaceContainer* (pills, panels).
// Every container below has a matching on* colour; UI code never hardcodes black/white text.
// Window background / accent are mirrored in res/values{,-night}/colors.xml.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF4F5B6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4F4),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF137333),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCEEAD6),
    onTertiaryContainer = Color(0xFF0D3B1E),
    background = Color(0xFFEEF1F6),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E5EC),
    onSurfaceVariant = Color(0xFF4A5059),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFF1F3F7),
    surfaceContainerHigh = Color(0xFFE8EBF1),
    surfaceContainerHighest = Color(0xFFDFE3EA),
    outline = Color(0xFF747A84),
    outlineVariant = Color(0xFFC9CED6),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF0B4A9E),
    onPrimaryContainer = Color(0xFFD9E6FD),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF363F4E),
    onSecondaryContainer = Color(0xFFDCE4F4),
    tertiary = Color(0xFF81C995),
    onTertiary = Color(0xFF00391A),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFCEEAD6),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE3E5EA),
    surface = Color(0xFF1A1D22),
    onSurface = Color(0xFFE3E5EA),
    surfaceVariant = Color(0xFF2C3037),
    onSurfaceVariant = Color(0xFFB8BEC8),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF16191D),
    surfaceContainer = Color(0xFF20242A),
    surfaceContainerHigh = Color(0xFF2A2E35),
    surfaceContainerHighest = Color(0xFF353A42),
    outline = Color(0xFF8B919B),
    outlineVariant = Color(0xFF3E434B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

// Head-unit reading distance: body 20sp, controls 20sp, titles 22–30sp.
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
)

val Typography.tboxBody: TextStyle get() = bodyLarge
val Typography.tboxButton: TextStyle get() = labelLarge
val Typography.tboxTitle: TextStyle get() = titleLarge
val Typography.tboxHeadline: TextStyle get() = headlineSmall

/** Shared control sizes so buttons, chips and icon buttons line up across screens. */
object StoreDimens {
    val ButtonHeight = 56.dp
    val CompactButtonHeight = 48.dp
    val ChipHeight = 48.dp
    val IconButton = 56.dp
    val ScreenPadding = 20.dp
    val CardCorner = 20.dp
    val ListIcon = 72.dp
    val DetailIcon = 112.dp
    val ContentMaxWidth = 1100.dp
}

@Composable
fun TboxStoreTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
