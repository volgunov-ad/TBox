package vad.dashing.rootsvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.PmListParser

class PeerPackagesTest {
    @Test
    fun parseMatchesExactUid() {
        val text = """
            package:vad.dashing.tbox uid:10123
            package:vad.dashing.camd uid:10124 user:0
            package:other.pkg uid:1012
            package:vad.dashing.rootsvc uid:10123
        """.trimIndent()
        val pkgs = PmListParser.forUid(text, 10123)
        assertEquals(listOf("vad.dashing.tbox", "vad.dashing.rootsvc"), pkgs)
    }

    @Test
    fun parseRejectsUidPrefix() {
        val text = "package:spoof uid:101230\npackage:ok uid:10123\n"
        val pkgs = PmListParser.forUid(text, 10123)
        assertEquals(listOf("ok"), pkgs)
    }

    @Test
    fun parseEmptyAndNegative() {
        assertTrue(PmListParser.forUid("", 1).isEmpty())
        assertTrue(PmListParser.forUid(null, 1).isEmpty())
        assertTrue(PmListParser.forUid("package:x uid:1", -1).isEmpty())
    }
}
