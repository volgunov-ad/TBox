package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainScreenWallpaperSupportTest {

    @Test
    fun effectiveWallpaperFileName_prefersSavedWhenPresent() {
        val names = listOf("a.png", "b.png", "c.png")
        assertEquals("b.png", effectiveWallpaperFileName(names, "b.png"))
    }

    @Test
    fun effectiveWallpaperFileName_fallsBackToFirstWhenMissing() {
        val names = listOf("a.png", "b.png")
        assertEquals("a.png", effectiveWallpaperFileName(names, "gone.png"))
    }

    @Test
    fun effectiveWallpaperFileName_emptyList() {
        assertNull(effectiveWallpaperFileName(emptyList(), "x.png"))
    }
}
