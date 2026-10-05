package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenPagePanelMountPlanTest {

    @Test
    fun shouldUseStagedMount_fromThreshold() {
        assertFalse(MainScreenPagePanelMountPlan.shouldUseStagedMount(4))
        assertTrue(MainScreenPagePanelMountPlan.shouldUseStagedMount(5))
        assertTrue(MainScreenPagePanelMountPlan.shouldUseStagedMount(10))
    }

    @Test
    fun visiblePrefixCount_belowThreshold_showsAll() {
        assertEquals(4, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 4, mountedCount = 0))
        assertEquals(4, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 4, mountedCount = 1))
    }

    @Test
    fun visiblePrefixCount_staged_growsWithMountedCount() {
        assertEquals(0, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 0))
        assertEquals(1, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 1))
        assertEquals(5, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 5))
        assertEquals(5, MainScreenPagePanelMountPlan.visiblePrefixCount(panelCount = 5, mountedCount = 99))
    }

    @Test
    fun stagedMountStepSize_a9Pair_a10Single() {
        assertEquals(
            2,
            MainScreenPagePanelMountPlan.stagedMountStepSize(HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(
            1,
            MainScreenPagePanelMountPlan.stagedMountStepSize(HeadUnitCanMode.Android10Vhal),
        )
    }

    @Test
    fun nextMountedCount_a9StepsByTwoUntilRemainder() {
        val step = MainScreenPagePanelMountPlan.stagedMountStepSize(HeadUnitCanMode.Android9MbCan)
        assertEquals(2, MainScreenPagePanelMountPlan.nextMountedCount(0, 5, step))
        assertEquals(4, MainScreenPagePanelMountPlan.nextMountedCount(2, 5, step))
        assertEquals(5, MainScreenPagePanelMountPlan.nextMountedCount(4, 5, step))
    }

    @Test
    fun nextMountedCount_a10StepsByOne() {
        val step = MainScreenPagePanelMountPlan.stagedMountStepSize(HeadUnitCanMode.Android10Vhal)
        assertEquals(1, MainScreenPagePanelMountPlan.nextMountedCount(0, 5, step))
        assertEquals(2, MainScreenPagePanelMountPlan.nextMountedCount(1, 5, step))
        assertEquals(5, MainScreenPagePanelMountPlan.nextMountedCount(4, 5, step))
    }
}
