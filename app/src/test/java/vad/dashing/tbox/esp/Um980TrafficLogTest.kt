package vad.dashing.tbox.esp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Um980TrafficLogTest {
    @Before
    fun setUp() {
        ShadowSystemClock.reset()
        EspCompanionRepository.clearUm980TrafficLog()
        EspCompanionRepository.clearCanRecentFrames()
    }

    @Test
    fun keepsAtMost100Entries() {
        repeat(120) { i ->
            EspCompanionRepository.appendUm980TrafficLog(Um980LogDirection.TX, "cmd$i")
        }
        val log = EspCompanionRepository.um980TrafficLog.value
        assertEquals(100, log.size)
        assertEquals("cmd20", log.first().text)
        assertEquals("cmd119", log.last().text)
    }

    @Test
    fun throttlesGeoMessagesToFiveSeconds() {
        val t0 = 1_000_000L
        EspCompanionRepository.appendUm980TrafficLog(
            Um980LogDirection.RX,
            "gps 1",
            isGeo = true,
            atMs = t0,
        )
        EspCompanionRepository.appendUm980TrafficLog(
            Um980LogDirection.RX,
            "gps 2",
            isGeo = true,
            atMs = t0 + 4_999L,
        )
        EspCompanionRepository.appendUm980TrafficLog(
            Um980LogDirection.RX,
            "gps 3",
            isGeo = true,
            atMs = t0 + 5_000L,
        )
        val geo = EspCompanionRepository.um980TrafficLog.value.map { it.text }
        assertEquals(listOf("gps 1", "gps 3"), geo)
    }

    @Test
    fun doesNotThrottleNonGeo() {
        val t0 = 1_000_000L
        repeat(3) { i ->
            EspCompanionRepository.appendUm980TrafficLog(
                Um980LogDirection.TX,
                "CONFIG $i",
                isGeo = false,
                atMs = t0 + i,
            )
        }
        assertEquals(3, EspCompanionRepository.um980TrafficLog.value.size)
    }

    @Test
    fun clearEmptiesLog() {
        EspCompanionRepository.appendUm980TrafficLog(Um980LogDirection.TX, "x")
        EspCompanionRepository.clearUm980TrafficLog()
        assertTrue(EspCompanionRepository.um980TrafficLog.value.isEmpty())
    }

    @Test
    fun companionTrafficLogAlias() {
        EspCompanionRepository.appendCompanionTrafficLog(
            CompanionLogDirection.TX,
            "hello",
        )
        assertEquals(1, EspCompanionRepository.um980TrafficLog.value.size)
        assertEquals("hello", EspCompanionRepository.um980TrafficLog.value.single().text)
    }

    @Test
    fun canRecentFramesRingKeeps200() {
        val frame = CanFrame(id = 1, ext = false, data = byteArrayOf(0x01))
        repeat(210) { i ->
            if (i > 0) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(101))
            }
            EspCompanionRepository.appendCanFrame(CompanionLogDirection.RX, frame)
        }
        assertEquals(200, EspCompanionRepository.canRecentFrames.value.size)
    }
}
