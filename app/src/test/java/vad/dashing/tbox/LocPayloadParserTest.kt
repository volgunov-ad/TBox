package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.utils.LocPayloadParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

class LocPayloadParserTest {

    @Test
    fun detectsNmeaFromScreenshotHex() {
        // Truncated raw payload from TBox Geoposition tab ("Сырые данные")
        val hex = "24 47 4E 52 4D 43 2C 30 33 30 31 35 32 2E 38 33 2C 56 " +
            "2C 2C 2C 2C 2C 2C 2C 31 30 31 32 32 35 2C 2C 2C 4E"
        val payload = hexToBytes(hex)
        assertTrue(LocPayloadParser.looksLikeNmea(payload))
        val loc = LocPayloadParser.parse(payload, Date())!!
        assertFalse(loc.locateStatus)
        assertEquals(0.0, loc.latitude, 1e-9)
        assertEquals(0.0, loc.longitude, 1e-9)
        assertEquals(3, loc.utcTime?.hour)
        assertEquals(1, loc.utcTime?.minute)
        assertEquals(52, loc.utcTime?.second)
        assertEquals(10, loc.utcTime?.day)
        assertEquals(12, loc.utcTime?.month)
        assertEquals(25, loc.utcTime?.year)
        // NMEA rawValue is readable ASCII (full payload, not a 39-byte hex cut)
        assertTrue(loc.rawValue.startsWith("\$GNRMC"))
        assertTrue(loc.rawValue.contains("030152.83"))
        assertTrue(loc.rawValue.contains(",V,"))
    }

    @Test
    fun parsesValidGnrmcAndGngga() {
        val nmea = listOf(
            "\$GNRMC,083012.00,A,6247.2260,N,07704.4640,E,012.5,084.0,200726,,,A*6F",
            "\$GNGGA,083012.00,6247.2260,N,07704.4640,E,1,08,1.0,45.2,M,0.0,M,,*5A",
        ).joinToString("\r\n")
        val loc = LocPayloadParser.parse(nmea.toByteArray(Charsets.US_ASCII), Date())!!
        assertTrue(loc.locateStatus)
        assertEquals(62.7871, loc.latitude, 1e-4)
        assertEquals(77.0744, loc.longitude, 1e-4)
        assertEquals(45.2, loc.altitude, 1e-6)
        assertEquals(8, loc.usingSatellites)
        assertEquals(12.5f * 1.852f, loc.speed, 1e-3f)
        assertEquals(84.0f, loc.trueDirection, 1e-3f)
        assertEquals(8, loc.utcTime?.hour)
        assertEquals(30, loc.utcTime?.minute)
        assertEquals(12, loc.utcTime?.second)
    }

    @Test
    fun parseNmeaCoordinate_ddmmToDecimal() {
        assertEquals(
            62.7871,
            LocPayloadParser.parseNmeaCoordinate("6247.2260", "N")!!,
            1e-4,
        )
        assertEquals(
            -77.0744,
            LocPayloadParser.parseNmeaCoordinate("07704.4640", "W")!!,
            1e-4,
        )
        assertEquals(null, LocPayloadParser.parseNmeaCoordinate("", "N"))
        assertEquals(null, LocPayloadParser.parseNmeaCoordinate("6247.2260", null))
    }

    @Test
    fun parseBinary_classic39ByteStruct() {
        val buf = ByteBuffer.allocate(39).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(1) // locateStatus
        buf.put(25.toByte()) // year
        buf.put(7.toByte()) // month
        buf.put(20.toByte()) // day
        buf.put(8.toByte()) // hour
        buf.put(30.toByte()) // minute
        buf.put(12.toByte()) // second
        buf.put(0) // lon dir east
        buf.putInt(77_074_400) // lon microdegrees
        buf.put(0) // lat dir north
        buf.putInt(62_787_100) // lat microdegrees
        buf.putInt(45_200_000) // alt micro-meters → /1e6 = 45.2
        buf.put(10) // visible
        buf.put(8) // using
        buf.putShort(125) // speed 12.5
        buf.putShort(840) // true 84.0
        buf.putShort(830) // mag 83.0
        val payload = buf.array()
        assertFalse(LocPayloadParser.looksLikeNmea(payload))
        val loc = LocPayloadParser.parse(payload, Date())
        assertNotNull(loc)
        assertTrue(loc!!.locateStatus)
        assertEquals(77.0744, loc.longitude, 1e-6)
        assertEquals(62.7871, loc.latitude, 1e-6)
        assertEquals(45.2, loc.altitude, 1e-6)
        assertEquals(10, loc.visibleSatellites)
        assertEquals(8, loc.usingSatellites)
        assertEquals(12.5f, loc.speed, 1e-3f)
        assertEquals(84.0f, loc.trueDirection, 1e-3f)
        assertEquals(83.0f, loc.magneticDirection, 1e-3f)
    }

    @Test
    fun nmeaAsBinaryWouldBeGarbage_butNmeaPathWins() {
        val nmea = "\$GNRMC,030152.83,V,,,,,,,101225,,,N*4A"
        val payload = nmea.toByteArray(Charsets.US_ASCII)
        // Ensure we do not treat leading '$' (0x24) as locateStatus=true with bogus coords
        val loc = LocPayloadParser.parse(payload, Date())!!
        assertFalse(loc.locateStatus)
        assertEquals(0.0, loc.latitude, 0.0)
        assertEquals(0.0, loc.longitude, 0.0)
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
    }
}
