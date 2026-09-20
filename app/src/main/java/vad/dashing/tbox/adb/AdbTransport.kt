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
    private val deviceId: Int,
    private val sharesNetworkWithHost: Boolean,
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
        if (sharesNetworkWithHost) {
            // Keep the usbfs FD open. On this HU, closing UsbDeviceConnection on the
            // TBox composite (RNDIS+ADB) unbinds system RNDIS and drops UDP to the TBox.
            // Bugjaeger-style USB clients keep the host handle across disconnect cycles.
            parkNetworkConnection(deviceId, connection)
            return
        }
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

        /** ADB class / subclass / protocol (Android USB debug interface). */
        const val ADB_CLASS = 0xFF
        const val ADB_SUBCLASS = 0x42
        const val ADB_PROTOCOL = 0x01

        private val holdLock = Any()
        private var heldNetworkDeviceId: Int? = null
        private var heldNetworkConnection: UsbDeviceConnection? = null

        fun isAdbDevice(device: UsbDevice): Boolean =
            findInterface(device) != null

        /**
         * True when the same USB device also exposes RNDIS / CDC-networking interfaces.
         * TBox (Neoway) is such a composite: careless open/close of the usbfs handle can
         * drop the system RNDIS link used for UDP to 192.168.225.1.
         */
        fun sharesNetworkWithHost(device: UsbDevice): Boolean {
            for (index in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(index)
                if (isNetworkInterface(
                        usbInterface.interfaceClass,
                        usbInterface.interfaceSubclass,
                        usbInterface.interfaceProtocol,
                    )
                ) {
                    return true
                }
            }
            return false
        }

        fun isNetworkInterface(interfaceClass: Int, interfaceSubclass: Int, interfaceProtocol: Int = 0): Boolean {
            // interfaceProtocol reserved for future RNDIS variants; wireless class is enough today.
            if (interfaceClass == UsbConstants.USB_CLASS_WIRELESS_CONTROLLER) return true
            if (interfaceClass == UsbConstants.USB_CLASS_COMM) {
                // ECM / NCM / MBIM — networking control. Abstract Control (0x02) alone is not.
                when (interfaceSubclass) {
                    0x06, 0x0D, 0x0E -> return true
                }
            }
            return false
        }

        fun open(usbManager: UsbManager, device: UsbDevice, timeoutMs: Int = 10_000): AdbUsbTransport {
            val found = findInterface(device)
                ?: throw IOException("ADB USB interface not found")
            val sharesNetwork = sharesNetworkWithHost(device)
            val connection = openConnection(usbManager, device, sharesNetwork)
            // Prefer force=false so we never usb_detach_kernel_driver on sibling RNDIS
            // interfaces (force=true has wedged TBox networking on this HU).
            val claimed = connection.claimInterface(found.usbInterface, false) ||
                (!sharesNetwork && connection.claimInterface(found.usbInterface, true))
            if (!claimed) {
                discardConnection(device.deviceId, connection, sharesNetwork)
                throw IOException("Unable to claim ADB USB interface")
            }
            return AdbUsbTransport(
                connection,
                found.usbInterface,
                found.input,
                found.output,
                device.deviceName,
                timeoutMs,
                device.deviceId,
                sharesNetwork,
            )
        }

        private fun openConnection(
            usbManager: UsbManager,
            device: UsbDevice,
            sharesNetwork: Boolean,
        ): UsbDeviceConnection {
            if (sharesNetwork) {
                val reused = takeParkedNetworkConnection(device.deviceId)
                if (reused != null) return reused
            } else {
                // Switching away from a parked TBox handle — drop it only when opening
                // a different, non-network ADB gadget so we do not leak FDs forever.
                clearParkedNetworkConnection()
            }
            return usbManager.openDevice(device)
                ?: throw IOException("Unable to open USB device")
        }

        private fun discardConnection(
            deviceId: Int,
            connection: UsbDeviceConnection,
            sharesNetwork: Boolean,
        ) {
            if (sharesNetwork) {
                parkNetworkConnection(deviceId, connection)
            } else {
                connection.close()
            }
        }

        private fun parkNetworkConnection(deviceId: Int, connection: UsbDeviceConnection) {
            synchronized(holdLock) {
                val previous = heldNetworkConnection
                val previousId = heldNetworkDeviceId
                if (previous != null && previous !== connection) {
                    runCatching { previous.close() }
                }
                heldNetworkDeviceId = deviceId
                heldNetworkConnection = connection
                if (previousId != null && previousId != deviceId) {
                    // replaced a different device handle
                }
            }
        }

        private fun takeParkedNetworkConnection(deviceId: Int): UsbDeviceConnection? {
            synchronized(holdLock) {
                if (heldNetworkDeviceId != deviceId) return null
                val connection = heldNetworkConnection
                heldNetworkDeviceId = null
                heldNetworkConnection = null
                return connection
            }
        }

        private fun clearParkedNetworkConnection() {
            synchronized(holdLock) {
                val previous = heldNetworkConnection
                heldNetworkDeviceId = null
                heldNetworkConnection = null
                runCatching { previous?.close() }
            }
        }

        /** Drop a parked handle after physical DETACH (FD is already dead). */
        fun discardParkedNetworkConnection(deviceId: Int) {
            synchronized(holdLock) {
                if (heldNetworkDeviceId != deviceId) return
                heldNetworkDeviceId = null
                heldNetworkConnection = null
                // Do not close — kernel already invalidated the device.
            }
        }

        private fun findInterface(device: UsbDevice): AdbUsbInterface? {
            for (index in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(index)
                if (usbInterface.interfaceClass != ADB_CLASS ||
                    usbInterface.interfaceSubclass != ADB_SUBCLASS ||
                    usbInterface.interfaceProtocol != ADB_PROTOCOL
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
