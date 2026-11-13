package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamdCommandTest {
    @Test
    fun parsesOpenCloseAndStatus() {
        val open = CamdCommands.parse("OPEN 0 0.0200 0.7000 0.2800 0.2800")
        assertEquals(CamdCommand.Open(0, 0.02f, 0.70f, 0.28f, 0.28f), open)
        assertEquals(
            CamdCommand.Open(4, 0.02f, 0.55f, 0.40f, 0.40f, 90),
            CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 90"),
        )
        assertNull(CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 45"))
        assertEquals(CamdCommand.CloseAll, CamdCommands.parse("close"))
        assertEquals(CamdCommand.Close(4), CamdCommands.parse("CLOSE 4"))
        assertEquals(CamdCommand.Status, CamdCommands.parse("STATUS"))
        assertEquals(CamdCommand.SetKeepAvm(true), CamdCommands.parse("AVM ON"))
        assertEquals(CamdCommand.SetKeepAvm(false), CamdCommands.parse("avm off"))
        assertNull(CamdCommands.parse("AVM"))
    }

    @Test
    fun parsesOpenWithOptionalFlips_6to9Tokens() {
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = false, flipV = false),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = false, flipV = false),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = true, flipV = false),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 1"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 90, flipH = true, flipV = true),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 90 1 1"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = false, flipV = true),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 0 1"),
        )
        // Flip without rotation slot is not a valid 7-token line (7th must be rot).
        assertNull(CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 1"))
        assertNull(CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 2"))
        assertNull(CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 1 1 0"))
    }

    @Test
    fun openDefaultsHaveNoFlip() {
        val open = CamdCommands.parse("OPEN 0 0.1 0.1 0.2 0.2") as CamdCommand.Open
        assertFalse(open.flipH)
        assertFalse(open.flipV)
        assertEquals(0, open.rotation)
    }

    @Test
    fun rejectsUnknownInputAndAWindowThatLeavesTheScreen() {
        assertNull(CamdCommands.parse("OPEN 2 0.1 0.1 0.2 0.2"))
        assertNull(CamdCommands.parse("OPEN 0 0.9 0.1 0.3 0.2"))
        assertNull(CamdCommands.parse("OPEN 0 0 0 0.01 0.2"))
        assertNull(CamdCommands.parse("MOVE 0"))
    }
}

class CamdLensCorrectionTest {
    @Test
    fun clampAndIdentity() {
        assertTrue(CamdLensCorrection.IDENTITY.isIdentity())
        assertFalse(CamdLensCorrection.PRESET_MILD_BARREL.isIdentity())
        val clamped = CamdLensCorrection.clamp(
            CamdLensCorrection(k1 = 5f, k2 = -5f, cx = 1f, cy = -1f),
        )
        assertEquals(1f, clamped.k1, 0f)
        assertEquals(-1f, clamped.k2, 0f)
        assertEquals(0.25f, clamped.cx, 0f)
        assertEquals(-0.25f, clamped.cy, 0f)
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

    @Test
    fun launcherStampsTheGrabberVersion() {
        val text = GrabberScripts.launcher("n5", stopAvm = false)
        val copy = text.indexOf("cp ")
        val stamp = text.indexOf("echo ${GrabberScripts.GRAB_VERSION} > ${GrabberScripts.VERSION_FILE}")
        val launch = text.indexOf("camd_grab > ")
        assertTrue(copy >= 0)
        assertTrue(stamp > copy)
        assertTrue(launch > stamp)
        assertTrue(text.contains("chmod 666 ${GrabberScripts.VERSION_FILE}"))
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

class PropCacheTest {
    @Test
    fun cachesWithinTtlReloadsAfterTtlAndOnInvalidate() {
        var now = 0L
        var loads = 0
        val cache = PropCache(ttlMs = 1_000, clock = { now })
        assertEquals("running", cache.get("init.svc.AVMapp") { loads += 1; "running" })
        assertEquals(1, loads)
        now = 500
        assertEquals("running", cache.get("init.svc.AVMapp") { loads += 1; "stopped" })
        assertEquals(1, loads)
        now = 1_000
        assertEquals("stopped", cache.get("init.svc.AVMapp") { loads += 1; "stopped" })
        assertEquals(2, loads)
        cache.invalidate()
        assertEquals("fresh", cache.get("init.svc.AVMapp") { loads += 1; "fresh" })
        assertEquals(3, loads)
    }

    @Test
    fun differentNamesAreCachedSeparately() {
        var now = 0L
        val cache = PropCache(ttlMs = 1_000, clock = { now })
        assertEquals("a", cache.get("p1") { "a" })
        assertEquals("b", cache.get("p2") { "b" })
        now = 100
        assertEquals("a", cache.get("p1") { "x" })
        assertEquals("b", cache.get("p2") { "y" })
    }
}

class CamdApiServerClientTest {
    @Test
    fun answersOneLineAndClosesTheSocket() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val server = CamdApiServer(engine)
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val client = java.net.Socket()
            client.soTimeout = 2_000
            client.connect(
                java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), listener.localPort),
                2_000,
            )
            client.getOutputStream().write("STATUS\n".toByteArray())
            client.getOutputStream().flush()
            listener.accept().use { accepted ->
                server.serveClient(accepted)
            }
            val reader = client.getInputStream().bufferedReader()
            assertTrue(reader.readLine().startsWith("OK "))
            assertEquals(-1, reader.read())
            client.close()
        }
    }

    @Test
    fun idleClientTimesOutAndClosesWithoutReply() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val server = CamdApiServer(engine, clientTimeoutMs = 150)
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val client = java.net.Socket()
            client.soTimeout = 5_000
            client.connect(
                java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), listener.localPort),
                2_000,
            )
            listener.accept().use { accepted ->
                server.serveClient(accepted)
            }
            assertEquals(-1, client.getInputStream().read())
            client.close()
        }
    }
}
