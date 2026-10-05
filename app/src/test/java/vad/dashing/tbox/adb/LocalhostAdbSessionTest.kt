package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalhostAdbSessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun run_portAlreadyOpen_propsOff_doesNotToggleOrRestore() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = true)
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            afterSession = LocalhostAdbSession.AfterSession.RestorePreviousTcp,
        ) { execute ->
            execute("echo ok")
            "done"
        }
        assertEquals(LocalhostAdbSession.Result.Ok("done"), result)
        assertTrue(gateway.setTcpCalls.isEmpty())
        assertEquals(listOf("echo ok"), gateway.shellCommands)
    }

    @Test
    fun run_propsOff_portClosed_enablesAndRestores() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            afterSession = LocalhostAdbSession.AfterSession.RestorePreviousTcp,
        ) { execute ->
            execute("pm grant x y")
            1
        }
        assertEquals(LocalhostAdbSession.Result.Ok(1), result)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
    }

    @Test
    fun run_propsOff_portClosed_leaveTcp_doesNotRestore() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            afterSession = LocalhostAdbSession.AfterSession.LeaveTcpEnabled,
        ) { "ok" }
        assertEquals(LocalhostAdbSession.Result.Ok("ok"), result)
        assertEquals(listOf(true), gateway.setTcpCalls)
        assertTrue(gateway.tcpEnabled)
        assertTrue(gateway.portOpen)
    }

    @Test
    fun run_propsOn_doesNotToggle() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
        ) { "ok" }
        assertEquals(LocalhostAdbSession.Result.Ok("ok"), result)
        assertTrue(gateway.setTcpCalls.isEmpty())
    }

    private class FakeGateway(
        var tcpEnabled: Boolean,
        var portOpen: Boolean,
    ) : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()

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
            AdbShellResult("", "", 0, true)
        }
    }
}
