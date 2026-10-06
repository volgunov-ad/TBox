package vad.dashing.tbox.phone

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import vad.dashing.tbox.ui.theme.darkColorScheme
import vad.dashing.tbox.ui.theme.lightColorScheme

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

/** Selected-button colors of the web panel (`climate_panel.html`), so both look the same. */
enum class Accent(val container: Color?, val content: Color) {
    PRIMARY(null, Color.White),
    HEAT(Color(0xFFE85D4C), Color.White),
    VENT(Color(0xFF3DBEFF), Color(0xFF082028)),
    ECO(Color(0xFF00A400), Color.White),
    COMFORT(Color(0xFF00C8FF), Color(0xFF082028)),
    STRONG(Color(0xFFF3A721), Color(0xFF1A1200)),
}

object PhoneColors {
    /** Secondary text: web `--muted`. */
    val muted: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline

    /** Selected tab and "open" states: web `--accent-text`. */
    val accentText: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondary

    /** Selected tab pill: web `--accent-soft`. */
    val accentSoft: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.28f else 0.14f)
}
