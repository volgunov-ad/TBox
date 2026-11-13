package vad.dashing.camd

import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.camd.ui.theme.CamdDarkColors
import vad.dashing.camd.ui.theme.CamdLightColors
import vad.dashing.camd.ui.theme.CamdTypography
import vad.dashing.camd.ui.theme.camdColorScheme
import vad.dashing.camd.ui.theme.camdDarkFromUiMode

class CamdThemeTest {
    @Test
    fun darkFromUiMode_readsNightMask() {
        assertFalse(camdDarkFromUiMode(Configuration.UI_MODE_NIGHT_NO))
        assertFalse(camdDarkFromUiMode(Configuration.UI_MODE_NIGHT_UNDEFINED))
        assertTrue(camdDarkFromUiMode(Configuration.UI_MODE_NIGHT_YES))
        assertTrue(
            camdDarkFromUiMode(
                Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES,
            ),
        )
        assertFalse(
            camdDarkFromUiMode(
                Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO,
            ),
        )
    }

    @Test
    fun colorScheme_matchesDayNightPalettes() {
        assertSame(CamdLightColors, camdColorScheme(dark = false))
        assertSame(CamdDarkColors, camdColorScheme(dark = true))
        assertEquals(0xFFF8F9FA.toInt(), CamdLightColors.background.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), CamdLightColors.surface.toArgb())
        assertEquals(0xFF1A1C1E.toInt(), CamdLightColors.onSurface.toArgb())
        assertEquals(0xFF211F1F.toInt(), CamdLightColors.onSurfaceVariant.toArgb())
        assertEquals(0xFF292F3B.toInt(), CamdDarkColors.background.toArgb())
        assertEquals(0xFF131C2D.toInt(), CamdDarkColors.surface.toArgb())
        assertEquals(0xFFE2E2E6.toInt(), CamdDarkColors.onSurface.toArgb())
        assertEquals(0xFFC2C7CF.toInt(), CamdDarkColors.onSurfaceVariant.toArgb())
        assertEquals(0xFF0066CC.toInt(), CamdLightColors.primary.toArgb())
        assertEquals(0xFF0066CC.toInt(), CamdDarkColors.primary.toArgb())
    }

    @Test
    fun typography_matchesMonitorSettingsSizes() {
        assertEquals(26f, CamdTypography.titleLarge.fontSize.value)
        assertEquals(24f, CamdTypography.bodyLarge.fontSize.value)
        assertEquals(24f, CamdTypography.labelLarge.fontSize.value)
        assertEquals(28f, CamdTypography.headlineSmall.fontSize.value)
    }
}
