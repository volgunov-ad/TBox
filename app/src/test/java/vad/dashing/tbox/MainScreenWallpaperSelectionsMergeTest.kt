package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainScreenWallpaperSelectionsMergeTest {

    @Test
    fun applyPendingWallpaperPatches_returnsBaseWhenNoPatches() {
        val base = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "nor.jpg")

        val merged = applyPendingWallpaperPatches(base, emptyMap())

        assertEquals("nor.jpg", merged.fileNameFor(1, forLightTheme = true))
    }

    @Test
    fun applyPendingWallpaperPatches_overlaysPendingPageSelection() {
        val base = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "nor.jpg")
        val patches = mapOf((1 to true) to "spt.jpg")

        val merged = applyPendingWallpaperPatches(base, patches)

        assertEquals("spt.jpg", merged.fileNameFor(1, forLightTheme = true))
        assertNull(merged.fileNameFor(2, forLightTheme = true))
    }
}
