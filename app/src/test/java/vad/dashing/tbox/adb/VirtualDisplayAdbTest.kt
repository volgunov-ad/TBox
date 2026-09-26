package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VirtualDisplayAdbTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun buildAmStartOnDisplayCommand() {
        assertEquals(
            "am start --display 5 -n ru.yandex.yandexmaps/ru.yandex.yandexmaps.MapActivity",
            VirtualDisplayAdb.buildAmStartOnDisplayCommand(
                5,
                "ru.yandex.yandexmaps/ru.yandex.yandexmaps.MapActivity",
            ),
        )
    }

    @Test
    fun refresh_restoresTcpAndParsesDump() = runBlocking {
        val dump = """
            mDisplayId=0
            size 1920 x 981
            mDisplayId=5
            size 1320 x 856
        """.trimIndent()
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        gateway.onShell = {
            AdbShellResult(stdout = dump, stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = VirtualDisplayAdb.refreshDisplayListWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
        )

        assertTrue(outcome is VirtualDisplayAdb.RefreshOutcome.Success)
        val success = outcome as VirtualDisplayAdb.RefreshOutcome.Success
        assertEquals(listOf(HuDisplayInfo(0, 1920, 981), HuDisplayInfo(5, 1320, 856)), success.displays)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertEquals(listOf(VirtualDisplayAdb.DUMPSYS_DISPLAY_COMMAND), gateway.shellCommands)
    }

    @Test
    fun refresh_portAlreadyOpen_doesNotToggle() = runBlocking {
        val dump = """
            mDisplayId=0
            size 1920 x 981
        """.trimIndent()
        val gateway = FakeGateway(tcpEnabled = false, portOpen = true)
        gateway.onShell = {
            AdbShellResult(stdout = dump, stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = VirtualDisplayAdb.refreshDisplayListWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
        )

        assertTrue(outcome is VirtualDisplayAdb.RefreshOutcome.Success)
        assertTrue(gateway.setTcpCalls.isEmpty())
    }

    @Test
    fun launch_leavesTcpEnabled() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        gateway.onShell = {
            AdbShellResult(stdout = "Starting: Intent {}", stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = VirtualDisplayAdb.launchOnDisplayWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            displayId = 5,
            component = "com.example/.MainActivity",
        )

        assertEquals(VirtualDisplayAdb.LaunchOutcome.Success, outcome)
        assertEquals(listOf(true), gateway.setTcpCalls)
        assertEquals(
            listOf("am start --display 5 -n com.example/.MainActivity"),
            gateway.shellCommands,
        )
    }

    @Test
    fun launch_tcpAlreadyOn_doesNotToggle() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        gateway.onShell = {
            AdbShellResult(stdout = "", stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = VirtualDisplayAdb.launchOnDisplayWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            displayId = 0,
            component = "com.example/.MainActivity",
        )

        assertEquals(VirtualDisplayAdb.LaunchOutcome.Success, outcome)
        assertTrue(gateway.setTcpCalls.isEmpty())
    }

    private class FakeGateway(
        var tcpEnabled: Boolean = false,
        var portOpen: Boolean = false,
    ) : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var onShell: () -> AdbShellResult = {
            AdbShellResult("", "", 0, true)
        }

        override suspend fun refreshHuAdb() = Unit

        override suspend fun isTcpEnabled(): Boolean = tcpEnabled

        override suspend fun setTcpEnabled(enabled: Boolean) {
            setTcpCalls += enabled
            tcpEnabled = enabled
            portOpen = enabled
        }

        override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = portOpen

        override fun <T> withShellSession(
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
