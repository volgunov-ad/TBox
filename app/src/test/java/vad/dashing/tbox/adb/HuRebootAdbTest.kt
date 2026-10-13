package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuRebootAdbTest {
    @Test
    fun commandEscalatesWithSuAndDetachesReboot() {
        val cmd = HuRebootAdb.SHELL_COMMAND
        assertTrue(cmd.contains("setsid su 0 sh -c 'sleep 1; svc power reboot'"))
        assertTrue(cmd.contains("&"))
    }

    @Test
    fun parsesMarkers() {
        assertEquals(HuRebootResult.Sent, HuRebootAdb.parseOutput("TBOX_REBOOT_OK\n"))
        assertEquals(HuRebootResult.NoSu, HuRebootAdb.parseOutput("TBOX_REBOOT_ERR nosu"))
        assertEquals(HuRebootResult.Failed("boom"), HuRebootAdb.parseOutput(" boom \n"))
    }
}
