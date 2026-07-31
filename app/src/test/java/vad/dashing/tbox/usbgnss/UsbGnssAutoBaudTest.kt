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
    fun silenceReopenSkippedWhileAutoBaudRunning() {
        UsbGnssRepository.reset()
        UsbGnssRepository.beginAutoBaudRun()
        UsbGnssRepository.setConnected(true, atMs = 90_000L)
        assertFalse(UsbGnssRepository.needsNmeaSilenceReopen(nowMs = 110_000L, silenceMs = 10_000L))
        UsbGnssRepository.finishAutoBaudFailed()
        UsbGnssRepository.reset()
    }
}
