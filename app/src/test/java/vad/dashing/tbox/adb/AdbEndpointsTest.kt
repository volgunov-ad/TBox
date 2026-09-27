package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbEndpointsTest {

    @Test
    fun matches_sameHostPort() {
        assertTrue(AdbEndpoints.matches("127.0.0.1:5555", "127.0.0.1", 5555))
        assertTrue(AdbEndpoints.matches("localhost:5555", "127.0.0.1", 5555))
        assertTrue(AdbEndpoints.matches("127.0.0.1:5555", "localhost", 5555))
        assertTrue(AdbEndpoints.matches("::1:5555", "127.0.0.1", 5555))
    }

    @Test
    fun matches_rejectsDifferentPortOrHost() {
        assertFalse(AdbEndpoints.matches("127.0.0.1:5555", "127.0.0.1", 5556))
        assertFalse(AdbEndpoints.matches("192.168.1.10:5555", "127.0.0.1", 5555))
        assertFalse(AdbEndpoints.matches("not-an-endpoint", "127.0.0.1", 5555))
    }

    @Test
    fun format_and_parse_roundTrip() {
        assertEquals("127.0.0.1:5555", AdbEndpoints.format("127.0.0.1", 5555))
        val parsed = AdbEndpoints.parse(" 192.168.0.5:5037 ")
        assertEquals("192.168.0.5", parsed!!.host)
        assertEquals(5037, parsed.port)
    }
}
