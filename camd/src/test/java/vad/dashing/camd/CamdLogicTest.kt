package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowXmlTest {
    @Test
    fun buildsTheWindowThatWasProvenOnInput0() {
        val xml = WindowXml.build(
            input = KnownInputs.byId(0)!!,
            x = 0.02f,
            y = 0.70f,
            w = 0.28f,
            h = 0.28f
        )
        assertTrue(xml.contains("input_id=\"0\""))
        assertTrue(xml.contains("window_pos_x=\"0.0200\""))
        assertTrue(xml.contains("window_pos_y=\"0.7000\""))
        assertTrue(xml.contains("window_width=\"0.2800\""))
        assertTrue(xml.contains("window_height=\"0.2800\""))
        assertTrue(xml.contains("width=\"1488\" height=\"616\""))
        assertFalse(xml.contains("\r"))
    }
}

class CamdCommandTest {
    @Test
    fun parsesOpenCloseAndStatus() {
        val open = CamdCommands.parse("OPEN 0 0.0200 0.7000 0.2800 0.2800")
        assertEquals(CamdCommand.Open(0, 0.02f, 0.70f, 0.28f, 0.28f), open)
        assertEquals(CamdCommand.CloseAll, CamdCommands.parse("close"))
        assertEquals(CamdCommand.Close(4), CamdCommands.parse("CLOSE 4"))
        assertEquals(CamdCommand.Status, CamdCommands.parse("STATUS"))
    }

    @Test
    fun rejectsUnknownInputAndAWindowThatLeavesTheScreen() {
        assertNull(CamdCommands.parse("OPEN 2 0.1 0.1 0.2 0.2"))
        assertNull(CamdCommands.parse("OPEN 0 0.9 0.1 0.3 0.2"))
        assertNull(CamdCommands.parse("OPEN 0 0 0 0.01 0.2"))
        assertNull(CamdCommands.parse("MOVE 0"))
    }
}

class DaemonScriptTest {
    @Test
    fun scriptsAreLfAndLaunchQcarcamFromTheAppXml() {
        val daemon = DaemonScripts.daemon()
        assertFalse(daemon.contains("\r"))
        assertTrue(daemon.startsWith("#!/system/bin/sh\n"))
        assertTrue(daemon.contains("PIDFILE=/data/local/tmp/camd.pid"))
        assertTrue(daemon.contains("echo \$\$ >"))
        assertTrue(daemon.contains("/system/bin/qcarcam_test -config=\"\$XML\" -seconds=86400 -nonInteractive"))
        assertTrue(daemon.contains(DaemonScripts.XML_FILE))

        val arm = DaemonScripts.arm("nonce42")
        assertFalse(arm.contains("\r"))
        assertTrue(arm.contains("already nonce42"))
        assertTrue(arm.contains(DaemonScripts.DAEMON_FILE))
    }
}

class ProbeScriptTest {
    @Test
    fun keepAvmScriptDoesNotStopTheServiceAndLogsFirst() {
        val script = ProbeScripts.body(false, KnownInputs.byId(4)!!, "nonce1")
        assertFalse(script.contains("\r"))
        assertFalse(script.contains("ctl.stop"))
        assertTrue(script.contains("variant=nostop input=4"))
        assertTrue(script.contains("/sdcard/Download/camd-probe-nostop-4-"))
        assertTrue(script.contains("-seconds=20"))
        assertTrue(script.contains("input_id=\"4\""))
        assertTrue(script.contains("logcat -s qcarcam_test:V"))
        assertTrue(script.indexOf("variant=nostop") < script.indexOf("qcarcam_test -config"))
    }

    @Test
    fun stopAvmScriptWritesTheLogBeforeStoppingAndRestartsAfter() {
        val script = ProbeScripts.body(true, KnownInputs.byId(0)!!, "nonce2")
        assertTrue(script.contains("setprop ctl.stop AVMapp"))
        assertTrue(script.contains("setprop ctl.start AVMapp"))
        assertTrue(script.contains("variant=stopavm input=0"))
        assertTrue(script.contains("nonce2"))
        assertTrue(script.indexOf("variant=stopavm") < script.indexOf("ctl.stop"))
        assertTrue(script.contains("kill -9"))
        assertTrue(script.indexOf("qcarcam_exit=killed") < script.indexOf("ctl.start"))
    }

    @Test
    fun launcherReturnsWithoutWaitingForTheProbe() {
        val launcher = ProbeScripts.launcher(ProbeScripts.bodyPath("nonce1"))
        assertTrue(launcher.contains("sh /data/data/vad.dashing.camd/files/camd-probe-nonce1.sh"))
        assertTrue(launcher.contains("&"))
        assertTrue(launcher.trimEnd().endsWith("exit 0"))
    }
}

class GrabberScriptTest {
    @Test
    fun launcherStopsAvmBeforeTheGrabberAndReturns() {
        val text = GrabberScripts.launcher("n1", stopAvm = true)
        val stop = text.indexOf("setprop ctl.stop AVMapp")
        val process = text.indexOf("camd_grab")
        assertTrue(stop >= 0)
        assertTrue(process > stop)
        assertTrue(text.contains("appops set vad.dashing.camd SYSTEM_ALERT_WINDOW allow"))
        assertTrue(text.indexOf("SYSTEM_ALERT_WINDOW") < process)
        assertFalse(text.contains("\r"))
        assertTrue(text.trimEnd().endsWith("exit 0"))
    }

    @Test
    fun launcherKeepsAvmWhenTheCameraIsNotSurround() {
        val text = GrabberScripts.launcher("n2", stopAvm = false)
        assertFalse(text.contains("ctl.stop"))
        assertTrue(text.contains("exit 0"))
    }

    @Test
    fun startAvmWritesTheNonceBeforeExit() {
        val text = GrabberScripts.startAvm("nonce9")
        val echo = text.indexOf("echo nonce9")
        val exit = text.indexOf("exit 0")
        assertTrue(echo >= 0)
        assertTrue(exit > echo)
        assertTrue(text.contains("ctl.start AVMapp"))
    }

    @Test
    fun grantOverlayAllowsTheWindowBeforeExit() {
        val text = GrabberScripts.grantOverlay("nonce3")
        val grant = text.indexOf("appops set vad.dashing.camd SYSTEM_ALERT_WINDOW allow")
        val exit = text.indexOf("exit 0")
        assertTrue(grant >= 0)
        assertTrue(exit > grant)
        assertTrue(text.contains("echo nonce3"))
    }

    @Test
    fun stopAvmDoesNotRestartTheGrabber() {
        val text = GrabberScripts.stopAvm("nonce4")
        assertTrue(text.contains("setprop ctl.stop AVMapp"))
        assertFalse(text.contains("camd_grab"))
        assertTrue(text.trimEnd().endsWith("exit 0"))
    }
}

class CamdPortTest {
    @Test
    fun apiPortDoesNotCollideWithMonitorOrMqtt() {
        assertTrue(DaemonScripts.API_PORT != 8765)
        assertTrue(DaemonScripts.API_PORT != 8766)
    }
}

class CamdApiReplyTest {
    @Test
    fun statusDoesNotNeedTheHeadUnit() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val server = CamdApiServer(engine)
        assertEquals("ERR bad_command", server.reply("NOPE"))
        assertTrue(server.reply("STATUS").startsWith("OK "))
    }
}
