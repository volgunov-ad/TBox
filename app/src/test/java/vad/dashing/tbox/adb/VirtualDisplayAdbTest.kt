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
        val gateway = FakeGateway(tcpEnabled = false, openAfterProbes = 1)
        gateway.onShell = {
            AdbShellResult(stdout = dump, stderr = "", exitCode = 0, shellV2 = true)
        }

        var now = 0L
        val outcome = VirtualDisplayAdb.refreshDisplayListWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            readyTimeoutMs = 3_000L,
            nowMs = { now },
            delayMs = { delta -> now += delta },
        )

        assertTrue(outcome is VirtualDisplayAdb.RefreshOutcome.Success)
        val success = outcome as VirtualDisplayAdb.RefreshOutcome.Success
        assertEquals(listOf(HuDisplayInfo(0, 1920, 981), HuDisplayInfo(5, 1320, 856)), success.displays)
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertEquals(listOf(VirtualDisplayAdb.DUMPSYS_DISPLAY_COMMAND), gateway.shellCommands)
    }

    @Test
    fun launch_leavesTcpEnabled() = runBlocking {
        val gateway = FakeGateway(tcpEnabled = false, openAfterProbes = 1)
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
        val gateway = FakeGateway(tcpEnabled = true, openAfterProbes = 1)
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
        private val openAfterProbes: Int = 1,
    ) : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var onShell: () -> AdbShellResult = {
            AdbShellResult("", "", 0, true)
        }
        private var probes = 0

        override suspend fun refreshHuAdb() = Unit

        override suspend fun isTcpEnabled(): Boolean = tcpEnabled

        override suspend fun setTcpEnabled(enabled: Boolean) {
            setTcpCalls += enabled
            tcpEnabled = enabled
        }

        override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
            probes++
            return probes >= openAfterProbes
        }

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
