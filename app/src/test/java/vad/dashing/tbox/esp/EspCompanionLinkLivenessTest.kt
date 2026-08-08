package vad.dashing.tbox.esp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EspCompanionLinkLivenessTest {

    @Test
    fun doesNotReopenWhenDisconnected() {
        assertFalse(
            EspCompanionProtocol.shouldForceReopenLink(
                connected = false,
                lastMessageAtMs = 0L,
                connectedAtMs = 1_000L,
                nowMs = 20_000L,
            ),
        )
    }

    @Test
    fun reopensWhenOpenButNeverReceivedRx() {
        assertTrue(
            EspCompanionProtocol.shouldForceReopenLink(
                connected = true,
                lastMessageAtMs = 0L,
                connectedAtMs = 1_000L,
                nowMs = 1_000L + EspCompanionProtocol.HEARTBEAT_TIMEOUT_MS + 1,
            ),
        )
        assertFalse(
            EspCompanionProtocol.shouldForceReopenLink(
                connected = true,
                lastMessageAtMs = 0L,
                connectedAtMs = 1_000L,
                nowMs = 1_000L + EspCompanionProtocol.HEARTBEAT_TIMEOUT_MS,
            ),
        )
    }

    @Test
    fun reopensWhenLastMessageStale() {
        assertTrue(
            EspCompanionProtocol.shouldForceReopenLink(
                connected = true,
                lastMessageAtMs = 5_000L,
                connectedAtMs = 1_000L,
                nowMs = 5_000L + EspCompanionProtocol.HEARTBEAT_TIMEOUT_MS + 1,
            ),
        )
    }
}
