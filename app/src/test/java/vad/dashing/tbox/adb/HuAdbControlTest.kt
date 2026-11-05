package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HuAdbControlTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @After
    fun clearError() {
        HuAdbControl.consumeError()
    }

    @Test
    fun setUsbEnabled_dualRolePortWithTcpOff_sessionTcpToggleDoesNotDeadlock() = runBlocking {
        val gateway = HuAdbControlTcpGateway()
        withTimeout(10_000) {
            HuAdbControl.setUsbEnabledWith(
                dualRolePort = true,
                gateway = gateway,
                keysDir = tempFolder.newFolder("adb"),
                clientName = "test@hu",
                enabled = true,
            )
        }
        assertEquals(listOf(true, false), gateway.setTcpCalls)
        assertEquals(listOf(HuAdbControl.usbPortRoleCommand(true)), gateway.shellCommands)
    }

    @Test
    fun tcpCommands_enableUsesBothPortsAndRestartsAdbd() {
        assertEquals(
            listOf(
                listOf("setprop", "persist.adb.tcp.port", "5555"),
                listOf("setprop", "service.adb.tcp.port", "5555"),
                listOf("setprop", "ctl.restart", "adbd"),
            ),
            HuAdbControl.tcpCommands(true),
        )
    }

    @Test
    fun tcpCommands_disableUsesNegativePortAndRestartsAdbd() {
        assertEquals(
            listOf(
                listOf("setprop", "persist.adb.tcp.port", "-1"),
                listOf("setprop", "service.adb.tcp.port", "-1"),
                listOf("setprop", "ctl.restart", "adbd"),
            ),
            HuAdbControl.tcpCommands(false),
        )
    }

    @Test
    fun usbCommands_usePersistedUsbAdbProperty() {
        assertEquals(
            listOf(listOf("setprop", "persist.usb.adbenable", "1")),
            HuAdbControl.usbCommands(true),
        )
        assertEquals(
            listOf(listOf("setprop", "persist.usb.adbenable", "0")),
            HuAdbControl.usbCommands(false),
        )
    }

    @Test
    fun usbPortRoleCommand_switchesDualRoleAsRootAndPrintsMode() {
        val on = HuAdbControl.usbPortRoleCommand(true)
        assertTrue(on.startsWith("su 0 sh -c '"))
        assertTrue("echo device > /sys/class/dual_role_usb/dual-role-usb20/data_role" in on)
        assertTrue("echo 13010000.cdncusb > /config/usb_gadget/g1/UDC" in on)
        assertTrue(on.endsWith("cat /sys/class/dual_role_usb/dual-role-usb20/mode'"))
        val off = HuAdbControl.usbPortRoleCommand(false)
        assertTrue("echo host > /sys/class/dual_role_usb/dual-role-usb20/data_role" in off)
        assertFalse("UDC" in off)
    }

    @Test
    fun stateParsers_acceptWhitespaceAndFallbackToServicePort() {
        assertTrue(HuAdbControl.isTcpPortPropEnabled(" 5555 ", "0"))
        assertTrue(HuAdbControl.isTcpPortPropEnabled("", "5555"))
        assertFalse(HuAdbControl.isTcpPortPropEnabled("-1", "5555"))
        assertFalse(HuAdbControl.isTcpPortPropEnabled(null, null))
        assertTrue(HuAdbControl.isUsbAdbPropEnabled(" 1 "))
        assertFalse(HuAdbControl.isUsbAdbPropEnabled("0"))
    }

    /** TCP toggles go through the real [HuAdbControl.setTcpEnabled] and its apply mutex, as in production. */
    private class HuAdbControlTcpGateway : LocalhostAdbSession.Gateway {
        val setTcpCalls = mutableListOf<Boolean>()
        val shellCommands = mutableListOf<String>()
        private var portOpen = false

        override suspend fun refreshHuAdb() = Unit

        override suspend fun isTcpEnabled(): Boolean = portOpen

        override suspend fun setTcpEnabled(enabled: Boolean) {
            HuAdbControl.setTcpEnabled(enabled)
            setTcpCalls += enabled
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
            AdbShellResult("${HuAdbControl.PORT_MODE_DEVICE}\n", "", 0, true)
        }
    }
}
