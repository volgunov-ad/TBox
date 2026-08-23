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

    @Test
    fun stagedOpenStepSize_a9Pair_a10Single() {
        assertEquals(2, FloatingOverlayOpenPlan.stagedOpenStepSize(HeadUnitCanMode.Android9MbCan))
        assertEquals(1, FloatingOverlayOpenPlan.stagedOpenStepSize(HeadUnitCanMode.Android10Vhal))
    }

    @Test
    fun pendingOpenBatches_a9PairsThenRemainder() {
        val pending = listOf(cfg("a"), cfg("b"), cfg("c"), cfg("d"), cfg("e"))
        val batches = FloatingOverlayOpenPlan.pendingOpenBatches(
            pending,
            FloatingOverlayOpenPlan.stagedOpenStepSize(HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")), batches.map { b -> b.map { it.id } })
    }

    @Test
    fun pendingOpenBatches_a10Singles() {
        val pending = listOf(cfg("a"), cfg("b"), cfg("c"))
        val batches = FloatingOverlayOpenPlan.pendingOpenBatches(
            pending,
            FloatingOverlayOpenPlan.stagedOpenStepSize(HeadUnitCanMode.Android10Vhal),
        )
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), batches.map { b -> b.map { it.id } })
    }
}
