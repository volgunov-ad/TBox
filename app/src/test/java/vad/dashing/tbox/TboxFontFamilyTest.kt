package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.ui.theme.TboxFontFamily
import vad.dashing.tbox.ui.theme.resolveFontFamily
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxMaterialTypography
import vad.dashing.tbox.ui.theme.tboxTitle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

class TboxFontFamilyTest {

    @Test
    fun fromId_returnsMatchingPresetOrDefault() {
        assertEquals(TboxFontFamily.Serif, TboxFontFamily.fromId(2))
        assertEquals(TboxFontFamily.Default, TboxFontFamily.fromId(99))
    }

    @Test
    fun fromSlug_roundTripsSlug() {
        assertEquals(TboxFontFamily.Monospace, TboxFontFamily.fromSlug("monospace"))
        assertNull(TboxFontFamily.fromSlug("unknown"))
        assertNull(TboxFontFamily.fromSlug(""))
    }

    @Test
    fun typographyUsesSelectedFontFamilyAndNormalWeight() {
        val typography = tboxMaterialTypography(FontFamily.Serif)
        assertEquals(FontFamily.Serif, typography.tboxTitle.fontFamily)
        assertEquals(FontWeight.Normal, typography.tboxTitle.fontWeight)
        assertEquals(FontFamily.Serif, typography.tboxCaption.fontFamily)
    }

    @Test
    fun resolveFontFamily_mapsStoredId() {
        assertEquals(FontFamily.Monospace, resolveFontFamily(TboxFontFamily.Monospace.id))
    }
}
