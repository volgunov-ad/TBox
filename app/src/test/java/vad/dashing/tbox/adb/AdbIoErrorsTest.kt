package vad.dashing.tbox.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AdbIoErrorsTest {

    @Before
    fun resetGate() {
        AdbShutdownGate.resetForTests()
    }

    @Test
    fun isBenignDisconnectMessage_matchesTransportClosed() {
        assertTrue(AdbIoErrors.isBenignDisconnectMessage("ADB transport closed"))
        assertTrue(AdbIoErrors.isBenignDisconnectMessage("EOFException: ADB transport closed"))
        assertTrue(AdbIoErrors.isBenignDisconnectMessage("ADB USB transport closed"))
        assertFalse(AdbIoErrors.isBenignDisconnectMessage("USB permission denied"))
        assertFalse(AdbIoErrors.isBenignDisconnectMessage(null))
        assertFalse(AdbIoErrors.isBenignDisconnectMessage(""))
    }

    @Test
    fun shouldSuppress_onlyWhenShuttingDownOrIntentionalClose() {
        val msg = "ADB transport closed"
        assertFalse(AdbIoErrors.shouldSuppressUserFacingFailure(msg))
        AdbShutdownGate.markAppShuttingDown()
        assertTrue(AdbIoErrors.shouldSuppressUserFacingFailure(msg))
        AdbShutdownGate.resetForTests()
        assertFalse(AdbIoErrors.shouldSuppressUserFacingFailure(msg))
        AdbShutdownGate.withIntentionalTransportClose {
            assertTrue(AdbIoErrors.shouldSuppressUserFacingFailure(msg))
        }
        assertFalse(AdbIoErrors.shouldSuppressUserFacingFailure(msg))
        assertFalse(AdbIoErrors.shouldSuppressUserFacingFailure("Permission denial"))
    }
}

class LocalhostAdbSessionCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun resetGate() {
        AdbShutdownGate.resetForTests()
        vad.dashing.tbox.TboxRepository.clearLogs()
        vad.dashing.tbox.TboxRepository.updateMinLogLevel("DEBUG")
    }

    @Test
    fun run_rethrowsCancellationException_insteadOfFailed() = runBlocking {
        val gateway = object : LocalhostAdbSession.Gateway {
            override suspend fun refreshHuAdb() = Unit
            override suspend fun isTcpEnabled(): Boolean = true
            override suspend fun setTcpEnabled(enabled: Boolean) = Unit
            override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = true
            override suspend fun <T> withShellSession(
                host: String,
                port: Int,
                connectTimeoutMs: Int,
                sessionTimeoutMs: Int,
                keysDir: File,
                clientName: String,
                block: (execute: (String) -> AdbShellResult) -> T,
            ): T {
                throw CancellationException("composition disposed")
            }
        }
        try {
            LocalhostAdbSession.run(
                gateway = gateway,
                keysDir = tempFolder.newFolder("adb"),
                clientName = "test@hu",
            ) { "ok" }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("composition disposed", e.message)
        }
    }

    @Test
    fun run_benignTransportClosed_duringShutdown_logsDebugNotError() = runBlocking {
        AdbShutdownGate.markAppShuttingDown()
        val gateway = object : LocalhostAdbSession.Gateway {
            override suspend fun refreshHuAdb() = Unit
            override suspend fun isTcpEnabled(): Boolean = true
            override suspend fun setTcpEnabled(enabled: Boolean) = Unit
            override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = true
            override suspend fun <T> withShellSession(
                host: String,
                port: Int,
                connectTimeoutMs: Int,
                sessionTimeoutMs: Int,
                keysDir: File,
                clientName: String,
                block: (execute: (String) -> AdbShellResult) -> T,
            ): T {
                throw java.io.EOFException("ADB transport closed")
            }
        }
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
        ) { "ok" }
        assertTrue(result is LocalhostAdbSession.Result.Failed)
        val logs = vad.dashing.tbox.TboxRepository.logs.value
        assertTrue(logs.any { it.contains("DEBUG") && it.contains("ADB transport closed") })
        assertFalse(logs.any { it.contains("ERROR") && it.contains("ADB transport closed") })
    }

    @Test
    fun run_transportClosed_whileAlive_logsError() = runBlocking {
        val gateway = object : LocalhostAdbSession.Gateway {
            override suspend fun refreshHuAdb() = Unit
            override suspend fun isTcpEnabled(): Boolean = true
            override suspend fun setTcpEnabled(enabled: Boolean) = Unit
            override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = true
            override suspend fun <T> withShellSession(
                host: String,
                port: Int,
                connectTimeoutMs: Int,
                sessionTimeoutMs: Int,
                keysDir: File,
                clientName: String,
                block: (execute: (String) -> AdbShellResult) -> T,
            ): T {
                throw java.io.EOFException("ADB transport closed")
            }
        }
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
        ) { "ok" }
        assertTrue(result is LocalhostAdbSession.Result.Failed)
        val logs = vad.dashing.tbox.TboxRepository.logs.value
        assertTrue(logs.any { it.contains("ERROR") && it.contains("ADB transport closed") })
    }

    @Test
    fun run_tcpEnableFailed_logsError() = runBlocking {
        val gateway = object : LocalhostAdbSession.Gateway {
            override suspend fun refreshHuAdb() = Unit
            override suspend fun isTcpEnabled(): Boolean = false
            override suspend fun setTcpEnabled(enabled: Boolean) {
                // Enable props but never open the port.
            }
            override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = false
            override suspend fun <T> withShellSession(
                host: String,
                port: Int,
                connectTimeoutMs: Int,
                sessionTimeoutMs: Int,
                keysDir: File,
                clientName: String,
                block: (execute: (String) -> AdbShellResult) -> T,
            ): T = error("should not connect")
        }
        val result = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            readyTimeoutMs = 50L,
            delayMs = { },
        ) { "ok" }
        assertTrue(result is LocalhostAdbSession.Result.Failed)
        assertEquals(
            LocalhostAdbSession.Reason.TcpEnableFailed,
            (result as LocalhostAdbSession.Result.Failed).reason,
        )
        val logs = vad.dashing.tbox.TboxRepository.logs.value
        assertTrue(logs.any { it.contains("ERROR") && it.contains("ADB TCP enable failed") })
    }
}
