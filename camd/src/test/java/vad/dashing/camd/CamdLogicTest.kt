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
        assertEquals(CamdCommand.Close, CamdCommands.parse("close"))
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

class CamdApiReplyTest {
    @Test
    fun statusDoesNotNeedTheHeadUnit() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val server = CamdApiServer(engine)
        assertEquals("ERR bad_command", server.reply("NOPE"))
        assertTrue(server.reply("STATUS").startsWith("OK "))
    }
}
