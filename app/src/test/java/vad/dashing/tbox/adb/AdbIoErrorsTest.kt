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
}
