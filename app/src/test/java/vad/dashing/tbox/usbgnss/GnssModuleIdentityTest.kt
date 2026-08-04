package vad.dashing.tbox.usbgnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssModuleIdentityTest {

    @Test
    fun parseVersiona_um980() {
        val line =
            "#VERSIONA,80,GPS,FINE,2210,306385000,0,0,18,22;" +
                "\"UM980\",\"R4.10Build13789\",\"HRPT00B000GNCP1\",\"xxx\",\"xxx\"" +
                "*hh"
        val id = GnssModuleCommands.parseProbeReplies(listOf(line))
        requireNotNull(id)
        assertEquals(GnssModuleFamily.UNICORE, id.family)
        assertTrue(id.isUm980)
        assertTrue(id.versionLabel.contains("UM980"))
    }

    @Test
    fun parsePmtk705_mediatek() {
        val id = GnssModuleCommands.parseProbeReplies(
            listOf("\$PMTK705,AXN_5.1.8,0016,*3E"),
        )
        requireNotNull(id)
        assertEquals(GnssModuleFamily.MEDIATEK, id.family)
        assertTrue(id.isKnown)
    }

    @Test
    fun parseEmpty_null() {
        assertNull(GnssModuleCommands.parseProbeReplies(listOf("\$GPGGA,...")))
    }

    @Test
    fun softRebootAscii_byFamily() {
        assertEquals("RESET", GnssModuleCommands.softRebootAscii(GnssModuleFamily.UNICORE))
        assertEquals("\$PMTK101*32", GnssModuleCommands.softRebootAscii(GnssModuleFamily.MEDIATEK))
        assertNull(GnssModuleCommands.softRebootAscii(GnssModuleFamily.UBLOX))
        assertNull(GnssModuleCommands.softRebootAscii(GnssModuleFamily.UNKNOWN))
    }

    @Test
    fun codec_roundTrip_andMigrate() {
        val a = GnssModuleIdentity(
            family = GnssModuleFamily.UNICORE,
            model = "UM980",
            versionLabel = "UM980 R4",
            probedAtMs = 42L,
        )
        val encoded = GnssModuleIdentityCodec.encodeMap(mapOf("10c4:ea60" to a))
        val decoded = GnssModuleIdentityCodec.decodeMap(encoded)
        assertEquals(a, decoded["10c4:ea60"])

        val migrated = GnssModuleIdentityCodec.migrateStableId(
            decoded,
            fromId = "10c4:ea60",
            toId = "10c4:ea60:ABC",
        )
        assertFalse(migrated.containsKey("10c4:ea60"))
        assertEquals(a, migrated["10c4:ea60:ABC"])
    }

    @Test
    fun shouldAutoProbe_onlyWhenMissing() {
        val map = mapOf(
            "a" to GnssModuleIdentity.unknown(1L),
        )
        assertTrue(GnssModuleProbe.shouldAutoProbe("b", map))
        assertFalse(GnssModuleProbe.shouldAutoProbe("a", map))
        assertFalse(GnssModuleProbe.shouldAutoProbe("", map))
    }

    @Test
    fun ubloxFrame_hasHeaderAndChecksumLength() {
        val frame = GnssModuleCommands.ubloxMonVerPollBytes()
        assertEquals(0xB5.toByte(), frame[0])
        assertEquals(0x62.toByte(), frame[1])
        assertEquals(8, frame.size) // header2 + class/id/len + ck
    }
}
