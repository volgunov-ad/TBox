package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MockLocationAutoGrantTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun grantWith_alreadyGranted_skipsTcp() = runBlocking {
        val gateway = FakeGateway(selected = true)
        val outcome = MockLocationAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )
        assertEquals(MockLocationAutoGrant.Outcome.AlreadyGranted, outcome)
        assertTrue(gateway.setTcpCalls.isEmpty())
        assertTrue(gateway.shellCommands.isEmpty())
    }

    @Test
    fun grantWith_runsAppopsAndSettingsPut() = runBlocking {
        val gateway = FakeGateway(
            selected = false,
            tcpEnabled = false,
            portOpen = false,
        )
        gateway.onShell = {
            gateway.selected = true
            AdbShellResult("", "", 0, true)
        }

        val outcome = MockLocationAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(MockLocationAutoGrant.Outcome.Success, outcome)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertEquals(
            listOf(
                "appops set vad.dashing.tbox MOCK_LOCATION allow",
                "settings put secure mock_location vad.dashing.tbox",
            ),
            gateway.shellCommands,
        )
    }

    @Test
    fun grantWith_shellFailure_reportsGrantCommandFailed() = runBlocking {
        val gateway = FakeGateway(
            selected = false,
            tcpEnabled = true,
            portOpen = true,
        )
        gateway.onShell = { command ->
            if (command.startsWith("appops")) {
                AdbShellResult("", "Error: unknown op", 255, true)
            } else {
                AdbShellResult("", "", 0, true)
            }
        }

        val outcome = MockLocationAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(
            MockLocationAutoGrant.Outcome.Failed(
                MockLocationAutoGrant.Reason.GrantCommandFailed,
                "Error: unknown op",
            ),
            outcome,
        )
    }

    @Test
    fun grantWith_stillMissingAfterCommands() = runBlocking {
        val gateway = FakeGateway(
            selected = false,
            tcpEnabled = true,
            portOpen = true,
        )

        val outcome = MockLocationAutoGrant.grantWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
        )

        assertEquals(
            MockLocationAutoGrant.Outcome.Failed(
                MockLocationAutoGrant.Reason.StillMissingAfterGrant,
            ),
            outcome,
        )
        assertEquals(2, gateway.shellCommands.size)
    }

    private class FakeGateway(
        var selected: Boolean,
        var tcpEnabled: Boolean = false,
        var portOpen: Boolean = false,
    ) : MockLocationAutoGrant.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var onShell: (String) -> AdbShellResult = {
            AdbShellResult("", "", 0, true)
        }

        override fun isMockProviderSelected(): Boolean = selected

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
