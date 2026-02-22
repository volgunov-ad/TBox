package vad.dashing.tbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import vad.dashing.tbox.DARK_THEME_ON_SURFACE_COLOR_INT
import vad.dashing.tbox.LIGHT_THEME_ON_SURFACE_COLOR_INT

@Composable
fun TboxAppTheme(
    theme: Int = 1, // 1 - светлая, 2 - темная
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        2 -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// Светлая цветовая схема
fun lightColorScheme() = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF0066CC),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E3FF),
    onSecondaryContainer = Color(0xFF001C3A),
    tertiary = Color(0xFF0066CC),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E3FF),
    onTertiaryContainer = Color(0xFF001C3A),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(LIGHT_THEME_ON_SURFACE_COLOR_INT),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF211F1F),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF)
)

// Темная цветовая схема
fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF004586),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFA8C7FF),
    onSecondary = Color(0xFF002F5F),
    secondaryContainer = Color(0xFF004586),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFFA8C7FF),
    onTertiary = Color(0xFF002F5F),
    tertiaryContainer = Color(0xFF004586),
    onTertiaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF292F3B),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF131C2D),
    onSurface = Color(DARK_THEME_ON_SURFACE_COLOR_INT),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E)
)