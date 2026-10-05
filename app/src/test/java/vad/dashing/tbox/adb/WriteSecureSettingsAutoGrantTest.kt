package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WriteSecureSettingsAutoGrantTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun waitUntilTcpReady_returnsTrueWhenPortOpensBeforeDeadline() = runBlocking {
        var probes = 0
        var now = 0L
        val ready = WriteSecureSettingsAutoGrant.waitUntilTcpReady(
            host = "127.0.0.1",
            port = 5555,
            readyTimeoutMs = 3_000L,
            probeTimeoutMs = 250,
            probeIntervalMs = 100L,
            isOpen = { _, _, _ ->
                probes++
                probes >= 3
            },
            nowMs = { now },
            delayMs = { delta -> now += delta },
        )
        assertTrue(ready)
        assertEquals(3, probes)
    }

    @Test
    fun waitUntilTcpReady_returnsFalseWhenDeadlineExpires() = runBlocking {
        var now = 0L
        val ready = WriteSecureSettingsAutoGrant.waitUntilTcpReady(
            host = "127.0.0.1",
            port = 5555,
            readyTimeoutMs = 300L,
            probeTimeoutMs = 50,
            probeIntervalMs = 100L,
            isOpen = { _, _, _ -> false },
            nowMs = { now },
            delayMs = { delta -> now += delta },
        )
        assertFalse(ready)
        assertTrue(now >= 300L)
    }

    @Test
    fun grantWith_alreadyGranted_skipsTcp() = runBlocking {
        val gateway = FakeGateway(permissionGranted = true)
        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )
        assertEquals(WriteSecureSettingsAutoGrant.Outcome.AlreadyGranted, outcome)
        assertEquals(0, gateway.setTcpCalls.size)
        assertEquals(0, gateway.shellCommands.size)
    }

    @Test
    fun grantWith_enablesTcpWaitsGrantsAndRestores() = runBlocking {
        val gateway = FakeGateway(
            permissionGranted = false,
            tcpEnabled = false,
            portOpen = false,
        )
        gateway.onShell = {
            gateway.permissionGranted = true
            AdbShellResult(stdout = "", stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(WriteSecureSettingsAutoGrant.Outcome.Success, outcome)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertEquals(
            listOf("pm grant vad.dashing.tbox android.permission.WRITE_SECURE_SETTINGS"),
            gateway.shellCommands,
        )
    }

    @Test
    fun grantWith_portOpenPropsOff_doesNotToggle() = runBlocking {
        val gateway = FakeGateway(
            permissionGranted = false,
            tcpEnabled = false,
            portOpen = true,
        )
        gateway.onShell = {
            gateway.permissionGranted = true
            AdbShellResult(stdout = "", stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(WriteSecureSettingsAutoGrant.Outcome.Success, outcome)
        assertTrue(gateway.setTcpCalls.isEmpty())
        assertEquals(1, gateway.shellCommands.size)
    }

    @Test
    fun grantWith_tcpAlreadyOn_doesNotToggle() = runBlocking {
        val gateway = FakeGateway(
            permissionGranted = false,
            tcpEnabled = true,
            portOpen = true,
        )
        gateway.onShell = {
            gateway.permissionGranted = true
            AdbShellResult(stdout = "", stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(WriteSecureSettingsAutoGrant.Outcome.Success, outcome)
        assertTrue(gateway.setTcpCalls.isEmpty())
        assertEquals(1, gateway.shellCommands.size)
    }

    @Test
    fun grantWith_tcpEnableFails_returnsTcpEnableFailed() = runBlocking {
        val gateway = FakeGateway(
            permissionGranted = false,
            tcpEnabled = false,
            portOpen = false,
            setTcpNoOp = true,
        )

        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(
            WriteSecureSettingsAutoGrant.Outcome.Failed(
                WriteSecureSettingsAutoGrant.Reason.TcpEnableFailed,
            ),
            outcome,
        )
        assertEquals(listOf(true), gateway.setTcpCalls)
        assertTrue(gateway.shellCommands.isEmpty())
    }

    @Test
    fun grantWith_tcpNotReady_restoresPreviousOffState() = runBlocking {
        val gateway = FakeGateway(
            permissionGranted = false,
            tcpEnabled = false,
            portOpen = false,
            openPortOnEnable = false,
        )
        var now = 0L
        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
            readyTimeoutMs = 200L,
            nowMs = { now },
            delayMs = { delta -> now += delta },
        )

        assertEquals(
            WriteSecureSettingsAutoGrant.Outcome.Failed(
                WriteSecureSettingsAutoGrant.Reason.TcpNotReady,
            ),
            outcome,
        )
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertTrue(gateway.shellCommands.isEmpty())
    }

    @Test
    fun grantWith_adbConnectThrows_restoresTcp() = runBlocking {
        val gateway = FakeGateway(
            permissionGranted = false,
            tcpEnabled = false,
            portOpen = false,
        )
        gateway.onShell = { error("auth rejected") }

        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertTrue(outcome is WriteSecureSettingsAutoGrant.Outcome.Failed)
        val failed = outcome as WriteSecureSettingsAutoGrant.Outcome.Failed
        assertEquals(WriteSecureSettingsAutoGrant.Reason.AdbConnectFailed, failed.reason)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
    }

    @Test
    fun grantWith_pmGrantNonZeroExit_reportsGrantCommandFailed() = runBlocking {
        val gateway = FakeGateway(
            permissionGranted = false,
            tcpEnabled = true,
            portOpen = true,
        )
        gateway.onShell = {
            AdbShellResult(
                stdout = "",
                stderr = "Permission denial",
                exitCode = 255,
                shellV2 = true,
            )
        }

        val outcome = WriteSecureSettingsAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(
            WriteSecureSettingsAutoGrant.Outcome.Failed(
                WriteSecureSettingsAutoGrant.Reason.GrantCommandFailed,
                "Permission denial",
            ),
            outcome,
        )
    }

    private class FakeGateway(
        var permissionGranted: Boolean,
        var tcpEnabled: Boolean = false,
        var portOpen: Boolean = false,
        private val setTcpNoOp: Boolean = false,
        private val openPortOnEnable: Boolean = true,
    ) : WriteSecureSettingsAutoGrant.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var onShell: () -> AdbShellResult = {
            AdbShellResult("", "", 0, true)
        }

        override fun isPermissionGranted(): Boolean = permissionGranted

        override suspend fun refreshHuAdb() = Unit

        override suspend fun isTcpEnabled(): Boolean = tcpEnabled

        override suspend fun setTcpEnabled(enabled: Boolean) {
            setTcpCalls += enabled
            if (!setTcpNoOp) {
                tcpEnabled = enabled
                if (openPortOnEnable) {
                    portOpen = enabled
                }
            }
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
