package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues

class MockPowerStateTest {

    @Test
    fun fromStorageMigratesLegacyBoolean() {
        assertEquals(MockPowerState.OFF, MockPowerState.fromStorage(null, false))
        assertEquals(MockPowerState.ALWAYS_ON, MockPowerState.fromStorage(null, true))
        assertEquals(MockPowerState.WHEN_NO_FIX, MockPowerState.fromStorage("WHEN_NO_FIX", false))
        assertEquals(MockPowerState.ALWAYS_ON, MockPowerState.fromStorage("ALWAYS_ON", false))
        assertEquals(MockPowerState.OFF, MockPowerState.fromStorage("OFF", true))
    }

    @Test
    fun whenNoFixForcesConstantEffectiveMode() {
        assertEquals(
            MockCanSpeedMode.CONSTANT,
            MockPowerState.WHEN_NO_FIX.effectiveCanSpeedMode(MockCanSpeedMode.NONE),
        )
        assertEquals(
            MockCanSpeedMode.ALWAYS,
            MockPowerState.ALWAYS_ON.effectiveCanSpeedMode(MockCanSpeedMode.ALWAYS),
        )
    }

    @Test
    fun widgetCycleSkipsWhenNoFix() {
        assertEquals(
            MockLocationWidgetCycle.INDEX_OFF,
            MockLocationWidgetCycle.indexOf(MockPowerState.OFF, MockCanSpeedMode.CONSTANT),
        )
        assertNull(
            MockLocationWidgetCycle.indexOf(MockPowerState.WHEN_NO_FIX, MockCanSpeedMode.NONE),
        )
        assertEquals(
            MockLocationWidgetCycle.INDEX_ADVANCED,
            MockLocationWidgetCycle.indexOf(
                MockPowerState.ALWAYS_ON,
                MockCanSpeedMode.CONSTANT,
            ),
        )
        val fromNf = MockLocationWidgetCycle.next(
            MockPowerState.WHEN_NO_FIX,
            MockCanSpeedMode.NONE,
        )
        assertEquals(MockPowerState.OFF, fromNf.power)
        val chain = generateSequence(
            MockLocationWidgetCycle.Selection(MockPowerState.OFF, MockCanSpeedMode.NONE),
        ) { MockLocationWidgetCycle.next(it.power, it.mode) }
            .take(6)
            .map { MockLocationWidgetCycle.indexOf(it.power, it.mode) }
            .toList()
        assertEquals(listOf(0, 1, 2, 3, 4, 0), chain)
    }

    @Test
    fun hasGnssFixGate() {
        val live = LocValues(
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
        )
        assertTrue(MockLocationJob.hasGnssFixForPowerGate(live, gnssFresh = true))
        assertFalse(MockLocationJob.hasGnssFixForPowerGate(live, gnssFresh = false))
        assertFalse(
            MockLocationJob.hasGnssFixForPowerGate(
                live.copy(locateStatus = false),
                gnssFresh = true,
            ),
        )
    }

    @Test
    fun whenNoFixInjectsUnlessGnssTruthful() {
        assertFalse(MockLocationJob.shouldInjectWhenNoFix(gnssTruthful = true))
        assertTrue(MockLocationJob.shouldInjectWhenNoFix(gnssTruthful = false))
    }
}
