package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MainScreenPanelInterestIdsTest {

    @Test
    fun mbCanAndMedia_idsDifferByWindowMode() {
        val panelId = "main-screen-abc12345"
        val fullMb = MainScreenPanelInterestIds.mbCanInterestSourceId(panelId, windowMode = false)
        val winMb = MainScreenPanelInterestIds.mbCanInterestSourceId(panelId, windowMode = true)
        val fullMedia = MainScreenPanelInterestIds.mediaSourceId(panelId, windowMode = false)
        val winMedia = MainScreenPanelInterestIds.mediaSourceId(panelId, windowMode = true)

        assertEquals("main-screen-$panelId", fullMb)
        assertEquals("main-screen-window-$panelId", winMb)
        assertEquals("main-screen-dashboard-$panelId", fullMedia)
        assertEquals("main-screen-window-dashboard-$panelId", winMedia)

        assertNotEquals(fullMb, winMb)
        assertNotEquals(fullMedia, winMedia)
    }

    @Test
    fun windowMode_idsDoNotCollideWithFullscreenPrefixes() {
        val panelId = "p1"
        val winMb = MainScreenPanelInterestIds.mbCanInterestSourceId(panelId, windowMode = true)
        val winMedia = MainScreenPanelInterestIds.mediaSourceId(panelId, windowMode = true)
        // Clearing fullscreen sources must not match window-mode keys.
        assertNotEquals("main-screen-$panelId", winMb)
        assertNotEquals("main-screen-dashboard-$panelId", winMedia)
    }
}
