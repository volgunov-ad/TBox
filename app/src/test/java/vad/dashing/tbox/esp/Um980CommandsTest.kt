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
    fun maxPrecisionPresetSafeDefaultsAndNoSignalGroupInBatch() {
        val cmds = Um980Commands.maxPrecisionPresetCommands()
        assertFalse(cmds.any { it.contains("COM3", ignoreCase = true) })
        assertFalse(cmds.any { it.contains("SIGNALGROUP", ignoreCase = true) })
        assertEquals("SAVECONFIG", cmds.last())
        assertTrue(cmds.contains("MASK 5"))
        assertTrue(cmds.contains("CONFIG SBAS DISABLE"))
        assertTrue(cmds.contains("GPGST 0"))
        assertTrue(cmds.contains("CONFIG SMOOTH RTKHEIGHT 0"))
        assertFalse(cmds.contains("GPGST 1"))
        assertTrue(cmds.contains("GPGGA 0.5"))
        assertTrue(cmds.contains("GPRMC 0.5"))
        assertTrue(cmds.contains("CONFIG DGPS TIMEOUT 300"))
        assertTrue(cmds.contains("CONFIG RTK TIMEOUT 300"))
        assertTrue(cmds.contains("CONFIG RTK USER_DEFAULTS"))
        assertTrue(cmds.contains("CONFIG RTK MMPL 0"))
        assertTrue(cmds.contains("CONFIG RTK RELIABILITY 3 3"))
        assertTrue(cmds.contains("CONFIG STANDALONE ENABLE 100"))
        assertTrue(cmds.contains("MODE ROVER"))
        assertTrue(cmds.contains("CONFIG PPP ENABLE E6-HAS"))
        assertTrue(cmds.contains("CONFIG ANTIJAM AUTO"))
        assertTrue(cmds.contains("CONFIG PVTALG AUTO"))
    }

    @Test
    fun maxAntispoofPresetCommands() {
        val cmds = Um980Commands.maxAntispoofPresetCommands()
        assertFalse(cmds.any { it.contains("SIGNALGROUP", ignoreCase = true) })
        assertEquals("SAVECONFIG", cmds.last())
        assertTrue(cmds.contains("MASK 5"))
        assertTrue(cmds.contains("CONFIG SBAS DISABLE"))
        assertTrue(cmds.contains("GPGST 0"))
        assertTrue(cmds.contains("CONFIG SMOOTH RTKHEIGHT 0"))
        assertTrue(cmds.contains("CONFIG DGPS TIMEOUT 600"))
        assertTrue(cmds.contains("CONFIG RTK TIMEOUT 0"))
        assertTrue(cmds.contains("CONFIG RTK DISABLE"))
        assertTrue(cmds.contains("CONFIG SMOOTH PSRVEL ENABLE"))
        assertTrue(cmds.contains("CONFIG SMOOTH HEADING 5"))
        assertTrue(cmds.contains("CONFIG STANDALONE ENABLE 3"))
        assertTrue(cmds.contains("MODE ROVER AUTOMOTIVE"))
        assertTrue(cmds.contains("CONFIG PPP DISABLE"))
        assertTrue(cmds.contains("CONFIG ANTIJAM FORCE"))
        assertTrue(cmds.contains("CONFIG PVTALG MULTI"))
    }

    @Test
    fun presetPreviewAppendsSignalGroupNote() {
        val batch = Um980Commands.maxPrecisionPresetCommands()
        val preview = Um980Commands.presetPreviewLines(batch)
        assertEquals(batch.size + 1, preview.size)
        assertTrue(preview.last().contains("SIGNALGROUP 2"))
        assertTrue(preview.last().contains("12s"))
    }

    @Test
    fun comBaudCommandUsesCom3() {
        assertEquals("CONFIG COM3 460800", Um980Commands.comBaudCommand(460800))
        assertEquals("CONFIG COM3 115200", Um980Commands.comBaudCommand(115200))
    }

    @Test
    fun refreshSnapshotCommands() {
        assertEquals(
            listOf("CONFIG", "MODE", "MASK", "VERSIONA"),
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
                "CONFIG RTK RELIABILITY 3 3",
                "CONFIG RTK MMPL 0",
                "CONFIG STANDALONE ENABLE 100",
                "CONFIG MMP ENABLE",
                "CONFIG AGNSS ENABLE",
                "CONFIG ANTIJAM FORCE",
                "CONFIG SIGNALGROUP 2",
                "CONFIG PVTALG MULTI",
                "CONFIG SBAS ENABLE SDCM",
                "MASK 10",
                "CONFIG SMOOTH PSRVEL ENABLE",
                "CONFIG SMOOTH RTKHEIGHT 10",
                "CONFIG SMOOTH HEADING 5",
                "CONFIG PSRVELDRPOS ENABLE",
                "CONFIG VELSTDTHD DISABLE",
                "#VERSIONA,79,GPS,FINE,2326,378237000,15434,0,18,889;\"UM980\",\"R4.10Build15434\",\"HRPT00-S10C-P\"*769f",
            ),
        )
        assertEquals("AUTOMOTIVE", snap.mode)
        assertEquals(600, snap.dgpsTimeout)
        assertEquals(true, snap.rtkOff)
        assertEquals(0, snap.rtkTimeout)
        assertEquals(3, snap.rtkReliability)
        assertEquals(3, snap.rtkAdrReliability)
        assertEquals(0, snap.rtkMmpl)
        assertEquals(true, snap.standalone)
        assertEquals(100, snap.standaloneWaitSec)
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
        assertEquals(5, snap.smoothHeading)
        assertEquals(true, snap.psrVelDrPos)
        assertEquals(false, snap.velStdThdEnabled)
        assertEquals("UM980 R4.10Build15434", snap.um980Version)
    }

    @Test
    fun parseStandaloneEnableWithoutWaitAndDisable() {
        val on = Um980Commands.parseConfigSnapshot(listOf("CONFIG STANDALONE ENABLE"))
        assertEquals(true, on.standalone)
        assertNull(on.standaloneWaitSec)
        val off = Um980Commands.parseConfigSnapshot(listOf("CONFIG STANDALONE DISABLE"))
        assertEquals(false, off.standalone)
        assertNull(off.standaloneWaitSec)
        val wait = Um980Commands.parseConfigSnapshot(listOf("CONFIG STANDALONE ENABLE 3"))
        assertEquals(true, wait.standalone)
        assertEquals(3, wait.standaloneWaitSec)
        val timeout = Um980Commands.parseConfigSnapshot(
            listOf("CONFIG STANDALONE TIMEOUT 60", "CONFIG STANDALONE ENABLE 10"),
        )
        assertEquals(60, timeout.standaloneTimeout)
        assertEquals(10, timeout.standaloneWaitSec)
    }

    @Test
    fun formatVersionLineFromVersiona() {
        assertEquals(
            "UM982 R4.10Build15434",
            Um980Commands.formatVersionLine(
                "#VERSIONA,79,GPS,FINE,2326,378237000,15434,0,18,889;" +
                    "\"UM982\",\"R4.10Build15434\",\"HRPT00-S10C-P\"*769f",
            ),
        )
    }

    @Test
    fun formatVersionLineIgnoresCommandEcho() {
        assertEquals("", Um980Commands.formatVersionLine("VERSIONA"))
        assertEquals("", Um980Commands.formatVersionLine("versiona"))
        assertFalse(Um980Commands.isVersionaPayloadLine("VERSIONA"))
        assertTrue(
            Um980Commands.isVersionaPayloadLine(
                "#VERSIONA,79,GPS,FINE,1,2,3,4,5,6;\"UM980\",\"R4.10\"*00",
            ),
        )
        val snap = Um980Commands.parseConfigSnapshot(listOf("VERSIONA", "OK"))
        assertNull(snap.um980Version)
        val snap2 = Um980Commands.parseConfigSnapshot(
            listOf(
                "VERSIONA",
                "#VERSIONA,79,GPS,FINE,2326,378237000,15434,0,18,889;" +
                    "\"UM980\",\"R4.10Build15434\",\"HRPT00-S10C-P\"*769f",
                "OK",
            ),
        )
        assertEquals("UM980 R4.10Build15434", snap2.um980Version)
    }

    @Test
    fun formatVersionLineUnquotedFields() {
        assertEquals(
            "UM980 R4.10Build999",
            Um980Commands.formatVersionLine(
                "#VERSIONA,80,GPS,FINE,1,2,3,4,5,6;UM980,R4.10Build999,PN*00",
            ),
        )
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

    @Test
    fun parseConfigSnapshotPpp() {
        val snap = Um980Commands.parseConfigSnapshot(
            listOf(
                "CONFIG PPP ENABLE B2b-PPP",
                "CONFIG PPP TIMEOUT 120",
                "CONFIG PPP DATUM WGS84",
                "CONFIG PPPRTK ENABLE L6CLAS",
            ),
        )
        assertEquals("B2b-PPP", snap.pppMode)
        assertEquals(120, snap.pppTimeout)
        assertEquals("WGS84", snap.pppDatum)
    }

    @Test
    fun parseConfigSnapshotPppDisableAndIgnoresPppRtk() {
        val snap = Um980Commands.parseConfigSnapshot(
            listOf(
                "CONFIG PPPRTK ENABLE L6CLAS",
                "CONFIG PPP DISABLE",
                "CONFIG PPP DATUM PPPORIGINAL",
            ),
        )
        assertEquals("DISABLE", snap.pppMode)
        assertEquals("PPPORIGINAL", snap.pppDatum)
    }

    @Test
    fun parseSmoothHeadingDisable() {
        val snap = Um980Commands.parseConfigSnapshot(
            listOf("CONFIG SMOOTH HEADING DISABLE", "CONFIG SMOOTH RTKHEIGHT DISABLE"),
        )
        assertEquals(0, snap.smoothHeading)
        assertEquals(0, snap.smoothRtkHeight)
    }
}
