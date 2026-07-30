package vad.dashing.tbox.usbgnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbGnssDeviceIdsTest {

    @Test
    fun formatAndParseStableId_withoutSerial() {
        val id = UsbGnssDeviceIds.formatStableId(0x1A86, 0x7523, null)
        assertEquals("1a86:7523", id)
        val parsed = UsbGnssDeviceIds.parseStableId(id)!!
        assertEquals(0x1A86, parsed.vendorId)
        assertEquals(0x7523, parsed.productId)
        assertNull(parsed.serial)
    }

    @Test
    fun formatAndParseStableId_withSerial() {
        val id = UsbGnssDeviceIds.formatStableId(0x10C4, 0xEA60, "ABC123")
        assertEquals("10c4:ea60:ABC123", id)
        val parsed = UsbGnssDeviceIds.parseStableId(id)!!
        assertEquals(0x10C4, parsed.vendorId)
        assertEquals(0xEA60, parsed.productId)
        assertEquals("ABC123", parsed.serial)
    }

    @Test
    fun parseStableId_rejectsBlank() {
        assertNull(UsbGnssDeviceIds.parseStableId(null))
        assertNull(UsbGnssDeviceIds.parseStableId(""))
        assertNull(UsbGnssDeviceIds.parseStableId("not-an-id"))
    }

    @Test
    fun isCandidate_excludesEspressif() {
        assertFalse(
            UsbGnssDeviceIds.isCandidate(
                vendorId = UsbGnssDeviceIds.ESPRESSIF_VID,
                looksLikeNetworkRndis = false,
                hasCdcData = true,
                hasBulkIn = true,
                hasBulkInOut = true,
            ),
        )
    }

    @Test
    fun isCandidate_excludesRndisLike() {
        assertFalse(
            UsbGnssDeviceIds.isCandidate(
                vendorId = 0x1234,
                looksLikeNetworkRndis = true,
                hasCdcData = true,
                hasBulkIn = true,
                hasBulkInOut = true,
            ),
        )
    }

    @Test
    fun isCandidate_acceptsCdcData() {
        assertTrue(
            UsbGnssDeviceIds.isCandidate(
                vendorId = 0x2341,
                looksLikeNetworkRndis = false,
                hasCdcData = true,
                hasBulkIn = true,
                hasBulkInOut = true,
            ),
        )
    }

    @Test
    fun isCandidate_acceptsCh340Bridge() {
        assertTrue(
            UsbGnssDeviceIds.isCandidate(
                vendorId = 0x1A86,
                looksLikeNetworkRndis = false,
                hasCdcData = false,
                hasBulkIn = true,
                hasBulkInOut = true,
            ),
        )
    }

    @Test
    fun isCandidate_rejectsUnknownWithoutCdc() {
        assertFalse(
            UsbGnssDeviceIds.isCandidate(
                vendorId = 0x1234,
                looksLikeNetworkRndis = false,
                hasCdcData = false,
                hasBulkIn = true,
                hasBulkInOut = true,
            ),
        )
    }

    @Test
    fun isCandidate_requiresBulkIn() {
        assertFalse(
            UsbGnssDeviceIds.isCandidate(
                vendorId = 0x1A86,
                looksLikeNetworkRndis = false,
                hasCdcData = false,
                hasBulkIn = false,
                hasBulkInOut = false,
            ),
        )
    }

    @Test
    fun matchesStableIdParts_vidPidOnly() {
        assertTrue(
            UsbGnssDeviceIds.matchesStableIdParts(0x1A86, 0x7523, null, "1a86:7523"),
        )
    }

    @Test
    fun matchesStableIdParts_softWhenSerialUnread() {
        assertTrue(
            UsbGnssDeviceIds.matchesStableIdParts(
                0x10C4,
                0xEA60,
                actualSerial = null,
                stableId = "10c4:ea60:ABC123",
            ),
        )
        assertTrue(
            UsbGnssDeviceIds.matchesStableIdParts(
                0x10C4,
                0xEA60,
                actualSerial = "",
                stableId = "10c4:ea60:ABC123",
            ),
        )
    }

    @Test
    fun classifyStableIdMatches_rejectsTwinsWithoutExactSerial() {
        assertEquals(
            UsbGnssDeviceIds.MatchClass.AMBIGUOUS,
            UsbGnssDeviceIds.classifyStableIdMatches(softMatchCount = 2, exactSerialMatchCount = 0),
        )
        assertEquals(
            UsbGnssDeviceIds.MatchClass.UNIQUE,
            UsbGnssDeviceIds.classifyStableIdMatches(softMatchCount = 2, exactSerialMatchCount = 1),
        )
        assertEquals(
            UsbGnssDeviceIds.MatchClass.UNIQUE,
            UsbGnssDeviceIds.classifyStableIdMatches(softMatchCount = 1, exactSerialMatchCount = 0),
        )
        assertEquals(
            UsbGnssDeviceIds.MatchClass.NOT_FOUND,
            UsbGnssDeviceIds.classifyStableIdMatches(softMatchCount = 0, exactSerialMatchCount = 0),
        )
    }
}

class NmeaFixAccumulatorTest {

    @Test
    fun mergesRmcAndGga() {
        val acc = NmeaFixAccumulator()
        val rmc =
            "\$GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val gga =
            "\$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"
        assertNull(acc.onLine("\$GPGSV,1,1,00*79"))
        val afterRmc = acc.onLine(rmc)
        assertNotNull(afterRmc)
        assertTrue(afterRmc!!.locateStatus)
        val afterGga = acc.onLine(gga)
        assertNotNull(afterGga)
        assertTrue(afterGga!!.locateStatus)
        assertEquals(8, afterGga.usingSatellites)
        assertEquals(545.4, afterGga.altitude, 0.01)
    }
}
