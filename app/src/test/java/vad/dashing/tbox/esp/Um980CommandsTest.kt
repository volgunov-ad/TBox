package vad.dashing.tbox.esp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Um980CommandsTest {

    @Test
    fun periodSecondsToNmeaRateMapping() {
        assertEquals("0", Um980Commands.periodSecondsToNmeaRate(0.0))
        assertEquals("0.5", Um980Commands.periodSecondsToNmeaRate(0.5))
        assertEquals("1", Um980Commands.periodSecondsToNmeaRate(1.0))
        assertEquals("2", Um980Commands.periodSecondsToNmeaRate(2.0))
    }

    @Test
    fun ggaRmcCommandsUseSameRate() {
        assertEquals(listOf("GPGGA 0.5", "GPRMC 0.5"), Um980Commands.ggaRmcCommands(0.5))
        assertEquals(listOf("GPGGA 0", "GPRMC 0"), Um980Commands.ggaRmcCommands(0.0))
    }

    @Test
    fun gpsGuideProfileHasNoBaudAndEndsWithSave() {
        val cmds = Um980Commands.gpsGuideProfileCommands()
        assertFalse(cmds.any { it.contains("COM3", ignoreCase = true) })
        assertFalse(cmds.any { it.contains("BAUD", ignoreCase = true) })
        assertEquals("SAVECONFIG", cmds.last())
        assertTrue(cmds.contains("GPGGA 0.5"))
        assertTrue(cmds.contains("GPRMC 0.5"))
        assertTrue(cmds.contains("MODE ROVER AUTOMOTIVE"))
    }

    @Test
    fun parseConfigSnapshot() {
        val snap = Um980Commands.parseConfigSnapshot(
            listOf(
                "MODE ROVER AUTOMOTIVE",
                "CONFIG DGPS TIMEOUT 600",
                "CONFIG RTK OFF",
                "CONFIG RTK TIMEOUT 0",
                "CONFIG STANDALONE ENABLE",
                "CONFIG MMP ENABLE",
                "CONFIG AGNSS ENABLE",
                "CONFIG ANTIJAM FORCE",
                "CONFIG SIGNALGROUP 2",
                "CONFIG PVTALG MULTI",
            ),
        )
        assertEquals("AUTOMOTIVE", snap.mode)
        assertEquals(600, snap.dgpsTimeout)
        assertEquals(true, snap.rtkOff)
        assertEquals(0, snap.rtkTimeout)
        assertEquals(true, snap.standalone)
        assertEquals(true, snap.mmp)
        assertEquals(true, snap.agnss)
        assertEquals(true, snap.antijamForce)
        assertEquals(2, snap.signalGroup)
        assertEquals(true, snap.pvtAlgMulti)
    }
}
