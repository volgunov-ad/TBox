package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vad.dashing.tbox.VirtualDisplayLaunchPolicy

class VirtualDisplayAdbTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun buildAmStartOnDisplayCommand_minimal() {
        assertEquals(
            "am start --display 5 -n ru.yandex.yandexmaps/ru.yandex.yandexmaps.MapActivity",
            VirtualDisplayAdb.buildAmStartOnDisplayCommand(
                5,
                "ru.yandex.yandexmaps/ru.yandex.yandexmaps.MapActivity",
            ),
        )
    }

    @Test
    fun buildAmStartOnDisplayCommand_multipleTask() {
        assertEquals(
            "am start --display 5 --activity-multiple-task --activity-new-task " +
                "-n com.example/.MainActivity",
            VirtualDisplayAdb.buildAmStartOnDisplayCommand(
                displayId = 5,
                component = "com.example/.MainActivity",
                multipleTask = true,
            ),
        )
    }

    @Test
    fun buildForceStopCommand() {
        assertEquals(
            "am force-stop com.example",
            VirtualDisplayAdb.buildForceStopCommand("com.example"),
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
    fun launch_relocate_forceStopsThenStarts_leavesTcpEnabled() = runBlocking {
        val dump = """
            mDisplayId=0
            size 1920 x 981
            mDisplayId=5
            size 1320 x 856
        """.trimIndent()
        val gateway = FakeGateway(tcpEnabled = false, portOpen = false)
        gateway.onShell = { command ->
            when {
                command == VirtualDisplayAdb.DUMPSYS_DISPLAY_COMMAND ->
                    AdbShellResult(stdout = dump, stderr = "", exitCode = 0, shellV2 = true)
                command.startsWith("am force-stop") ->
                    AdbShellResult(stdout = "", stderr = "", exitCode = 0, shellV2 = true)
                else ->
                    AdbShellResult(stdout = "Starting: Intent {}", stderr = "", exitCode = 0, shellV2 = true)
            }
        }

        val outcome = VirtualDisplayAdb.launchOnDisplayWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "com.example",
            preferredDisplayId = 5,
            preferredWidthPx = 1320,
            preferredHeightPx = 856,
            policy = VirtualDisplayLaunchPolicy.RELOCATE,
            component = "com.example/.MainActivity",
        )

        assertTrue(outcome is VirtualDisplayAdb.LaunchOutcome.Success)
        val success = outcome as VirtualDisplayAdb.LaunchOutcome.Success
        assertEquals(5, success.displayId)
        assertEquals(listOf(true), gateway.setTcpCalls)
        assertEquals(
            listOf(
                VirtualDisplayAdb.DUMPSYS_DISPLAY_COMMAND,
                "am force-stop com.example",
                "am start --display 5 -n com.example/.MainActivity",
            ),
            gateway.shellCommands,
        )
    }

    @Test
    fun launch_newInstance_usesMultipleTaskFlags_noForceStop() = runBlocking {
        val dump = """
            mDisplayId=5
            size 1320 x 856
        """.trimIndent()
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        gateway.onShell = { command ->
            if (command == VirtualDisplayAdb.DUMPSYS_DISPLAY_COMMAND) {
                AdbShellResult(stdout = dump, stderr = "", exitCode = 0, shellV2 = true)
            } else {
                AdbShellResult(stdout = "", stderr = "", exitCode = 0, shellV2 = true)
            }
        }

        val outcome = VirtualDisplayAdb.launchOnDisplayWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "com.example",
            preferredDisplayId = 5,
            preferredWidthPx = 1320,
            preferredHeightPx = 856,
            policy = VirtualDisplayLaunchPolicy.NEW_INSTANCE,
            component = "com.example/.MainActivity",
        )

        assertTrue(outcome is VirtualDisplayAdb.LaunchOutcome.Success)
        assertTrue(gateway.setTcpCalls.isEmpty())
        assertEquals(
            listOf(
                VirtualDisplayAdb.DUMPSYS_DISPLAY_COMMAND,
                "am start --display 5 --activity-multiple-task --activity-new-task " +
                    "-n com.example/.MainActivity",
            ),
            gateway.shellCommands,
        )
    }

    @Test
    fun launch_remapsDisplayIdByStoredSize() = runBlocking {
        val dump = """
            mDisplayId=0
            size 1920 x 981
            mDisplayId=9
            size 1320 x 856
        """.trimIndent()
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        gateway.onShell = { command ->
            if (command == VirtualDisplayAdb.DUMPSYS_DISPLAY_COMMAND) {
                AdbShellResult(stdout = dump, stderr = "", exitCode = 0, shellV2 = true)
            } else {
                AdbShellResult(stdout = "", stderr = "", exitCode = 0, shellV2 = true)
            }
        }

        val outcome = VirtualDisplayAdb.launchOnDisplayWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "com.example",
            preferredDisplayId = 5,
            preferredWidthPx = 1320,
            preferredHeightPx = 856,
            policy = VirtualDisplayLaunchPolicy.NEW_INSTANCE,
            component = "com.example/.MainActivity",
        )

        val success = outcome as VirtualDisplayAdb.LaunchOutcome.Success
        assertEquals(9, success.displayId)
        assertTrue(success.remapped)
        assertTrue(
            gateway.shellCommands.any {
                it == "am start --display 9 --activity-multiple-task --activity-new-task " +
                    "-n com.example/.MainActivity"
            },
        )
    }

    @Test
    fun launch_displayNotFound_failsClearly() = runBlocking {
        val dump = """
            mDisplayId=0
            size 1920 x 981
        """.trimIndent()
        val gateway = FakeGateway(tcpEnabled = true, portOpen = true)
        gateway.onShell = {
            AdbShellResult(stdout = dump, stderr = "", exitCode = 0, shellV2 = true)
        }

        val outcome = VirtualDisplayAdb.launchOnDisplayWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "com.example",
            preferredDisplayId = 5,
            preferredWidthPx = 1320,
            preferredHeightPx = 856,
            policy = VirtualDisplayLaunchPolicy.RELOCATE,
            component = "com.example/.MainActivity",
        )

        val failed = outcome as VirtualDisplayAdb.LaunchOutcome.Failed
        assertEquals(VirtualDisplayAdb.Reason.DisplayNotFound, failed.reason)
        assertTrue(failed.detail.contains("1320"))
    }

    private class FakeGateway(
        var tcpEnabled: Boolean = false,
        var portOpen: Boolean = false,
    ) : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var onShell: (String) -> AdbShellResult = {
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
            onShell(command)
        }
    }
}
