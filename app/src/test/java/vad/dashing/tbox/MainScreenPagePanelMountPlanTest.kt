package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenPagePanelMountPlanTest {

    @Test
    fun shouldUseStagedMount_fromThreshold() {
        assertFalse(MainScreenPagePanelMountPlan.shouldUseStagedMount(2))
        assertTrue(MainScreenPagePanelMountPlan.shouldUseStagedMount(3))
        assertTrue(MainScreenPagePanelMountPlan.shouldUseStagedMount(10))
    }

    @Test
    fun visiblePrefixCount_belowThreshold_showsAll() {
        assertEquals(2, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 2, mountedCount = 0))
        assertEquals(2, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 2, mountedCount = 1))
    }

    @Test
    fun visiblePrefixCount_staged_growsWithMountedCount() {
        assertEquals(0, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 0))
        assertEquals(1, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 1))
        assertEquals(5, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 5))
        assertEquals(5, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 99))
    }
}
