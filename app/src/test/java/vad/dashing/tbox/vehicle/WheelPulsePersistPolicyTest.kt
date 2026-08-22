package vad.dashing.tbox.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelPulsePersistPolicyTest {

    @Test
    fun delayUntilNextWrite_zeroWhenIntervalElapsed() {
        assertEquals(0L, WheelPulsePersistPolicy.delayUntilNextWriteMs(70_000L, 10_000L))
        assertEquals(0L, WheelPulsePersistPolicy.delayUntilNextWriteMs(70_000L, 10_000L))
    }

    @Test
    fun delayUntilNextWrite_waitsRemaining() {
        assertEquals(
            50_000L,
            WheelPulsePersistPolicy.delayUntilNextWriteMs(20_000L, 10_000L),
        )
        assertEquals(
            30_000L,
            WheelPulsePersistPolicy.delayUntilNextWriteMs(40_000L, 10_000L),
        )
        assertEquals(60_000L, WheelPulsePersistPolicy.MIN_INTERVAL_MS)
    }

    @Test
    fun nearlyEqual_respectsEpsAndFlags() {
        val a = WheelPulseCalibration(0.025f, 0.80f, tripsEnabled = true, mockDrEnabled = false)
        val b = WheelPulseCalibration(0.025f, 0.805f, tripsEnabled = true, mockDrEnabled = false)
        assertTrue(WheelPulsePersistPolicy.nearlyEqual(a, b))
        assertFalse(
            WheelPulsePersistPolicy.nearlyEqual(
                a,
                a.copy(tripsEnabled = false),
            ),
        )
    }

    @Test
    fun isDirty_whenCandidateDiffersFromPersisted() {
        val persisted = WheelPulseCalibration(0.025f, 0.80f)
        val candidate = WheelPulseCalibration(0.025f, 0.70f)
        assertTrue(WheelPulsePersistPolicy.isDirty(persisted, null, candidate))
        assertFalse(WheelPulsePersistPolicy.isDirty(persisted, null, persisted))
        assertFalse(
            WheelPulsePersistPolicy.isDirty(persisted, candidate, candidate),
        )
    }
}
