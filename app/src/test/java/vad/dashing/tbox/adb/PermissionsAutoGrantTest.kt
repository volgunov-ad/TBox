package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vad.dashing.tbox.AppPermissionId
import vad.dashing.tbox.AppPermissions

class PermissionsAutoGrantTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun applyGrants_runsAppopsPmAndNotificationListenerPut() {
        val commands = mutableListOf<String>()
        val execute: (String) -> AdbShellResult = { cmd ->
            commands += cmd
            when {
                cmd.startsWith("settings get") ->
                    AdbShellResult("null", "", 0, true)
                else -> AdbShellResult("", "", 0, true)
            }
        }

        PermissionsAutoGrant.applyGrants(
            missing = listOf(
                AppPermissionId.Overlay,
                AppPermissionId.WriteSettings,
                AppPermissionId.WriteSecureSettings,
                AppPermissionId.UsageStats,
                AppPermissionId.NotificationListener,
                AppPermissionId.InstallPackages,
                AppPermissionId.Storage,
                AppPermissionId.Location,
            ),
            packageName = "vad.dashing.tbox",
            sdkInt = 28,
            execute = execute,
        )

        assertTrue(commands.any { it.contains("SYSTEM_ALERT_WINDOW") })
        assertTrue(commands.any { it.contains("WRITE_SETTINGS") })
        assertTrue(commands.any { it.contains("WRITE_SECURE_SETTINGS") })
        assertTrue(commands.any { it.contains("GET_USAGE_STATS") })
        assertTrue(commands.any { it.contains("REQUEST_INSTALL_PACKAGES") })
        assertTrue(commands.any { it.contains("READ_EXTERNAL_STORAGE") })
        assertTrue(commands.any { it.contains("ACCESS_FINE_LOCATION") })
        assertTrue(commands.none { it.contains("ACCESS_BACKGROUND_LOCATION") })
        assertTrue(commands.any { it.startsWith("settings get secure enabled_notification_listeners") })
        assertTrue(
            commands.any {
                it.startsWith("settings put secure enabled_notification_listeners") &&
                    it.contains("MediaControlNotificationListenerService")
            },
        )
    }

    @Test
    fun applyGrants_skipsNotificationPutWhenAlreadyEnabled() {
        val component = AppPermissions.notificationListenerComponent("vad.dashing.tbox")
        val commands = mutableListOf<String>()
        PermissionsAutoGrant.applyGrants(
            missing = listOf(AppPermissionId.NotificationListener),
            packageName = "vad.dashing.tbox",
            sdkInt = 28,
            execute = { cmd ->
                commands += cmd
                if (cmd.startsWith("settings get")) {
                    AdbShellResult(component, "", 0, true)
                } else {
                    AdbShellResult("", "", 0, true)
                }
            },
        )
        assertEquals(1, commands.size)
        assertTrue(commands[0].startsWith("settings get"))
    }

    @Test
    fun applyGrants_storageUsesManageExternalStorageOnApi30() {
        val commands = mutableListOf<String>()
        PermissionsAutoGrant.applyGrants(
            missing = listOf(AppPermissionId.Storage),
            packageName = "vad.dashing.tbox",
            sdkInt = 30,
            execute = {
                commands += it
                AdbShellResult("", "", 0, true)
            },
        )
        assertEquals(
            listOf("appops set vad.dashing.tbox MANAGE_EXTERNAL_STORAGE allow"),
            commands,
        )
    }

    @Test
    fun grantMissingWith_alreadyAllGranted_skipsTcp() = runBlocking {
        val gateway = FakeGateway(missing = emptyList())
        val outcome = PermissionsAutoGrant.grantMissingWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
            sdkInt = 28,
        )
        assertEquals(PermissionsAutoGrant.Outcome.AlreadyAllGranted, outcome)
        assertTrue(gateway.setTcpCalls.isEmpty())
        assertTrue(gateway.shellCommands.isEmpty())
    }

    @Test
    fun grantMissingWith_grantsAllAndRestoresTcp() = runBlocking {
        val gateway = FakeGateway(
            missing = listOf(AppPermissionId.Overlay, AppPermissionId.Location),
            tcpEnabled = false,
            openAfterProbes = 1,
        )
        gateway.afterShell = {
            gateway.missing = emptyList()
        }

        val outcome = PermissionsAutoGrant.grantMissingWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
            sdkInt = 28,
        )

        assertEquals(
            PermissionsAutoGrant.Outcome.Success(
                listOf(AppPermissionId.Overlay, AppPermissionId.Location),
            ),
            outcome,
        )
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertTrue(gateway.shellCommands.any { it.contains("SYSTEM_ALERT_WINDOW") })
        assertTrue(gateway.shellCommands.any { it.contains("ACCESS_FINE_LOCATION") })
    }

    @Test
    fun grantMissingWith_partialWhenSomeRemainMissing() = runBlocking {
        val gateway = FakeGateway(
            missing = listOf(
                AppPermissionId.Overlay,
                AppPermissionId.WriteSecureSettings,
            ),
            tcpEnabled = true,
            openAfterProbes = 1,
        )
        gateway.afterShell = {
            gateway.missing = listOf(AppPermissionId.WriteSecureSettings)
        }

        val outcome = PermissionsAutoGrant.grantMissingWith(
            gateway = gateway,
            keysDir = tempFolder.newFolder("adb"),
            clientName = "test@hu",
            packageName = "vad.dashing.tbox",
            sdkInt = 28,
        )

        assertEquals(
            PermissionsAutoGrant.Outcome.Partial(
                newlyGranted = listOf(AppPermissionId.Overlay),
                stillMissing = listOf(AppPermissionId.WriteSecureSettings),
            ),
            outcome,
        )
        assertTrue(gateway.setTcpCalls.isEmpty())
    }

    @Test
    fun buildNotificationListenerEnableCommand_appendsToExistingList() {
        val component = "vad.dashing.tbox/vad.dashing.tbox.MediaControlNotificationListenerService"
        val cmd = AppPermissions.buildNotificationListenerEnableCommand(
            component = component,
            currentListeners = "other.pkg/.OtherListener",
        )
        assertEquals(
            "settings put secure enabled_notification_listeners " +
                "other.pkg/.OtherListener:$component",
            cmd,
        )
    }

    private class FakeGateway(
        var missing: List<AppPermissionId>,
        var tcpEnabled: Boolean = false,
        private val openAfterProbes: Int = 1,
    ) : PermissionsAutoGrant.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        var afterShell: () -> Unit = {}
        private var probes = 0

        override fun missingPermissionIds(): List<AppPermissionId> = missing

        override fun refreshMissingPermissionIds(): List<AppPermissionId> = missing

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
        ): T {
            val result = block { command ->
                shellCommands += command
                if (command.startsWith("settings get")) {
                    AdbShellResult("null", "", 0, true)
                } else {
                    AdbShellResult("", "", 0, true)
                }
            }
            afterShell()
            return result
        }
    }
}
