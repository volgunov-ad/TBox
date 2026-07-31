package vad.dashing.tbox.usbgnss

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log

/**
 * Vendor UART init for Silicon Labs CP210x and QinHeng CH340/CH341.
 *
 * CDC ACM uses standard SET_LINE_CODING; these bridges need vendor control transfers
 * (same sequences as usb-serial-for-android Cp21xx / Ch34x drivers).
 */
object UsbUartBridgeInit {
    private const val TAG = "UsbUartBridgeInit"
    private const val TIMEOUT_MS = 5_000

    const val VID_SILABS = 0x10C4
    const val VID_QINHENG = 0x1A86

    /**
     * @return true if this VID has a vendor init path that was attempted (success or logged fail).
     */
    fun applyIfNeeded(
        device: UsbDevice,
        connection: UsbDeviceConnection,
        interfaceId: Int,
        baud: Int,
    ): Boolean {
        return when (device.vendorId and 0xFFFF) {
            VID_SILABS -> {
                initCp210x(connection, interfaceId, baud)
                true
            }
            VID_QINHENG -> {
                initCh34x(connection, baud)
                true
            }
            else -> false
        }
    }

    fun initCp210x(connection: UsbDeviceConnection, portNumber: Int, baud: Int) {
        // SILABSER_IFC_ENABLE = UART_ENABLE
        controlOutCp(connection, request = 0x00, value = 0x0001, index = portNumber, data = null)
        // DTR_ENABLE | RTS_ENABLE (SET_MHS)
        controlOutCp(connection, request = 0x07, value = 0x0101 or 0x0202, index = portNumber, data = null)
        setCp210xBaud(connection, portNumber, baud)
        // LINE_CTL: 8 data bits, no parity, 1 stop ? 0x0800
        controlOutCp(connection, request = 0x03, value = 0x0800, index = portNumber, data = null)
        Log.i(TAG, "CP210x init baud=$baud port=$portNumber")
    }

    fun encodeCp210xBaud(baud: Int): ByteArray = byteArrayOf(
        (baud and 0xff).toByte(),
        ((baud shr 8) and 0xff).toByte(),
        ((baud shr 16) and 0xff).toByte(),
        ((baud shr 24) and 0xff).toByte(),
    )

    private fun setCp210xBaud(connection: UsbDeviceConnection, portNumber: Int, baud: Int) {
        val data = encodeCp210xBaud(baud)
        val rc = connection.controlTransfer(
            /* requestType */ 0x41, // vendor host?device
            /* request */ 0x1E, // SET_BAUDRATE
            /* value */ 0,
            /* index */ portNumber,
            /* buffer */ data,
            /* length */ data.size,
            /* timeout */ TIMEOUT_MS,
        )
        if (rc < 0) {
            Log.w(TAG, "CP210x SET_BAUDRATE failed rc=$rc baud=$baud")
        }
    }

    private fun controlOutCp(
        connection: UsbDeviceConnection,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
    ) {
        val len = data?.size ?: 0
        val rc = connection.controlTransfer(
            0x41,
            request,
            value,
            index,
            data,
            len,
            TIMEOUT_MS,
        )
        if (rc < 0) {
            Log.w(TAG, "CP210x control failed req=0x${request.toString(16)} rc=$rc")
        }
    }

    fun initCh34x(connection: UsbDeviceConnection, baud: Int) {
        // Minimal CH340 open sequence (usb-serial-for-android Ch34xSerialDriver).
        controlInCh(connection, request = 0x5f, value = 0, index = 0, length = 2)
        controlOutCh(connection, request = 0xa1, value = 0, index = 0)
        setCh34xBaud(connection, baud)
        controlInCh(connection, request = 0x95, value = 0x2518, index = 0, length = 2)
        // LCR: ENABLE_RX|ENABLE_TX|CS8
        controlOutCh(connection, request = 0x9a, value = 0x2518, index = 0x80 or 0x40 or 0x03)
        controlInCh(connection, request = 0x95, value = 0x0706, index = 0, length = 2)
        controlOutCh(connection, request = 0xa1, value = 0x501f, index = 0xd90a)
        setCh34xBaud(connection, baud)
        // DTR+RTS asserted (~SCL_DTR|SCL_RTS inverted write)
        controlOutCh(connection, request = 0xa4, value = ((0x20 or 0x40).inv()) and 0xFFFF, index = 0)
        Log.i(TAG, "CH34x init baud=$baud")
    }

    /**
     * CH340 baud factor/divisor encoding (unit-testable).
     * @return Pair(val1312, val0f2c) for controlOut 0x9a.
     */
    fun encodeCh34xBaudRegisters(baud: Int): Pair<Int, Int> {
        if (baud == 921_600) {
            val divisor = 7
            val factor = 0xf300
            val val1 = (factor and 0xff00) or (divisor or 0x0080)
            val val2 = factor and 0xff
            return val1 to val2
        }
        val baudBaseFactor = 1_532_620_800L
        var factor = baudBaseFactor / baud
        var divisor = 3
        while (factor > 0xfff0 && divisor > 0) {
            factor = factor shr 3
            divisor--
        }
        if (factor > 0xfff0) {
            throw IllegalArgumentException("Unsupported CH340 baud: $baud")
        }
        factor = 0x10000 - factor
        val divisorWithFlag = (divisor or 0x0080).toLong()
        val val1 = ((factor and 0xff00) or divisorWithFlag).toInt()
        val val2 = (factor and 0xff).toInt()
        return val1 to val2
    }

    private fun setCh34xBaud(connection: UsbDeviceConnection, baud: Int) {
        val (val1, val2) = try {
            encodeCh34xBaudRegisters(baud)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, e.message ?: "bad baud")
            return
        }
        controlOutCh(connection, request = 0x9a, value = 0x1312, index = val1)
        controlOutCh(connection, request = 0x9a, value = 0x0f2c, index = val2)
    }

    private fun controlOutCh(connection: UsbDeviceConnection, request: Int, value: Int, index: Int) {
        val reqType = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_OUT
        val rc = connection.controlTransfer(reqType, request, value, index, null, 0, TIMEOUT_MS)
        if (rc < 0) {
            Log.w(TAG, "CH34x controlOut failed req=0x${request.toString(16)} rc=$rc")
        }
    }

    private fun controlInCh(
        connection: UsbDeviceConnection,
        request: Int,
        value: Int,
        index: Int,
        length: Int,
    ) {
        val reqType = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_IN
        val buf = ByteArray(length)
        val rc = connection.controlTransfer(reqType, request, value, index, buf, length, TIMEOUT_MS)
        if (rc < 0) {
            Log.w(TAG, "CH34x controlIn failed req=0x${request.toString(16)} rc=$rc")
        }
    }
}
