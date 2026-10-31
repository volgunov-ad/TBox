package vad.dashing.mqtt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightBackground = Color(0xFFF8F9FA)
private val LightSurface = Color(0xFFFFFFFF)
private val DarkBackground = Color(0xFF292F3B)
private val DarkSurface = Color(0xFF131C2D)
private val Primary = Color(0xFF0066CC)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1A1C1E),
    surface = LightSurface,
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF211F1F),
    error = Color(0xFFBA1A1A),
    outline = Color(0xFF72777F),
)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    background = DarkBackground,
    onBackground = Color(0xFFE2E2E6),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFC2C7CF),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF8C9199),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 44.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 34.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 31.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 31.sp),
)

val Typography.tboxBody: TextStyle get() = bodyLarge
val Typography.tboxButton: TextStyle get() = labelLarge
val Typography.tboxTitle: TextStyle get() = titleLarge
val Typography.tboxHeadline: TextStyle get() = headlineSmall
val Typography.tboxTabLabel: TextStyle get() = displaySmall

@Composable
fun TboxMqttTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
