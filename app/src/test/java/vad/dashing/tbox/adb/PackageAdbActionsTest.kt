package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackageAdbActionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun resetSession() {
        PackageAdbActions.resetSessionForTests()
    }

    @Test
    fun buildCommands() {
        assertEquals("pm hide com.example", PackageAdbActions.buildHideCommand("com.example"))
        assertEquals("pm unhide com.example", PackageAdbActions.buildUnhideCommand("com.example"))
        assertEquals(
            "pm disable-user --user 0 com.example",
            PackageAdbActions.buildDisableCommand("com.example"),
        )
        assertEquals("pm enable com.example", PackageAdbActions.buildEnableCommand("com.example"))
        assertEquals(
            "am force-stop com.example",
            PackageAdbActions.buildForceStopCommand("com.example"),
        )
    }

    @Test
    fun parsePmListPackages() {
        val out = """
            package:com.android.settings
            package:vad.dashing.tbox
            junk
            package:
        """.trimIndent()
        assertEquals(
            setOf("com.android.settings", "vad.dashing.tbox"),
            PackageAdbActions.parsePmListPackages(out),
        )
    }

    @Test
    fun buildStatuses_marksHiddenDisabledRunning() {
        val statuses = PackageAdbActions.buildStatuses(
            listed = setOf("a", "b"),
            listedIncludingUninstalled = setOf("a", "b", "hidden.pkg"),
            disabled = setOf("b"),
            running = setOf("a"),
        )
        assertTrue(statuses.getValue("hidden.pkg").systemHidden)
        assertFalse(statuses.getValue("a").systemHidden)
        assertTrue(statuses.getValue("b").disabled)
        assertTrue(statuses.getValue("a").running)
        assertFalse(statuses.getValue("b").running)
    }

    @Test
    fun parseRunningPackages_matchesProcessAndColonSuffix() {
        val ps = """
            USER           PID  NAME
            u0_a12        1001 com.example
            u0_a12        1002 com.example:service
            u0_a13        1003 other.app
        """.trimIndent()
        val running = PackageAdbActions.parseRunningPackages(
            ps,
            setOf("com.example", "missing.app"),
        )
        assertEquals(setOf("com.example"), running)
    }

    @Test
    fun enter_enablesTcpAndLeavesItOn_exitRestores() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        gateway.onShell = { AdbShellResult("package:com.example\n", "", 0, true) }

        val entered = PackageAdbActions.enterAdvancedSessionWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
        )
        assertTrue(entered is PackageAdbActions.StatusLoadOutcome.Success)
        assertTrue(PackageAdbActions.isAdvancedSessionActive())
        assertEquals(listOf(true), gateway.setTcpCalls)

        gateway.onShell = { cmd ->
            when {
                cmd.startsWith("am force-stop") ->
                    AdbShellResult("", "", 0, true)
                else -> AdbShellResult("package:com.example\n", "", 0, true)
            }
        }
        val action = PackageAdbActions.runActionWith(
            gateway = gateway,
            keysDir = tempFolder.root.resolve("adb"),
            clientName = "test@hu",
            action = PackageAdbActions.Action.ForceStop,
            packageName = "com.example",
        )
        assertTrue(action is PackageAdbActions.Outcome.Success)
        // Port already open from enter — no extra TCP toggles.
        assertEquals(listOf(true), gateway.setTcpCalls)
        assertTrue(gateway.shellCommands.any { it == "am force-stop com.example" })

        PackageAdbActions.exitAdvancedSessionWith(gateway)
        assertFalse(PackageAdbActions.isAdvancedSessionActive())
        assertEquals(listOf(true, false), gateway.setTcpCalls)
    }

    @Test
    fun enter_portAlreadyOpen_doesNotToggleOnExit() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = true)
        gateway.onShell = { AdbShellResult("package:x\n", "", 0, true) }

        val entered = PackageAdbActions.enterAdvancedSessionWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb2"),
            clientName = "test@hu",
        )
        assertTrue(entered is PackageAdbActions.StatusLoadOutcome.Success)
        assertTrue(gateway.setTcpCalls.isEmpty())

        PackageAdbActions.exitAdvancedSessionWith(gateway)
        assertTrue(gateway.setTcpCalls.isEmpty())
    }

    @Test
    fun runAction_withoutSession_fails() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        val outcome = PackageAdbActions.runActionWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb3"),
            clientName = "test@hu",
            action = PackageAdbActions.Action.Hide,
            packageName = "com.example",
        )
        assertTrue(outcome is PackageAdbActions.Outcome.Failed)
        assertEquals(
            PackageAdbActions.Reason.SessionNotActive,
            (outcome as PackageAdbActions.Outcome.Failed).reason,
        )
    }

    private class FakeGateway(
        var tcpEnabled: Boolean,
        var portOpen: Boolean,
    ) : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var onShell: (String) -> AdbShellResult = { AdbShellResult("", "", 0, true) }

        override suspend fun refreshHuAdb() = Unit

        override suspend fun isTcpEnabled(): Boolean = tcpEnabled

        override suspend fun setTcpEnabled(enabled: Boolean) {
            setTcpCalls += enabled
            tcpEnabled = enabled
            portOpen = enabled
        }

        override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = portOpen

        override suspend fun <T> withShellSession(
            host: String,
            port: Int,
            connectTimeoutMs: Int,
            sessionTimeoutMs: Int,
            keysDir: File,
            clientName: String,
            block: (execute: (String) -> AdbShellResult) -> T,
        ): T = block { command ->
            shellCommands += command
            onShell(command)
        }
    }
}
