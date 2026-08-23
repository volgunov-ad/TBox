package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingOverlayOpenPlanTest {

    private fun cfg(id: String, enabled: Boolean = true) = FloatingDashboardConfig(
        id = id,
        name = id,
        enabled = enabled,
        widgetsConfig = emptyList(),
        rows = 1,
        cols = 1,
        width = 100,
        height = 100,
        startX = 0,
        startY = 0,
        background = false,
        clickAction = false,
    )

    @Test
    fun shouldUseStagedOpen_fromThreshold() {
        assertFalse(FloatingOverlayOpenPlan.shouldUseStagedOpen(4))
        assertTrue(FloatingOverlayOpenPlan.shouldUseStagedOpen(5))
        assertTrue(FloatingOverlayOpenPlan.shouldUseStagedOpen(22))
    }

    @Test
    fun pendingOpensInConfigOrder_preservesOrderAndSkipsMounted() {
        val visible = listOf(
            cfg("a"),
            cfg("b"),
            cfg("c"),
        )
        val pending = FloatingOverlayOpenPlan.pendingOpensInConfigOrder(
            visibleConfigs = visible,
            alreadyMountedIds = setOf("b"),
            shouldOpen = { it.enabled },
        )
        assertEquals(listOf("a", "c"), pending.map { it.id })
    }

    @Test
    fun pendingOpensInConfigOrder_respectsShouldOpenPredicate() {
        val visible = listOf(cfg("a"), cfg("b", enabled = false), cfg("c"))
        val pending = FloatingOverlayOpenPlan.pendingOpensInConfigOrder(
            visibleConfigs = visible,
            alreadyMountedIds = emptySet(),
            shouldOpen = { it.enabled },
        )
        assertEquals(listOf("a", "c"), pending.map { it.id })
    }
}
