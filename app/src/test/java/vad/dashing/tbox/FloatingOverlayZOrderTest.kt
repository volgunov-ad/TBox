package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingOverlayZOrderTest {

    private fun r(x: Int, y: Int, w: Int, h: Int) = PanelPxBounds(x, y, w, h)

    @Test
    fun rectsIntersect_overlapAndTouch() {
        assertTrue(FloatingOverlayZOrder.rectsIntersect(r(0, 0, 100, 100), r(50, 50, 100, 100)))
        assertFalse(FloatingOverlayZOrder.rectsIntersect(r(0, 0, 100, 100), r(100, 0, 50, 50)))
        assertFalse(FloatingOverlayZOrder.rectsIntersect(r(0, 0, 100, 100), r(200, 200, 10, 10)))
    }

    @Test
    fun overlappingComponents_singletonsOmitted() {
        val order = listOf("a", "b", "c")
        val bounds = mapOf(
            "a" to r(0, 0, 50, 50),
            "b" to r(100, 0, 50, 50),
            "c" to r(200, 0, 50, 50),
        )
        assertTrue(
            FloatingOverlayZOrder.overlappingComponentsInConfigOrder(order, bounds).isEmpty(),
        )
    }

    @Test
    fun overlappingComponents_oneClusterPreservesConfigOrder() {
        val order = listOf("a", "b", "c")
        val bounds = mapOf(
            "a" to r(0, 0, 100, 100),
            "b" to r(50, 50, 100, 100),
            "c" to r(300, 0, 50, 50),
        )
        val components = FloatingOverlayZOrder.overlappingComponentsInConfigOrder(order, bounds)
        assertEquals(listOf(listOf("a", "b")), components)
    }

    @Test
    fun overlappingComponents_transitiveCluster() {
        // a overlaps b, b overlaps c, a does not overlap c → one component
        val order = listOf("c", "a", "b")
        val bounds = mapOf(
            "a" to r(0, 0, 60, 60),
            "b" to r(50, 0, 60, 60),
            "c" to r(100, 0, 60, 60),
        )
        val components = FloatingOverlayZOrder.overlappingComponentsInConfigOrder(order, bounds)
        assertEquals(1, components.size)
        assertEquals(listOf("c", "a", "b"), components.single())
    }

    @Test
    fun componentNeedsRemount_whenOrderDiffers() {
        assertFalse(
            FloatingOverlayZOrder.componentNeedsRemount(listOf("a", "b"), listOf("a", "b")),
        )
        assertTrue(
            FloatingOverlayZOrder.componentNeedsRemount(listOf("a", "b"), listOf("b", "a")),
        )
        assertFalse(FloatingOverlayZOrder.componentNeedsRemount(listOf("a"), listOf("a")))
    }
}
