package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeOpenIntentParserTest {

    @Test
    fun isTboxThemeFileName_acceptsExtension() {
        assertTrue(ThemeOpenIntentParser.isTboxThemeFileName("my_theme.tboxtheme"))
        assertTrue(ThemeOpenIntentParser.isTboxThemeFileName("MY_THEME.TBOXTHEME"))
    }

    @Test
    fun isTboxThemeFileName_rejectsOtherExtensions() {
        assertFalse(ThemeOpenIntentParser.isTboxThemeFileName("theme.zip"))
        assertFalse(ThemeOpenIntentParser.isTboxThemeFileName("theme.json"))
        assertFalse(ThemeOpenIntentParser.isTboxThemeFileName(""))
    }

    @Test
    fun isTboxThemeUri_checksDisplayName() {
        assertTrue(
            ThemeOpenIntentParser.isTboxThemeUri("content://com.example/files/eco.tboxtheme"),
        )
        assertFalse(
            ThemeOpenIntentParser.isTboxThemeUri("content://com.example/files/eco.zip"),
        )
    }
}
