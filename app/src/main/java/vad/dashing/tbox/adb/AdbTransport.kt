package vad.dashing.tbox.adb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

interface AdbTransport : Closeable {
    val description: String

    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun write(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size)

    fun writePacket(packet: ByteArray) {
        write(packet)
    }
}

class AdbTcpTransport private constructor(
    private val socket: Socket,
    override val description: String,
) : AdbTransport {

    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        input.read(buffer, offset, length)

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        output.write(buffer, offset, length)
        output.flush()
    }

    override fun close() {
        socket.close()
    }

    companion object {
        fun connect(host: String, port: Int, timeoutMs: Int = 10_000): AdbTcpTransport {
            require(host.isNotBlank()) { "host is blank" }
            require(port in 1..65535) { "port is out of range" }
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                socket.tcpNoDelay = true
                return AdbTcpTransport(socket, "$host:$port")
            } catch (error: Exception) {
                runCatching { socket.close() }
                throw error
            }
        }
    }
}

class AdbUsbTransport private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val inputEndpoint: UsbEndpoint,
    private val outputEndpoint: UsbEndpoint,
    override val description: String,
    private val timeoutMs: Int,
) : AdbTransport {

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = connection.bulkTransfer(inputEndpoint, buffer, offset, length, timeoutMs)
        if (count < 0) throw SocketTimeoutException("USB read timed out")
        return count
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        writeTransfer(buffer, offset, length)
    }

    override fun writePacket(packet: ByteArray) {
        require(packet.size >= AdbProtocol.HEADER_SIZE) { "ADB packet is too short" }
        writeTransfer(packet, 0, AdbProtocol.HEADER_SIZE)
        if (packet.size > AdbProtocol.HEADER_SIZE) {
            writeTransfer(
                packet,
                AdbProtocol.HEADER_SIZE,
                packet.size - AdbProtocol.HEADER_SIZE,
            )
        }
    }

    override fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        connection.close()
    }

    private fun writeTransfer(buffer: ByteArray, offset: Int, length: Int) {
        var written = 0
        while (written < length) {
            val chunkLength = minOf(length - written, USB_WRITE_CHUNK)
            val chunk = buffer.copyOfRange(offset + written, offset + written + chunkLength)
            val count = connection.bulkTransfer(outputEndpoint, chunk, chunk.size, timeoutMs)
            if (count <= 0) throw IOException("USB write failed: $count")
            written += count
        }
    }

    companion object {
        private const val USB_WRITE_CHUNK = 16 * 1024

        fun isAdbDevice(device: UsbDevice): Boolean =
            findInterface(device) != null

        fun open(usbManager: UsbManager, device: UsbDevice, timeoutMs: Int = 10_000): AdbUsbTransport {
            val found = findInterface(device)
                ?: throw IOException("ADB USB interface not found")
            val connection = usbManager.openDevice(device)
                ?: throw IOException("Unable to open USB device")
            if (!connection.claimInterface(found.usbInterface, true)) {
                connection.close()
                throw IOException("Unable to claim ADB USB interface")
            }
            return AdbUsbTransport(
                connection,
                found.usbInterface,
                found.input,
                found.output,
                device.deviceName,
                timeoutMs,
            )
        }

        private fun findInterface(device: UsbDevice): AdbUsbInterface? {
            for (index in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(index)
                if (usbInterface.interfaceClass != 0xFF ||
                    usbInterface.interfaceSubclass != 0x42 ||
                    usbInterface.interfaceProtocol != 0x01
                ) {
                    continue
                }
                var input: UsbEndpoint? = null
                var output: UsbEndpoint? = null
                for (endpointIndex in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(endpointIndex)
                    if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) input = endpoint
                    if (endpoint.direction == UsbConstants.USB_DIR_OUT) output = endpoint
                }
                if (input != null && output != null) {
                    return AdbUsbInterface(usbInterface, input, output)
                }
            }
            return null
        }

        private class AdbUsbInterface(
            val usbInterface: UsbInterface,
            val input: UsbEndpoint,
            val output: UsbEndpoint,
        )
    }
}
