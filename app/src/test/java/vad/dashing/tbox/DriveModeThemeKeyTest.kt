package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class DriveModeThemeKeyTest {

    @Test
    fun resolveDriveModeThemeKey_prefersStandardDriveMode() {
        val key = DriveModeThemeWatcher.resolveDriveModeThemeKey(drive = 2, wet6dct = 1)
        assertEquals(2, key)
    }

    @Test
    fun resolveDriveModeThemeKey_fallsBackTo6dct() {
        val key = DriveModeThemeWatcher.resolveDriveModeThemeKey(drive = null, wet6dct = 0)
        assertEquals(100, key)
    }

    @Test
    fun resolveDriveModeThemeKey_returnsNullWhenUnknown() {
        assertNull(DriveModeThemeWatcher.resolveDriveModeThemeKey(drive = 99, wet6dct = 99))
    }

    @Test
    fun panelVisibility_ignoresPageAboveCount() {
        val panel = MainScreenPanelConfig(
            id = "p",
            name = "P",
            enabled = true,
            widgetsConfig = emptyList(),
            rows = 1,
            cols = 1,
            relX = 0f,
            relY = 0f,
            relWidth = 0.2f,
            relHeight = 0.2f,
            background = false,
            clickAction = false,
            pageNumber = 4,
        )
        assertEquals(false, panel.isVisibleOnMainScreenPage(pageCount = 2, currentPage = 1))
    }
}
