package vad.dashing.tbox.adb

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbTcpListenFactTest {
    @After
    fun tearDown() {
        AdbTcpListenFact.resetForTests()
    }

    @Test
    fun isListening_usesProbeAndCachesWithinTtl() {
        var probes = 0
        AdbTcpListenFact.setNowElapsedMsForTests { 1_000L }
        AdbTcpListenFact.setProbeForTests { _, _, _ ->
            probes += 1
            true
        }

        assertTrue(AdbTcpListenFact.isListening())
        assertTrue(AdbTcpListenFact.isListening())
        assertEquals(1, probes)

        AdbTcpListenFact.setNowElapsedMsForTests { 1_600L }
        assertTrue(AdbTcpListenFact.isListening())
        assertEquals(2, probes)
    }

    @Test
    fun remember_feedsCacheWithoutProbe() {
        var probes = 0
        AdbTcpListenFact.setNowElapsedMsForTests { 5_000L }
        AdbTcpListenFact.setProbeForTests { _, _, _ ->
            probes += 1
            false
        }
        AdbTcpListenFact.remember(true, atElapsedMs = 5_000L)

        assertTrue(AdbTcpListenFact.isListening())
        assertEquals(0, probes)
        assertFalse(
            run {
                AdbTcpListenFact.setNowElapsedMsForTests { 5_600L }
                AdbTcpListenFact.isListening()
            },
        )
        assertEquals(1, probes)
    }
}
