package vad.dashing.tbox.esp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertFalse(cmds.any { it.contains("INS RESET", ignoreCase = true) })
        assertFalse(cmds.any { it.contains("RTK OFF", ignoreCase = true) })
        assertFalse(cmds.any { it.contains("RTK ENABLE", ignoreCase = true) })
        assertEquals("SAVECONFIG", cmds.last())
        assertTrue(cmds.contains("GPGGA 0.5"))
        assertTrue(cmds.contains("GPRMC 0.5"))
        assertTrue(cmds.contains("MODE ROVER AUTOMOTIVE"))
        assertTrue(cmds.contains("CONFIG RTK TIMEOUT 0"))
        assertTrue(cmds.contains("CONFIG PVTALG MULTI"))
        assertTrue(cmds.contains("MASK 10"))
        assertTrue(cmds.contains("CONFIG SBAS ENABLE AUTO"))
        assertTrue(cmds.contains("CONFIG STANDALONE TIMEOUT 86400"))
        assertTrue(cmds.contains("CONFIG SMOOTH PSRVEL ENABLE"))
        assertTrue(cmds.contains("CONFIG SMOOTH RTKHEIGHT 10"))
        assertTrue(cmds.contains("CONFIG PSRVELDRPOS ENABLE"))
    }

    @Test
    fun refreshSnapshotCommands() {
        assertEquals(
            listOf("CONFIG", "MODE", "MASK", "VERSION"),
            Um980Commands.refreshSnapshotCommands(),
        )
    }

    @Test
    fun parseConfigSnapshot() {
        val snap = Um980Commands.parseConfigSnapshot(
            listOf(
                "MODE ROVER AUTOMOTIVE",
                "CONFIG DGPS TIMEOUT 600",
                "CONFIG RTK TIMEOUT 0",
                "CONFIG RTK RELIABILITY 3",
                "CONFIG STANDALONE ENABLE",
                "CONFIG STANDALONE TIMEOUT 86400",
                "CONFIG MMP ENABLE",
                "CONFIG AGNSS ENABLE",
                "CONFIG ANTIJAM FORCE",
                "CONFIG SIGNALGROUP 2",
                "CONFIG PVTALG MULTI",
                "CONFIG SBAS ENABLE SDCM",
                "MASK 10",
                "CONFIG SMOOTH PSRVEL ENABLE",
                "CONFIG SMOOTH RTKHEIGHT 10",
                "CONFIG PSRVELDRPOS ENABLE",
                "CONFIG VELSTDTHD DISABLE",
                "#VERSION,UM980,BUILD_1",
            ),
        )
        assertEquals("AUTOMOTIVE", snap.mode)
        assertEquals(600, snap.dgpsTimeout)
        assertEquals(true, snap.rtkOff)
        assertEquals(0, snap.rtkTimeout)
        assertEquals(3, snap.rtkReliability)
        assertEquals(true, snap.standalone)
        assertEquals(86400, snap.standaloneTimeout)
        assertEquals(true, snap.mmp)
        assertEquals(true, snap.agnss)
        assertEquals("FORCE", snap.antijamMode)
        assertEquals(true, snap.antijamForce)
        assertEquals(2, snap.signalGroup)
        assertEquals("MULTI", snap.pvtAlg)
        assertEquals(true, snap.pvtAlgMulti)
        assertEquals("SDCM", snap.sbasMode)
        assertEquals(10, snap.maskElevation)
        assertEquals(true, snap.smoothPsrVel)
        assertEquals(10, snap.smoothRtkHeight)
        assertEquals(true, snap.psrVelDrPos)
        assertEquals(false, snap.velStdThdEnabled)
        assertTrue(snap.um980Version!!.contains("UM980"))
    }

    @Test
    fun parseConfigSnapshotRtkDisableAndPvtAuto() {
        val snap = Um980Commands.parseConfigSnapshot(
            listOf(
                "CONFIG RTK DISABLE",
                "CONFIG PVTALG AUTO",
                "CONFIG ANTIJAM AUTO",
                "CONFIG SBAS DISABLE",
                "CONFIG ANTIJAM DISABLE",
            ),
        )
        assertEquals(true, snap.rtkOff)
        assertEquals("AUTO", snap.pvtAlg)
        assertEquals(false, snap.pvtAlgMulti)
        // last ANTIJAM wins
        assertEquals("DISABLE", snap.antijamMode)
        assertEquals(false, snap.antijamForce)
        assertEquals("DISABLE", snap.sbasMode)
    }

    @Test
    fun parseConfigSnapshotSignalGroup8AndPvtSingle() {
        val snap = Um980Commands.parseConfigSnapshot(
            listOf(
                "CONFIG SIGNALGROUP 8",
                "CONFIG PVTALG SINGLE",
                "\$CONFIG,MASK,MASK 5.000000*3C",
            ),
        )
        assertEquals(8, snap.signalGroup)
        assertEquals("SINGLE", snap.pvtAlg)
        assertEquals(false, snap.pvtAlgMulti)
        assertEquals(5, snap.maskElevation)
        assertNull(snap.antijamMode)
    }
}
