package vad.dashing.tbox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapKitCameraGateTest {
    @Test
    fun firstApplyAlwaysPasses() {
        val gate = MapKitCameraGate()
        assertTrue(gate.shouldApply(55.0, 37.0, 16f, 0f, nowMs = 1_000L))
    }

    @Test
    fun tinyMoveWithinIntervalIsSkipped() {
        val gate = MapKitCameraGate()
        assertTrue(gate.shouldApply(55.0, 37.0, 16f, 0f, nowMs = 1_000L))
        // ~1 m east — below MIN_MOVE_M and within MIN_INTERVAL_MS
        assertFalse(gate.shouldApply(55.0, 37.0 + 1e-5, 16f, 0f, nowMs = 1_050L))
    }

    @Test
    fun materialMoveAfterIntervalApplies() {
        val gate = MapKitCameraGate()
        assertTrue(gate.shouldApply(55.0, 37.0, 16f, 0f, nowMs = 1_000L))
        // ~15 m north after 300 ms
        assertTrue(gate.shouldApply(55.0 + 1.4e-4, 37.0, 16f, 0f, nowMs = 1_300L))
    }

    @Test
    fun largeJumpBypassesInterval() {
        val gate = MapKitCameraGate()
        assertTrue(gate.shouldApply(55.0, 37.0, 16f, 0f, nowMs = 1_000L))
        // ~100 m jump immediately
        assertTrue(gate.shouldApply(55.0 + 9e-4, 37.0, 16f, 0f, nowMs = 1_020L))
    }

    @Test
    fun azimuthNoiseIsFiltered() {
        val gate = MapKitCameraGate()
        assertTrue(gate.shouldApply(55.0, 37.0, 16f, 10f, nowMs = 1_000L))
        assertFalse(gate.shouldApply(55.0, 37.0, 16f, 11f, nowMs = 1_400L))
        assertTrue(gate.shouldApply(55.0, 37.0, 16f, 14f, nowMs = 1_400L))
    }
}
