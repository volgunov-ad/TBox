package vad.dashing.tbox.hotspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EspSoftApIdentityTest {
    @Test
    fun randomSsidAndPskStayInsideWifiLimits() {
        repeat(20) {
            val ssid = EspSoftApIdentity.randomSsid()
            val psk = EspSoftApIdentity.randomPsk()
            assertTrue(ssid.matches(Regex("TBox-[0-9a-f]{4}")))
            assertEquals(10, psk.length)
            assertTrue(EspSoftApIdentity.isValidSsid(ssid))
            assertTrue(EspSoftApIdentity.isValidPsk(psk))
            assertTrue(psk.all { it in "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        }
    }

    @Test
    fun rejectsQuotesBackslashAndShortPassword() {
        assertFalse(EspSoftApIdentity.isValidSsid(""))
        assertFalse(EspSoftApIdentity.isValidSsid("a".repeat(33)))
        assertFalse(EspSoftApIdentity.isValidSsid("TBox\"x"))
        assertFalse(EspSoftApIdentity.isValidPsk("short"))
        assertFalse(EspSoftApIdentity.isValidPsk("tbox\"8765"))
        assertFalse(EspSoftApIdentity.isValidPsk("tbox\\8765"))
        assertTrue(EspSoftApIdentity.isValidSsid("Cabin"))
        assertTrue(EspSoftApIdentity.isValidPsk("cabin8765"))
    }
}
