package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbAutomationActionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun runShell_blankCommand_failsWithoutTcp() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        val result = AdbAutomationActions.runShellCommandWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            command = "  ",
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("пуста"))
        assertTrue(gateway.setTcpCalls.isEmpty())
    }

    @Test
    fun runShell_portAlreadyOpen_doesNotToggleTcp() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = true)
        gateway.onShell = {
            AdbShellResult("ok-line\n", "", 0, true)
        }
        val result = AdbAutomationActions.runShellCommandWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            command = "pm list packages",
        )
        assertTrue(result.success)
        assertTrue(result.message.contains("ok-line"))
        assertTrue(gateway.setTcpCalls.isEmpty())
        assertEquals(listOf("pm list packages"), gateway.shellCommands)
    }

    @Test
    fun runShell_enablesAndRestoresWhenPortClosed() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        gateway.onShell = { AdbShellResult("", "", 0, true) }
        val result = AdbAutomationActions.runShellCommandWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            command = "echo hi",
        )
        assertTrue(result.success)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
    }

    @Test
    fun runShell_nonZeroExit_isFailure() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        gateway.onShell = {
            AdbShellResult("", "Permission denial", 255, true)
        }
        val result = AdbAutomationActions.runShellCommandWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            command = "pm grant x y",
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("Permission denial"))
    }

    @Test
    fun forceStopCommand_enablesAndRestoresLikeShell() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        val result = AdbAutomationActions.runShellCommandWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb-fs"),
            clientName = "test@hu",
            command = PackageAdbActions.buildForceStopCommand("com.example"),
        )
        assertTrue(result.success)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertEquals(listOf("am force-stop com.example"), gateway.shellCommands)
    }

    private class FakeGateway(
        var tcpEnabled: Boolean,
        var portOpen: Boolean,
    ) : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var onShell: () -> AdbShellResult = { AdbShellResult("", "", 0, true) }

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
            onShell()
        }
    }
}
