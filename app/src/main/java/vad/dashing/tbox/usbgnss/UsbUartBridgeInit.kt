package vad.dashing.tbox.usbgnss

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log

/**
 * Vendor UART init for common USB-UART bridges.
 *
 * CDC ACM uses standard SET_LINE_CODING; these chips need vendor control transfers
 * (aligned with usb-serial-for-android Cp21xx / Ch34x / Ftdi / Prolific drivers).
 */
object UsbUartBridgeInit {
    private const val TAG = "UsbUartBridgeInit"
    private const val TIMEOUT_MS = 5_000

    const val VID_SILABS = 0x10C4
    const val VID_QINHENG = 0x1A86
    const val VID_FTDI = 0x0403
    const val VID_PROLIFIC = 0x067B

    /** FTDI prepends 2 modem-status bytes to every bulk IN packet. */
    const val FTDI_STATUS_HEADER_LEN = 2

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
            VID_FTDI -> {
                initFtdi(device, connection, interfaceId, baud)
                true
            }
            VID_PROLIFIC -> {
                initProlific(connection, baud)
                true
            }
            else -> false
        }
    }

    fun needsFtdiStatusFilter(vendorId: Int): Boolean =
        (vendorId and 0xFFFF) == VID_FTDI

    /**
     * Strip FTDI 2-byte status headers from a bulk IN buffer (unit-testable).
     */
    fun filterFtdiStatusBytes(src: ByteArray, length: Int, maxPacketSize: Int): ByteArray {
        if (length <= 0 || maxPacketSize <= FTDI_STATUS_HEADER_LEN) {
            return ByteArray(0)
        }
        val out = ByteArray(length)
        var dest = 0
        var srcPos = 0
        while (srcPos < length) {
            val chunkEnd = minOf(srcPos + maxPacketSize, length)
            val dataStart = srcPos + FTDI_STATUS_HEADER_LEN
            if (dataStart > chunkEnd) break
            val n = chunkEnd - dataStart
            if (n > 0) {
                System.arraycopy(src, dataStart, out, dest, n)
                dest += n
            }
            srcPos = chunkEnd
        }
        return if (dest == out.size) out else out.copyOf(dest)
    }

    fun initCp210x(connection: UsbDeviceConnection, portNumber: Int, baud: Int) {
        controlOutCp(connection, request = 0x00, value = 0x0001, index = portNumber, data = null)
        controlOutCp(connection, request = 0x07, value = 0x0101 or 0x0202, index = portNumber, data = null)
        setCp210xBaud(connection, portNumber, baud)
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
        val rc = connection.controlTransfer(0x41, 0x1E, 0, portNumber, data, data.size, TIMEOUT_MS)
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
        val rc = connection.controlTransfer(0x41, request, value, index, data, len, TIMEOUT_MS)
        if (rc < 0) {
            Log.w(TAG, "CP210x control failed req=0x${request.toString(16)} rc=$rc")
        }
    }

    fun initCh34x(connection: UsbDeviceConnection, baud: Int) {
        controlInCh(connection, request = 0x5f, value = 0, index = 0, length = 2)
        controlOutCh(connection, request = 0xa1, value = 0, index = 0)
        setCh34xBaud(connection, baud)
        controlInCh(connection, request = 0x95, value = 0x2518, index = 0, length = 2)
        controlOutCh(connection, request = 0x9a, value = 0x2518, index = 0x80 or 0x40 or 0x03)
        controlInCh(connection, request = 0x95, value = 0x0706, index = 0, length = 2)
        controlOutCh(connection, request = 0xa1, value = 0x501f, index = 0xd90a)
        setCh34xBaud(connection, baud)
        controlOutCh(connection, request = 0xa4, value = ((0x20 or 0x40).inv()) and 0xFFFF, index = 0)
        Log.i(TAG, "CH34x init baud=$baud")
    }

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

    fun initFtdi(
        device: UsbDevice,
        connection: UsbDeviceConnection,
        interfaceId: Int,
        baud: Int,
    ) {
        val portIndex = interfaceId + 1
        val reqOut = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_OUT
        // RESET_ALL
        controlVendor(connection, reqOut, request = 0, value = 0, index = portIndex, data = null)
        // DTR+RTS enable
        controlVendor(
            connection,
            reqOut,
            request = 1,
            value = 0x0101 or 0x0202,
            index = portIndex,
            data = null,
        )
        val (baudValue, baudIndexBase) = encodeFtdiBaud(baud)
        val baudIndex = if (device.interfaceCount > 1) {
            (baudIndexBase shl 8) or portIndex
        } else {
            baudIndexBase
        }
        controlVendor(
            connection,
            reqOut,
            request = 3,
            value = baudValue,
            index = baudIndex,
            data = null,
        )
        // 8 data bits, no parity, 1 stop
        controlVendor(connection, reqOut, request = 4, value = 8, index = portIndex, data = null)
        Log.i(TAG, "FTDI init baud=$baud if=$interfaceId")
    }

    /**
     * FTDI divisor encoding for common baud rates (unit-testable).
     * @return Pair(value, indexBase) for SET_BAUD_RATE (request 3).
     */
    fun encodeFtdiBaud(baud: Int): Pair<Int, Int> {
        if (baud <= 0) throw IllegalArgumentException("Invalid FTDI baud: $baud")
        if (baud >= 2_500_000) return 0 to 0
        if (baud >= 1_750_000) return 1 to 0
        var divisor = (24_000_000 shl 1) / baud
        divisor = (divisor + 1) shr 1
        val subdivisor = divisor and 0x07
        divisor = divisor shr 3
        if (divisor > 0x3fff) {
            throw IllegalArgumentException("FTDI baud too low: $baud")
        }
        var value = divisor
        var index = 0
        when (subdivisor) {
            0 -> Unit
            4 -> value = value or 0x4000
            2 -> value = value or 0x8000
            1 -> value = value or 0xc000
            3 -> index = 1
            5 -> {
                value = value or 0x4000
                index = 1
            }
            6 -> {
                value = value or 0x8000
                index = 1
            }
            7 -> {
                value = value or 0xc000
                index = 1
            }
        }
        return value to index
    }

    /**
     * Minimal Prolific PL2303 HX-style init + CDC-like line coding.
     * Covers common USB-GPS dongles; exotic HXN variants may still need a dedicated driver.
     */
    fun initProlific(connection: UsbDeviceConnection, baud: Int) {
        prolificBlackMagicHx(connection)
        // SET_CONTROL_LINE_STATE DTR|RTS (class request to interface, same as CDC 0x21)
        val ctrlType = 0x21
        controlVendor(connection, ctrlType, request = 0x22, value = 0x01 or 0x02, index = 0, data = null)
        val line = ByteArray(7)
        line[0] = (baud and 0xff).toByte()
        line[1] = ((baud shr 8) and 0xff).toByte()
        line[2] = ((baud shr 16) and 0xff).toByte()
        line[3] = ((baud shr 24) and 0xff).toByte()
        line[4] = 0 // 1 stop
        line[5] = 0 // no parity
        line[6] = 8 // 8 data bits
        controlVendor(connection, ctrlType, request = 0x20, value = 0, index = 0, data = line)
        Log.i(TAG, "Prolific init baud=$baud")
    }

    private fun prolificBlackMagicHx(connection: UsbDeviceConnection) {
        val inType = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_IN
        val outType = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_OUT
        vendorInProlific(connection, inType, 0x8484, 0, 1)
        vendorOutProlific(connection, outType, 0x0404, 0)
        vendorInProlific(connection, inType, 0x8484, 0, 1)
        vendorInProlific(connection, inType, 0x8383, 0, 1)
        vendorInProlific(connection, inType, 0x8484, 0, 1)
        vendorOutProlific(connection, outType, 0x0404, 1)
        vendorInProlific(connection, inType, 0x8484, 0, 1)
        vendorInProlific(connection, inType, 0x8383, 0, 1)
        vendorOutProlific(connection, outType, 0, 1)
        vendorOutProlific(connection, outType, 1, 0)
        vendorOutProlific(connection, outType, 2, 0x44)
    }

    private fun vendorInProlific(
        connection: UsbDeviceConnection,
        reqType: Int,
        value: Int,
        index: Int,
        length: Int,
    ) {
        val buf = ByteArray(length)
        connection.controlTransfer(reqType, 0x01, value, index, buf, length, TIMEOUT_MS)
    }

    private fun vendorOutProlific(
        connection: UsbDeviceConnection,
        reqType: Int,
        value: Int,
        index: Int,
    ) {
        connection.controlTransfer(reqType, 0x01, value, index, null, 0, TIMEOUT_MS)
    }

    private fun controlVendor(
        connection: UsbDeviceConnection,
        reqType: Int,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
    ) {
        val len = data?.size ?: 0
        val rc = connection.controlTransfer(reqType, request, value, index, data, len, TIMEOUT_MS)
        if (rc < 0) {
            Log.w(
                TAG,
                "vendor ctrl failed type=0x${reqType.toString(16)} " +
                    "req=0x${request.toString(16)} rc=$rc",
            )
        }
    }
}
