package vad.dashing.tbox

import android.hardware.usb.UsbConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.adb.AdbUsbTransport

class AdbUsbNetworkInterfaceTest {

    @Test
    fun wirelessRndisControl_isNetwork() {
        assertTrue(
            AdbUsbTransport.isNetworkInterface(
                UsbConstants.USB_CLASS_WIRELESS_CONTROLLER,
                0x01,
                0x03,
            ),
        )
    }

    @Test
    fun cdcEcmNcmMbim_areNetwork() {
        assertTrue(AdbUsbTransport.isNetworkInterface(UsbConstants.USB_CLASS_COMM, 0x06, 0))
        assertTrue(AdbUsbTransport.isNetworkInterface(UsbConstants.USB_CLASS_COMM, 0x0D, 0))
        assertTrue(AdbUsbTransport.isNetworkInterface(UsbConstants.USB_CLASS_COMM, 0x0E, 0))
    }

    @Test
    fun abstractControlAndAdb_areNotNetworkAlone() {
        assertFalse(AdbUsbTransport.isNetworkInterface(UsbConstants.USB_CLASS_COMM, 0x02, 0x01))
        assertFalse(
            AdbUsbTransport.isNetworkInterface(
                AdbUsbTransport.ADB_CLASS,
                AdbUsbTransport.ADB_SUBCLASS,
                AdbUsbTransport.ADB_PROTOCOL,
            ),
        )
    }
}
