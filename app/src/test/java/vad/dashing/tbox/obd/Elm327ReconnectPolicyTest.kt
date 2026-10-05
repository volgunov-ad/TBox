package vad.dashing.tbox.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327ReconnectPolicyTest {
    @Test
    fun absentLink_usesLongBackoffAndOneShortConnect() {
        assertEquals(30_000L, Elm327ReconnectPolicy.backoffMs(hadSuccessfulLink = false, failureIndex = 0))
        assertEquals(120_000L, Elm327ReconnectPolicy.backoffMs(hadSuccessfulLink = false, failureIndex = 1))
        assertEquals(300_000L, Elm327ReconnectPolicy.backoffMs(hadSuccessfulLink = false, failureIndex = 2))
        assertEquals(600_000L, Elm327ReconnectPolicy.backoffMs(hadSuccessfulLink = false, failureIndex = 3))
        assertEquals(600_000L, Elm327ReconnectPolicy.backoffMs(hadSuccessfulLink = false, failureIndex = 9))

        assertEquals(1, Elm327ReconnectPolicy.rfcommAttemptCount(false, 0))
        assertEquals(8_000L, Elm327ReconnectPolicy.connectTimeoutMs(false, 0))
        assertEquals(300_000L, Elm327ReconnectPolicy.bondRetryMs(false, 0))
        assertEquals(12_000L, Elm327ReconnectPolicy.bondTimeoutMs(false, 0))
        assertFalse(Elm327ReconnectPolicy.aggressiveReconnect(false, 0))
    }

    @Test
    fun afterLink_shortLadderThenAbsentSchedule() {
        assertEquals(3_000L, Elm327ReconnectPolicy.backoffMs(true, 0))
        assertEquals(10_000L, Elm327ReconnectPolicy.backoffMs(true, 1))
        assertEquals(30_000L, Elm327ReconnectPolicy.backoffMs(true, 2))
        assertTrue(Elm327ReconnectPolicy.aggressiveReconnect(true, 2))
        assertEquals(3, Elm327ReconnectPolicy.rfcommAttemptCount(true, 0))
        assertEquals(15_000L, Elm327ReconnectPolicy.connectTimeoutMs(true, 1))

        assertEquals(30_000L, Elm327ReconnectPolicy.backoffMs(true, 3))
        assertEquals(120_000L, Elm327ReconnectPolicy.backoffMs(true, 4))
        assertEquals(600_000L, Elm327ReconnectPolicy.backoffMs(true, 8))
        assertFalse(Elm327ReconnectPolicy.aggressiveReconnect(true, 3))
        assertEquals(1, Elm327ReconnectPolicy.rfcommAttemptCount(true, 3))
        assertEquals(8_000L, Elm327ReconnectPolicy.connectTimeoutMs(true, 4))
    }

    @Test
    fun pinForAttempt_rotatesOnePinAtATime() {
        assertEquals("1111", Elm327ReconnectPolicy.pinForAttempt("1111", 0))
        assertEquals("1234", Elm327ReconnectPolicy.pinForAttempt("1111", 1))
        assertEquals("0000", Elm327ReconnectPolicy.pinForAttempt("1111", 2))
        assertEquals("1111", Elm327ReconnectPolicy.pinForAttempt("1111", 5))

        assertEquals("1234", Elm327ReconnectPolicy.pinForAttempt("", 0))
        assertEquals("0000", Elm327ReconnectPolicy.pinForAttempt("  ", 1))
        assertEquals("1234", Elm327ReconnectPolicy.pinForAttempt("1234", 0))
        assertEquals("0000", Elm327ReconnectPolicy.pinForAttempt("1234", 1))
    }
}
