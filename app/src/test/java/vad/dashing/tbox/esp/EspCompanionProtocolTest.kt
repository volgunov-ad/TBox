package vad.dashing.tbox.esp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EspCompanionProtocolTest {

    @Test
    fun parseHello() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"hello","fw":"0.3.0","gpioIn":8,"relays":4,"um980":true,"baud":9600}"""
        )
        assertTrue(msg is EspMessage.Hello)
        val hello = msg as EspMessage.Hello
        assertEquals("0.3.0", hello.fw)
        assertEquals(8, hello.gpioInCount)
        assertEquals(4, hello.relayCount)
        assertTrue(hello.um980)
        assertEquals(9600, hello.baud)
    }

    @Test
    fun parseGpsAndMapToLocValues() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"gps","fix":1,"lat":55.75,"lon":37.61,"alt":150.2,"speedKmh":42.0,"course":180.5,"satsUsed":14,"satsVis":28,"utc":"2026-07-18T12:00:00Z","hdop":1.1,"pdop":1.5,"vdop":2.0}"""
        )
        assertTrue(msg is EspMessage.Gps)
        val gps = msg as EspMessage.Gps
        val loc = EspCompanionProtocol.gpsToLocValues(gps)
        assertTrue(loc.locateStatus)
        assertEquals(55.75, loc.latitude, 1e-6)
        assertEquals(37.61, loc.longitude, 1e-6)
        assertEquals(14, loc.usingSatellites)
        assertEquals(28, loc.visibleSatellites)
        assertEquals(42.0f, loc.speed, 0.01f)
        assertEquals(1.1f, loc.hdop!!, 1e-3f)
        assertEquals(1.5f, loc.pdop!!, 1e-3f)
        assertEquals(2.0f, loc.vdop!!, 1e-3f)
        assertNotNull(loc.utcTime)
    }

    @Test
    fun parseGpsWithoutDopKeepsNull() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"gps","fix":1,"lat":55.75,"lon":37.61,"alt":150.2,"speedKmh":42.0,"course":180.5,"satsUsed":14,"satsVis":28,"utc":"2026-07-18T12:00:00Z"}"""
        ) as EspMessage.Gps
        val loc = EspCompanionProtocol.gpsToLocValues(msg)
        assertNull(loc.hdop)
        assertNull(loc.pdop)
        assertNull(loc.vdop)
    }

    @Test
    fun parseGpioRelayHb() {
        assertTrue(
            EspCompanionProtocol.parseLine("""{"v":1,"t":"gpio","mask":5,"ms":1001}""")
                is EspMessage.Gpio
        )
        assertTrue(
            EspCompanionProtocol.parseLine("""{"v":1,"t":"gpioEvent","ch":0,"level":1,"ms":1002}""")
                is EspMessage.GpioEvent
        )
        assertTrue(
            EspCompanionProtocol.parseLine("""{"v":1,"t":"relay","mask":1}""") is EspMessage.Relay
        )
        assertTrue(
            EspCompanionProtocol.parseLine("""{"v":1,"t":"hb","uptimeMs":12345}""")
                is EspMessage.Heartbeat
        )
    }

    @Test
    fun encodeUm980AndReboot() {
        val cmd = EspCompanionProtocol.encodeUm980Cmd("GPGGA 0.5")
        assertTrue(cmd.contains("\"t\":\"um980Cmd\""))
        assertTrue(cmd.contains("\"cmd\":\"GPGGA 0.5\""))
        assertTrue(cmd.endsWith("\n"))
        assertTrue(EspCompanionProtocol.encodeReboot().contains("\"t\":\"reboot\""))
        val baud = EspCompanionProtocol.encodeUm980Baud(9600)
        assertTrue(baud.contains("\"t\":\"um980Baud\""))
        assertTrue(baud.contains("\"baud\":9600"))
    }

    @Test
    fun parseUm980RspAndRebootAck() {
        val rsp = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"um980Rsp","cmd":"CONFIG","lines":["${'$'}command,CONFIG","OK"],"ok":true}"""
        )
        assertTrue(rsp is EspMessage.Um980Rsp)
        val um = rsp as EspMessage.Um980Rsp
        assertEquals("CONFIG", um.cmd)
        assertEquals(2, um.lines.size)
        assertTrue(um.ok)
        assertTrue(
            EspCompanionProtocol.parseLine("""{"v":1,"t":"rebootAck"}""") is EspMessage.RebootAck
        )
        val baudMsg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"um980Baud","baud":57600,"ok":true}"""
        )
        assertTrue(baudMsg is EspMessage.Um980Baud)
        assertEquals(57600, (baudMsg as EspMessage.Um980Baud).baud)
        assertTrue(baudMsg.ok)
    }

    @Test
    fun encodeCommands() {
        assertTrue(EspCompanionProtocol.encodeHello().contains("\"t\":\"hello\""))
        assertTrue(EspCompanionProtocol.encodeRelaySet(3).contains("\"mask\":3"))
        assertTrue(EspCompanionProtocol.encodeHello().endsWith("\n"))
    }

    @Test
    fun rejectBadLines() {
        assertNull(EspCompanionProtocol.parseLine(""))
        assertNull(EspCompanionProtocol.parseLine("not-json"))
        assertNull(EspCompanionProtocol.parseLine("""{"v":2,"t":"hello"}"""))
        assertNull(EspCompanionProtocol.parseLine("""{"v":1,"t":"unknown"}"""))
    }

    @Test
    fun encodeParseOtaMessages() {
        val begin = EspCompanionProtocol.encodeOtaBegin(12345L, 0xA1B2C3D4L)
        assertTrue(begin.contains("\"t\":\"otaBegin\""))
        assertTrue(begin.contains("\"size\":12345"))
        assertTrue(begin.contains("\"crc32\":"))
        assertTrue(begin.endsWith("\n"))
        assertTrue(EspCompanionProtocol.encodeOtaEnd().contains("\"t\":\"otaEnd\""))

        val ack = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"otaAck","phase":"begin","offset":0,"ok":true}""",
        )
        assertTrue(ack is EspMessage.OtaAck)
        assertEquals("begin", (ack as EspMessage.OtaAck).phase)
        assertTrue(ack.ok)

        val nack = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"otaAck","phase":"chunk","offset":512,"ok":false,"err":"chunk crc"}""",
        ) as EspMessage.OtaAck
        assertFalse(nack.ok)
        assertEquals("chunk crc", nack.err)

        val done = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"otaDone","ok":true}""",
        )
        assertTrue(done is EspMessage.OtaDone)
        assertTrue((done as EspMessage.OtaDone).ok)
    }

    @Test
    fun otaChunkFrameAndCrc() {
        val payload = byteArrayOf(0xE9.toByte(), 0x01, 0x02, 0x03)
        val frame = EspCompanionProtocol.encodeOtaChunkFrame(payload)
        assertEquals(0xA5.toByte(), frame[0])
        assertEquals(0x5A.toByte(), frame[1])
        assertEquals(0, frame[2].toInt() and 0xFF)
        assertEquals(4, frame[3].toInt() and 0xFF)
        assertEquals(payload[0], frame[4])
        val crc = EspCompanionProtocol.crc32Ieee(payload)
        val got = ((frame[8].toLong() and 0xFF) shl 24) or
            ((frame[9].toLong() and 0xFF) shl 16) or
            ((frame[10].toLong() and 0xFF) shl 8) or
            (frame[11].toLong() and 0xFF)
        assertEquals(crc, got)
    }

    @Test
    fun validateFirmwareImageMagic() {
        assertEquals("empty", EspCompanionProtocol.validateFirmwareImage(0, 0xE9))
        assertEquals("too_large", EspCompanionProtocol.validateFirmwareImage(
            EspCompanionProtocol.OTA_MAX_IMAGE_SIZE + 1,
            0xE9,
        ))
        assertEquals("bad_magic", EspCompanionProtocol.validateFirmwareImage(100, 0x00))
        assertNull(EspCompanionProtocol.validateFirmwareImage(100, 0xE9))
    }
}
