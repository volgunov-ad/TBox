package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun run_propsOnButPortClosed_restartsAdbdWithoutTakingOwnership() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = false)
        val clock = FakeClock()
        var tcpEnabledCallback = false
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            nowMs = clock::now,
            delayMs = clock::advance,
            onTcpEnabled = { tcpEnabledCallback = true },
        ) { execute ->
            execute("pm grant x y")
            "ok"
        }
        assertEquals(LocalhostAdbSession.Result.Ok("ok"), result)
        assertEquals(listOf(true), gateway.setTcpCalls)
        assertEquals(listOf("pm grant x y"), gateway.shellCommands)
        assertFalse(tcpEnabledCallback)
        assertEquals(LocalhostAdbSession.TCP_READY_TIMEOUT_MS, clock.nowMs)
    }

    @Test
    fun run_propsOnPortStillClosedAfterAdbdRestart_failsWithoutRestore() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = false)
        gateway.setTcpOpensPort = false
        val clock = FakeClock()
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            nowMs = clock::now,
            delayMs = clock::advance,
        ) { "never" }
        assertEquals(
            LocalhostAdbSession.Result.Failed(LocalhostAdbSession.Reason.TcpNotReady),
            result,
        )
        assertEquals(listOf(true), gateway.setTcpCalls)
        assertEquals(0, gateway.shellSessionAttempts)
        assertEquals(
            LocalhostAdbSession.TCP_READY_TIMEOUT_MS +
                LocalhostAdbSession.TCP_READY_AFTER_ENABLE_TIMEOUT_MS,
            clock.nowMs,
        )
    }

    @Test
    fun run_enabledBySessionButPortNeverOpens_restoresWithoutSecondRestart() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        gateway.setTcpOpensPort = false
        val clock = FakeClock()
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            nowMs = clock::now,
            delayMs = clock::advance,
        ) { "never" }
        assertEquals(
            LocalhostAdbSession.Result.Failed(LocalhostAdbSession.Reason.TcpNotReady),
            result,
        )
        assertEquals(listOf(true, false), gateway.setTcpCalls)
    }

    @Test
    fun withSessionLock_waitsForRunningSession() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        val shellEntered = CompletableDeferred<Unit>()
        val releaseShell = CompletableDeferred<Unit>()
        gateway.beforeShell = {
            shellEntered.complete(Unit)
            releaseShell.await()
        }
        val keysDir = tempFolder.newFolder("adb")
        val session = async {
            LocalhostAdbSession.run(gateway = gateway, keysDir = keysDir, clientName = "test@hu") { "ok" }
        }
        shellEntered.await()
        val toggleRan = CompletableDeferred<Unit>()
        val toggle = launch { LocalhostAdbSession.withSessionLock { toggleRan.complete(Unit) } }
        assertNull(withTimeoutOrNull(200) { toggleRan.await() })
        releaseShell.complete(Unit)
        assertEquals(LocalhostAdbSession.Result.Ok("ok"), session.await())
        toggle.join()
        assertTrue(toggleRan.isCompleted)
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

    private class FakeClock {
        var nowMs = 0L

        fun now(): Long = nowMs

        suspend fun advance(ms: Long) {
            nowMs += ms
        }
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
        /** False: adbd restarts with TCP props set but never listens. */
        var setTcpOpensPort: Boolean = true
        var beforeShell: suspend () -> Unit = {}

        override suspend fun refreshHuAdb() = Unit

        override suspend fun isTcpEnabled(): Boolean = tcpEnabled

        override suspend fun setTcpEnabled(enabled: Boolean) {
            // Like HuAdbControl's withContext(IO): fails fast in a cancelled coroutine.
            currentCoroutineContext().ensureActive()
            setTcpCalls += enabled
            tcpEnabled = enabled
            portOpen = enabled && setTcpOpensPort
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
            beforeShell()
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
