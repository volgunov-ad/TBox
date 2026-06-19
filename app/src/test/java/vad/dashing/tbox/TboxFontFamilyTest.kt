package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(TboxFontFamily.Roboto, TboxFontFamily.fromId(4))
        assertEquals(TboxFontFamily.Nunito, TboxFontFamily.fromId(9))
        assertEquals(TboxFontFamily.Default, TboxFontFamily.fromId(99))
    }

    @Test
    fun fromSlug_roundTripsSlug() {
        assertEquals(TboxFontFamily.Monospace, TboxFontFamily.fromSlug("monospace"))
        assertEquals(TboxFontFamily.Montserrat, TboxFontFamily.fromSlug("montserrat"))
        assertEquals(TboxFontFamily.CrimsonText, TboxFontFamily.fromSlug("crimson_text"))
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

    @Test
    fun bundledRoboto_differsFromDefault() {
        val roboto = TboxFontFamily.Roboto.toComposeFontFamily()
        assertNotEquals(FontFamily.Default, roboto)
        assertEquals(roboto, resolveFontFamily(TboxFontFamily.Roboto.id))
    }
}
