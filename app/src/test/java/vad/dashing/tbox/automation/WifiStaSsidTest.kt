package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiStaSsidTest {
    @Test
    fun normalize_stripsQuotesAndUnknown() {
        assertEquals("home", WifiStaSsid.normalize("\"home\""))
        assertEquals("home", WifiStaSsid.normalize("  home  "))
        assertNull(WifiStaSsid.normalize(null))
        assertNull(WifiStaSsid.normalize(""))
        assertNull(WifiStaSsid.normalize("\"\""))
        assertNull(WifiStaSsid.normalize("<unknown ssid>"))
        assertNull(WifiStaSsid.normalize("none"))
    }

    @Test
    fun matches_ignoresQuotesAndCase() {
        assertTrue(WifiStaSsid.matches("\"Home\"", "home"))
        assertFalse(WifiStaSsid.matches("home", "office"))
        assertFalse(WifiStaSsid.matches(null, "home"))
    }

    @Test
    fun findSavedNetworkId_returnsFirstMatch() {
        val networks = listOf(
            3 to "\"office\"",
            7 to "\"Home\"",
            9 to "\"home\"",
        )
        assertEquals(7, WifiStaSsid.findSavedNetworkId(networks, "home"))
        assertNull(WifiStaSsid.findSavedNetworkId(networks, "guest"))
        assertNull(WifiStaSsid.findSavedNetworkId(networks, "none"))
    }

    @Test
    fun uniqueSsids_dropsEmptyAndDedupes() {
        assertEquals(
            listOf("home", "office"),
            WifiStaSsid.uniqueSsids(listOf("\"home\"", "HOME", "\"office\"", "<unknown ssid>", "")),
        )
    }

    @Test
    fun snapshot_publishesExplicitOffAndNone() {
        val off = WifiStaSnapshot(radioEnabled = false, associated = false, ssid = null)
        assertEquals("off", off.radioState())
        assertEquals("off", off.associatedState())
        assertEquals(WifiStaSsid.NONE, off.ssidState())

        val associated = WifiStaSnapshot(radioEnabled = true, associated = true, ssid = "home")
        assertEquals("on", associated.radioState())
        assertEquals("on", associated.associatedState())
        assertEquals("home", associated.ssidState())
    }
}
