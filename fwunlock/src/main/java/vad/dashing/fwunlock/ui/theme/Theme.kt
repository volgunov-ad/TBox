package vad.dashing.fwunlock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Primary = Color(0xFF0066CC)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF72777F),
)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    background = Color(0xFF292F3B),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF131C1D),
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 38.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 28.sp),
)

@Composable
fun FwUnlockTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
