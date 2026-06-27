package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeExportNamesTest {

    @Test
    fun sanitizeThemeExportBaseName_stripsExtension() {
        assertEquals("my_theme", ThemeBundleExport.sanitizeThemeExportBaseName("my_theme.tboxtheme"))
    }

    @Test
    fun sanitizeThemeExportBaseName_rejectsBlank() {
        assertNull(ThemeBundleExport.sanitizeThemeExportBaseName("   "))
        assertNull(ThemeBundleExport.sanitizeThemeExportBaseName("."))
    }

    @Test
    fun sanitizeThemeExportBaseName_replacesInvalidChars() {
        assertEquals("a_b", ThemeBundleExport.sanitizeThemeExportBaseName("a/b"))
    }

    @Test
    fun themeFileNameFromBaseName_addsExtension() {
        assertEquals("night.tboxtheme", ThemeBundleExport.themeFileNameFromBaseName("night"))
    }
}
