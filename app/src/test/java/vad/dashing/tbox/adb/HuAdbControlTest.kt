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
    fun stateParsers_acceptWhitespaceAndFallbackToServicePort() {
        assertTrue(HuAdbControl.isTcpPortPropEnabled(" 5555 ", "0"))
        assertTrue(HuAdbControl.isTcpPortPropEnabled("", "5555"))
        assertFalse(HuAdbControl.isTcpPortPropEnabled("-1", "5555"))
        assertFalse(HuAdbControl.isTcpPortPropEnabled(null, null))
        assertTrue(HuAdbControl.isUsbAdbPropEnabled(" 1 "))
        assertFalse(HuAdbControl.isUsbAdbPropEnabled("0"))
    }
}
