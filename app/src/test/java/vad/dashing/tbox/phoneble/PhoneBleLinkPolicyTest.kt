package vad.dashing.tbox.phoneble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBleLinkPolicyTest {

    @Test
    fun hungScan_requiresScanningWithoutGatt() {
        assertFalse(
            PhoneBleLinkPolicy.hungScan(
                scanning = false,
                hasGatt = false,
                attemptAgeMs = PhoneBleLinkPolicy.SCAN_STALE_MS,
                force = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.hungScan(
                scanning = true,
                hasGatt = true,
                attemptAgeMs = PhoneBleLinkPolicy.SCAN_STALE_MS,
                force = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.hungScan(
                scanning = true,
                hasGatt = false,
                attemptAgeMs = PhoneBleLinkPolicy.SCAN_STALE_MS - 1,
                force = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.hungScan(
                scanning = true,
                hasGatt = false,
                attemptAgeMs = PhoneBleLinkPolicy.SCAN_STALE_MS,
                force = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.hungScan(
                scanning = true,
                hasGatt = false,
                attemptAgeMs = 0,
                force = true,
            ),
        )
    }

    @Test
    fun hungConnect_onlyBeforeLinkUp() {
        assertFalse(
            PhoneBleLinkPolicy.hungConnect(
                linkUp = true,
                hasGatt = true,
                attemptAgeMs = PhoneBleLinkPolicy.CONNECT_STALE_MS,
                force = true,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.hungConnect(
                linkUp = false,
                hasGatt = false,
                attemptAgeMs = PhoneBleLinkPolicy.CONNECT_STALE_MS,
                force = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.hungConnect(
                linkUp = false,
                hasGatt = true,
                attemptAgeMs = PhoneBleLinkPolicy.CONNECT_STALE_MS,
                force = false,
            ),
        )
    }

    @Test
    fun staleLinked_recoversZombieGattAfterDoze() {
        assertFalse(
            PhoneBleLinkPolicy.staleLinked(
                linkUp = false,
                snapAgeMs = PhoneBleLinkPolicy.LINK_STALE_MS,
                force = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.staleLinked(
                linkUp = true,
                snapAgeMs = PhoneBleLinkPolicy.LINK_STALE_MS - 1,
                force = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.staleLinked(
                linkUp = true,
                snapAgeMs = PhoneBleLinkPolicy.LINK_STALE_MS,
                force = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.staleLinked(
                linkUp = true,
                snapAgeMs = 0,
                force = true,
            ),
        )
    }

    @Test
    fun hungWrite_timesOutWithoutCallback() {
        assertFalse(
            PhoneBleLinkPolicy.hungWrite(
                writing = false,
                writeAgeMs = PhoneBleLinkPolicy.WRITE_STALE_MS,
                force = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.hungWrite(
                writing = true,
                writeAgeMs = PhoneBleLinkPolicy.WRITE_STALE_MS - 1,
                force = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.hungWrite(
                writing = true,
                writeAgeMs = PhoneBleLinkPolicy.WRITE_STALE_MS,
                force = false,
            ),
        )
    }

    @Test
    fun resumeShouldKick_skipsFreshAttempt_reopensStaleLink() {
        assertFalse(
            PhoneBleLinkPolicy.resumeShouldKick(
                linkUp = false,
                scanningOrConnecting = true,
                attemptAgeMs = PhoneBleLinkPolicy.CONNECT_STALE_MS - 1,
                snapAgeMs = 0,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.resumeShouldKick(
                linkUp = false,
                scanningOrConnecting = true,
                attemptAgeMs = PhoneBleLinkPolicy.CONNECT_STALE_MS,
                snapAgeMs = 0,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.resumeShouldKick(
                linkUp = false,
                scanningOrConnecting = false,
                attemptAgeMs = 0,
                snapAgeMs = 0,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.resumeShouldKick(
                linkUp = true,
                scanningOrConnecting = false,
                attemptAgeMs = 0,
                snapAgeMs = PhoneBleLinkPolicy.LINK_STALE_MS - 1,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.resumeShouldKick(
                linkUp = true,
                scanningOrConnecting = false,
                attemptAgeMs = 0,
                snapAgeMs = PhoneBleLinkPolicy.LINK_STALE_MS,
            ),
        )
    }

    @Test
    fun directSearchRetry_backsOffOnlyInBackground() {
        val fg = (1..8).map { PhoneBleLinkPolicy.directSearchRetryMs(foreground = true, passesWithoutLink = it) }
        assertTrue(fg.all { it == PhoneBleLinkPolicy.DIRECT_RETRY_MS })
        val bg = (0..8).map { PhoneBleLinkPolicy.directSearchRetryMs(foreground = false, passesWithoutLink = it) }
        assertEquals(
            listOf(5_000L, 5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L, 60_000L, 60_000L),
            bg,
        )
    }

    @Test
    fun directWaitAfterClose_failedPassDoesNotLookLikeSuccess() {
        val failed = PhoneBleLinkPolicy.directWaitAfterClose(
            answered = false,
            foreground = true,
            passesWithoutLink = 2,
        )
        assertEquals(3, failed.passesWithoutLink)
        assertTrue(failed.waitMs >= PhoneBleLinkPolicy.DIRECT_CLOSED_BEFORE_ANSWER_FG_MS)
        val answered = PhoneBleLinkPolicy.directWaitAfterClose(
            answered = true,
            foreground = true,
            passesWithoutLink = 2,
        )
        assertEquals(0, answered.passesWithoutLink)
        assertEquals(PhoneBleLinkPolicy.DIRECT_RECONNECT_MS, answered.waitMs)
    }

    @Test
    fun acceptWindow_commandBlocksOlderSnapshot() {
        val after = PhoneBleLinkPolicy.acceptWindowAfterSend(
            previous = PhoneBleLinkPolicy.AcceptWindow(1L, 4L),
            counter = 5L,
            resetAcceptRange = true,
            updateWaiting = false,
        )
        assertFalse(PhoneBleLinkPolicy.counterInAcceptWindow(4L, after))
    }
}
