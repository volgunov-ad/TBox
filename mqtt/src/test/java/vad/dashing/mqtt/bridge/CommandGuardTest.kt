package vad.dashing.mqtt.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandGuardTest {
    @Test
    fun dropsRetainedEchoInFlightAndExtraCommandWithinASecond() {
        val guard = CommandGuard()
        assertEquals("retained", guard.decide("drive_mode", "SPT", true, "NOR", false, 1_000))
        assertEquals("unchanged", guard.decide("drive_mode", "NOR", false, "NOR", false, 1_000))
        assertNull(guard.decide("drive_mode", "SPT", false, "NOR", false, 1_000))
        assertEquals("in_flight", guard.decide("drive_mode", "ECO", false, "NOR", false, 1_100))
        guard.finish("drive_mode")
        assertEquals("rate", guard.decide("drive_mode", "ECO", false, "NOR", false, 1_500))
        assertNull(guard.decide("drive_mode", "ECO", false, "NOR", false, 2_000))
    }

    @Test
    fun buttonRepeatsEvenWhenPayloadMatches() {
        val guard = CommandGuard()
        assertNull(guard.decide("media", "PRESS", false, "PRESS", true, 5_000))
        guard.finish("media")
        assertEquals("rate", guard.decide("media", "PRESS", false, "PRESS", true, 5_100))
        assertNull(guard.decide("media", "PRESS", false, "PRESS", true, 6_100))
    }
}
