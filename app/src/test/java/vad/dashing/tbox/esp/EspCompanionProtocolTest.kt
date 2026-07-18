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
            """{"v":1,"t":"hello","fw":"0.1.0","gpioIn":8,"relays":4,"um980":true}"""
        )
        assertTrue(msg is EspMessage.Hello)
        val hello = msg as EspMessage.Hello
        assertEquals("0.1.0", hello.fw)
        assertEquals(8, hello.gpioInCount)
        assertEquals(4, hello.relayCount)
        assertTrue(hello.um980)
    }

    @Test
    fun parseGpsAndMapToLocValues() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"gps","fix":1,"lat":55.75,"lon":37.61,"alt":150.2,"speedKmh":42.0,"course":180.5,"satsUsed":14,"satsVis":28,"utc":"2026-07-18T12:00:00Z"}"""
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
        assertNotNull(loc.utcTime)
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
    fun gpsWithoutFix() {
        val msg = EspCompanionProtocol.parseLine(
            """{"v":1,"t":"gps","fix":0,"lat":0,"lon":0,"alt":0,"speedKmh":0,"course":0,"satsUsed":0,"satsVis":0,"utc":""}"""
        ) as EspMessage.Gps
        val loc = EspCompanionProtocol.gpsToLocValues(msg)
        assertFalse(loc.locateStatus)
    }
}
