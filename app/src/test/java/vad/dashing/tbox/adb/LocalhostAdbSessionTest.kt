package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
        assertEquals(LocalhostAdbSession.Result.Ok(1, tcpEnabledBySession = true), result)
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
        assertEquals(LocalhostAdbSession.Result.Ok("ok", tcpEnabledBySession = true), result)
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

    @Test
    fun run_cancelledMidSession_stillRestoresTcp() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        val keysDir = tempFolder.newFolder("adb")
        lateinit var job: Job
        job = launch {
            LocalhostAdbSession.run(gateway = gateway, keysDir = keysDir, clientName = "test@hu") {
                job.cancel()
                "x"
            }
        }
        job.join()
        assertEquals(listOf(true, false), gateway.setTcpCalls)
    }

    @Test
    fun run_reportsTcpEnabledCallback() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        var enabled = false
        LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            afterSession = LocalhostAdbSession.AfterSession.LeaveTcpEnabled,
            onTcpEnabled = { enabled = true },
        ) { "ok" }
        assertTrue(enabled)
        LocalhostAdbSession.restoreTcpOff(gateway)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
    }

    @Test
    fun run_retriesOnceAfterTransportClosedThenSucceeds() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        gateway.failShellTimes = 1
        gateway.shellFailure = java.io.EOFException("ADB transport closed")
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb-retry"),
            clientName = "test@hu",
            delayMs = { },
        ) { execute ->
            execute("am force-stop com.example")
            "done"
        }
        assertEquals(LocalhostAdbSession.Result.Ok("done"), result)
        assertEquals(2, gateway.shellSessionAttempts)
        assertEquals(listOf("am force-stop com.example"), gateway.shellCommands)
    }

    @Test
    fun run_notEstablishedThenSucceedsOnRetry() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        gateway.failShellTimes = 1
        gateway.shellFailure =
            IllegalStateException("ADB connection is not established")
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb-retry2"),
            clientName = "test@hu",
            delayMs = { },
        ) { "ok" }
        assertEquals(LocalhostAdbSession.Result.Ok("ok"), result)
        assertEquals(2, gateway.shellSessionAttempts)
    }

    private class FakeGateway(
        var tcpEnabled: Boolean,
        var portOpen: Boolean,
    ) : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var failShellTimes: Int = 0
        var shellFailure: Exception = java.io.EOFException("ADB transport closed")
        var shellSessionAttempts: Int = 0

        override suspend fun refreshHuAdb() = Unit

        override suspend fun isTcpEnabled(): Boolean = tcpEnabled

        override suspend fun setTcpEnabled(enabled: Boolean) {
            // Like HuAdbControl's withContext(IO): fails fast in a cancelled coroutine.
            currentCoroutineContext().ensureActive()
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
        ): T {
            shellSessionAttempts++
            if (failShellTimes > 0) {
                failShellTimes--
                throw shellFailure
            }
            return block { command ->
                shellCommands += command
                AdbShellResult("", "", 0, true)
            }
        }
    }
}
