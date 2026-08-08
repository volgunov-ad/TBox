package vad.dashing.tbox.mbcan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VhalPermissionDenialTrackerTest {

    @Test
    fun markDenied_isPerProperty() {
        val tracker = VhalPermissionDenialTracker()
        tracker.markDenied(100)
        assertTrue(tracker.isDenied(100))
        assertFalse(tracker.isDenied(200))
    }

    @Test
    fun areAllDenied_requiresEveryId() {
        val tracker = VhalPermissionDenialTracker()
        tracker.markDenied(1)
        assertFalse(tracker.areAllDenied(setOf(1, 2)))
        tracker.markDenied(2)
        assertTrue(tracker.areAllDenied(setOf(1, 2)))
        assertFalse(tracker.areAllDenied(emptySet()))
    }

    @Test
    fun clear_resetsDenials() {
        val tracker = VhalPermissionDenialTracker()
        tracker.markDenied(7)
        tracker.clear()
        assertFalse(tracker.isDenied(7))
    }
}
