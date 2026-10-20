package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuAdbControlTest {
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
}
