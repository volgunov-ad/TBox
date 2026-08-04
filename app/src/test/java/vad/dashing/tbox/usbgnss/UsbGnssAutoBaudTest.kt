package vad.dashing.tbox.usbgnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbGnssAutoBaudTest {
    @Test
    fun candidateBauds_prefersCurrentThenCommon() {
        val list = UsbGnssAutoBaud.candidateBauds(57_600)
        assertEquals(57_600, list.first())
        assertTrue(list.indexOf(115_200) < list.indexOf(19_200))
        assertEquals(UsbGnssDeviceIds.BAUD_OPTIONS.toSet(), list.toSet())
    }

    @Test
    fun hasValidChecksum_acceptsKnownGga() {
        assertTrue(
            UsbGnssAutoBaud.hasValidChecksum(
                "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47",
            ),
        )
    }

    @Test
    fun hasValidChecksum_rejectsGarbageAndBadCs() {
        assertFalse(UsbGnssAutoBaud.hasValidChecksum(""))
        assertFalse(UsbGnssAutoBaud.hasValidChecksum("\$GPGGA,1,2,3"))
        assertFalse(UsbGnssAutoBaud.hasValidChecksum("\$GPGGA,1,2,3*ZZ"))
        assertFalse(UsbGnssAutoBaud.hasValidChecksum("\$GPGGA,1,2,3*00"))
    }

    @Test
    fun probeTimingsArePositive() {
        assertTrue(UsbGnssAutoBaud.PROBE_MS_PER_BAUD >= 2_500L)
        assertTrue(UsbGnssAutoBaud.CONNECT_WAIT_MS >= 1_000L)
        assertTrue(UsbGnssAutoBaud.SETTLE_MS_AFTER_CONNECT >= 200L)
    }

    @Test
    fun silenceReopenSkippedWhileAutoBaudRunning() {
        UsbGnssRepository.reset()
        UsbGnssRepository.beginAutoBaudRun()
        UsbGnssRepository.setConnected(true, atMs = 90_000L)
        assertFalse(UsbGnssRepository.needsNmeaSilenceReopen(nowMs = 110_000L, silenceMs = 10_000L))
        UsbGnssRepository.finishAutoBaudFailed()
        UsbGnssRepository.reset()
    }

    @Test
    fun previewAutoBaudTrying_updatesUiWithoutArmingEpoch() {
        UsbGnssRepository.reset()
        UsbGnssRepository.beginAutoBaudRun()
        UsbGnssRepository.previewAutoBaudTrying(115_200)
        assertEquals(115_200, UsbGnssRepository.autoBaudTryingBaud.value)
        assertFalse(UsbGnssRepository.hasValidNmeaSinceProbeEpoch())
        UsbGnssRepository.markValidChecksumNmea(atMs = 1_000L)
        assertFalse(UsbGnssRepository.hasValidNmeaSinceProbeEpoch())
        UsbGnssRepository.setAutoBaudTrying(115_200, epochMs = 900L)
        UsbGnssRepository.markValidChecksumNmea(atMs = 1_000L)
        assertTrue(UsbGnssRepository.hasValidNmeaSinceProbeEpoch())
        UsbGnssRepository.finishAutoBaudSuccess(115_200)
        assertEquals(UsbGnssRepository.AutoBaudPhase.SUCCESS, UsbGnssRepository.autoBaudPhase.value)
        UsbGnssRepository.clearAutoBaudPhaseIfTerminal()
        UsbGnssRepository.reset()
    }

    @Test
    fun isCompatibleStableId_allowsSerialUpgrade() {
        assertTrue(
            UsbGnssDeviceIds.isCompatibleStableId("1a86:7523:abc", "1a86:7523"),
        )
        assertTrue(
            UsbGnssDeviceIds.isCompatibleStableId("1a86:7523", "1a86:7523"),
        )
        assertFalse(
            UsbGnssDeviceIds.isCompatibleStableId("10c4:ea60", "1a86:7523"),
        )
    }
}
