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
        assertFalse(hello.can)
        assertNull(hello.canBackend)
        assertFalse(hello.magSupported)
        assertFalse(hello.mag)
        assertNull(hello.magChip)
        assertTrue(hello.magSeen.isEmpty())
    }

    @Test
    fun parseHelloCanCaps() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"hello","fw":"0.5.0","gpioIn":4,"relays":2,"um980":true,"baud":115200,""" +
                """"can":true,"canBackend":"mcp2515","canBaud":500000,"canLight":false}""",
        )
        assertTrue(msg is EspMessage.Hello)
        val hello = msg as EspMessage.Hello
        assertTrue(hello.can)
        assertEquals("mcp2515", hello.canBackend)
        assertEquals(500000, hello.canBaud)
        assertFalse(hello.canLight)
        assertFalse(hello.magSupported)
    }

    @Test
    fun parseHelloMagCaps() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"hello","fw":"0.6.0","gpioIn":4,"relays":2,"um980":true,"baud":115200,""" +
                """"mag":false,"magChip":"rm3100","magSeen":["mmc5983"]}""",
        )
        assertTrue(msg is EspMessage.Hello)
        val hello = msg as EspMessage.Hello
        assertTrue(hello.magSupported)
        assertFalse(hello.mag)
        assertEquals("rm3100", hello.magChip)
        assertEquals(listOf("mmc5983"), hello.magSeen)
    }

    @Test
    fun parseMagAndMagChip() {
        val mag = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"mag","chip":"rm3100","hx":12.4,"hy":-3.1,"hz":41.2,""" +
                """"heading":217.3,"fs":48.2,"ok":true}""",
        ) as EspMessage.Mag
        assertEquals("rm3100", mag.chip)
        assertEquals(12.4f, mag.hx, 0.01f)
        assertEquals(-3.1f, mag.hy, 0.01f)
        assertEquals(41.2f, mag.hz, 0.01f)
        assertEquals(217.3f, mag.headingDeg, 0.01f)
        assertEquals(48.2f, mag.fs, 0.01f)
        assertTrue(mag.ok)

        val ack = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"magChip","chip":"mmc5983","ok":true,"mag":true,"seen":["rm3100","mmc5983"]}""",
        ) as EspMessage.MagChip
        assertEquals("mmc5983", ack.chip)
        assertTrue(ack.ok)
        assertTrue(ack.mag)
        assertEquals(listOf("rm3100", "mmc5983"), ack.seen)
    }

    @Test
    fun parseHelloGnssAutodetect() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"hello","fw":"0.7.0","gpioIn":4,"relays":2,"gnss":true,""" +
                """"gnssChip":"neo-m8n","gnssModel":"NEO-M8N-0-10","um980":false,"baud":9600,""" +
                """"mag":true,"magChip":"ist8310","magSeen":["ist8310"]}""",
        )
        assertTrue(msg is EspMessage.Hello)
        val hello = msg as EspMessage.Hello
        assertTrue(hello.gnss)
        assertEquals("neo-m8n", hello.gnssChip)
        assertEquals("NEO-M8N-0-10", hello.gnssModel)
        assertFalse(hello.um980)
        assertEquals(9600, hello.baud)
        assertTrue(hello.mag)
        assertEquals("ist8310", hello.magChip)
        assertEquals(listOf("ist8310"), hello.magSeen)
    }

    @Test
    fun isKnownMagChipIncludesIst8310() {
        assertTrue(EspCompanionProtocol.isKnownMagChip("ist8310"))
        assertTrue(EspCompanionProtocol.isKnownMagChip("qmc5883l"))
        assertFalse(EspCompanionProtocol.isKnownMagChip("unknown"))
    }

    @Test
    fun encodeMagChipSet() {
        val line = EspCompanionProtocol.encodeMagChipSet(EspCompanionProtocol.MAG_CHIP_RM3100)
        assertTrue(line.contains("\"t\":\"magChipSet\""))
        assertTrue(line.contains("\"chip\":\"rm3100\""))
        assertTrue(EspCompanionProtocol.isKnownMagChip("RM3100"))
        assertFalse(EspCompanionProtocol.isKnownMagChip("qmc5883"))
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
    fun parseGpsWithHrmsVrms() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"gps","fix":1,"lat":55.75,"lon":37.61,"alt":150.2,"speedKmh":42.0,"course":180.5,"satsUsed":14,"satsVis":28,"utc":"2026-07-18T12:00:00Z","hdop":1.1,"pdop":1.5,"vdop":2.0,"hrms":0.114,"vrms":0.09}"""
        ) as EspMessage.Gps
        val loc = EspCompanionProtocol.gpsToLocValues(msg)
        assertEquals(0.114f, loc.hrms!!, 1e-3f)
        assertEquals(0.09f, loc.vrms!!, 1e-3f)
        assertEquals(1, loc.fixQuality)
        assertNull(loc.diffAgeSec)
    }

    @Test
    fun parseGpsWithDiffAge() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"gps","fix":4,"lat":55.75,"lon":37.61,"alt":150.2,"speedKmh":0.0,"course":0.0,"satsUsed":18,"satsVis":28,"utc":"2026-07-18T12:00:00Z","diffAge":0.8}"""
        ) as EspMessage.Gps
        val loc = EspCompanionProtocol.gpsToLocValues(msg)
        assertEquals(4, loc.fixQuality)
        assertEquals(0.8f, loc.diffAgeSec!!, 1e-3f)
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
        assertNull(loc.hrms)
        assertNull(loc.vrms)
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

    @Test
    fun encodeCanTxAndFilter() {
        val tx = EspCompanionProtocol.encodeCanTx(
            id = 0x280,
            ext = false,
            data = byteArrayOf(0x11, 0x22),
            rtr = false,
        )
        assertTrue(tx.contains("\"t\":\"canTx\""))
        assertTrue(tx.contains("\"id\":\"0x280\""))
        assertTrue(tx.contains("\"ext\":false"))
        assertTrue(tx.contains("\"data\":\"1122\""))
        assertTrue(tx.endsWith("\n"))

        val all = EspCompanionProtocol.encodeCanFilter(acceptAll = true)
        assertTrue(all.contains("\"t\":\"canFilter\""))
        assertTrue(all.contains("\"acceptAll\":true"))

        val filt = EspCompanionProtocol.encodeCanFilter(
            listOf(CanFilterSpec(id = 0x280, mask = 0x7FF, ext = false)),
        )
        assertTrue(filt.contains("\"filters\""))
        assertTrue(filt.contains("0x280"))
        assertTrue(filt.contains("0x7FF"))
        assertTrue(EspCompanionProtocol.encodeCanBaud(500000).contains("\"baud\":500000"))
        assertTrue(EspCompanionProtocol.encodeCanLightBegin().contains("\"t\":\"canLightBegin\""))
        assertTrue(EspCompanionProtocol.encodeCanLightEnd().contains("\"t\":\"canLightEnd\""))
    }

    @Test
    fun parseCanAckAndBaud() {
        val ack = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"canAck","phase":"lightBegin","ok":true}""",
        )
        assertTrue(ack is EspMessage.CanAck)
        assertEquals("lightBegin", (ack as EspMessage.CanAck).phase)
        assertTrue(ack.ok)

        val baud = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"canBaud","baud":250000,"ok":true}""",
        ) as EspMessage.CanBaud
        assertEquals(250000, baud.baud)
        assertTrue(baud.ok)
    }

    @Test
    fun canLightFrameRoundtrip() {
        val frame = CanFrame(
            id = 0x18DAF110,
            ext = true,
            rtr = false,
            data = byteArrayOf(0x02, 0x10.toByte(), 0x03),
            tx = false,
        )
        val payload = EspCompanionProtocol.encodeCanLightFrames(listOf(frame), tx = false)
        assertEquals(EspCompanionProtocol.CAN_LIGHT_FRAME_LEN, payload.size)
        val decoded = EspCompanionProtocol.decodeCanLightPayload(payload)
        assertEquals(1, decoded.size)
        assertEquals(frame.id, decoded[0].id)
        assertTrue(decoded[0].ext)
        assertFalse(decoded[0].rtr)
        assertFalse(decoded[0].tx)
        assertTrue(frame.data.contentEquals(decoded[0].data))
        assertEquals(3, decoded[0].dlc)
    }

    @Test
    fun canLightOtaFrameRoundtrip() {
        val frame = CanFrame(
            id = 0x280,
            ext = false,
            rtr = false,
            data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            tx = true,
        )
        val records = EspCompanionProtocol.encodeCanLightFrames(listOf(frame), tx = true)
        val ota = EspCompanionProtocol.encodeOtaChunkFrame(records)
        val decoder = EspOtaFrameDecoder()
        val payloads = decoder.push(ota)
        assertEquals(1, payloads.size)
        val decoded = EspCompanionProtocol.decodeCanLightPayload(payloads.single())
        assertEquals(1, decoded.size)
        assertEquals(0x280, decoded[0].id)
        assertTrue(decoded[0].tx)
        assertTrue(frame.data.contentEquals(decoded[0].data))
    }

    @Test
    fun otaDecoderHandlesSplitChunks() {
        val records = EspCompanionProtocol.encodeCanLightFrames(
            listOf(CanFrame(id = 0x123, ext = false, data = byteArrayOf(0xAA.toByte()))),
            tx = false,
        )
        val ota = EspCompanionProtocol.encodeOtaChunkFrame(records)
        val decoder = EspOtaFrameDecoder()
        val mid = 6
        assertTrue(decoder.push(ota.copyOfRange(0, mid)).isEmpty())
        val rest = decoder.push(ota.copyOfRange(mid, ota.size))
        assertEquals(1, rest.size)
        val decoded = EspCompanionProtocol.decodeCanLightPayload(rest.single())
        assertEquals(0x123, decoded.single().id)
        assertEquals(1, decoded.single().dlc)
    }

    @Test
    fun parseHexIdAndData() {
        assertEquals(0x280, EspCompanionProtocol.parseHexId("280"))
        assertEquals(0x280, EspCompanionProtocol.parseHexId("0x280"))
        assertEquals(0x18DAF110, EspCompanionProtocol.parseHexId("18DAF110"))
        assertNull(EspCompanionProtocol.parseHexId(""))
        val data = EspCompanionProtocol.parseHexData("11 22:33-44")
        assertNotNull(data)
        assertEquals(4, data!!.size)
        assertEquals(0x11.toByte(), data[0])
        assertEquals(0x44.toByte(), data[3])
        assertTrue(EspCompanionProtocol.parseHexData("")!!.isEmpty())
        assertNull(EspCompanionProtocol.parseHexData("1"))
    }
}
