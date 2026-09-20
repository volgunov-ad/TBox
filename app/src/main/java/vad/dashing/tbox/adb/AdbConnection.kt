package vad.dashing.tbox.adb

import java.io.EOFException
import java.io.IOException
import java.security.KeyPair

class AdbDeviceInfo(
    val banner: String,
    val features: Set<String>,
    val maxPayload: Int,
)

class AdbShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val shellV2: Boolean,
)

private class AdbMessage(
    val header: AdbMessageHeader,
    val payload: ByteArray,
)

class AdbConnection(
    private val transport: AdbTransport,
    private val keyPair: KeyPair,
    private val clientName: String,
) : AutoCloseable {

    private var connected = false
    private var peerFeatures = emptySet<String>()
    private var peerMaxPayload = AdbProtocol.MAX_PAYLOAD
    private var useChecksum = true
    private var nextLocalId = 1

    @Synchronized
    fun connect(): AdbDeviceInfo {
        check(!connected) { "ADB connection is already established" }
        send(
            AdbProtocol.CMD_CNXN,
            AdbProtocol.VERSION,
            AdbProtocol.MAX_PAYLOAD,
            "host::features=shell_v2\u0000".toByteArray(Charsets.UTF_8),
        )
        var authTokens = 0
        while (true) {
            val message = receive()
            when (message.header.command) {
                AdbProtocol.CMD_CNXN -> {
                    val banner = message.payload.toString(Charsets.UTF_8).trimEnd('\u0000')
                    peerFeatures = parseFeatures(banner)
                    peerMaxPayload = message.header.arg1.coerceIn(1, AdbProtocol.MAX_INBOUND_PAYLOAD)
                    useChecksum = message.header.arg0 < AdbProtocol.VERSION
                    connected = true
                    return AdbDeviceInfo(banner, peerFeatures, peerMaxPayload)
                }
                AdbProtocol.CMD_AUTH -> {
                    if (message.header.arg0 != AdbProtocol.AUTH_TYPE_TOKEN) {
                        throw IOException("Unexpected AUTH type ${message.header.arg0}")
                    }
                    when (authTokens++) {
                        0 -> send(
                            AdbProtocol.CMD_AUTH,
                            AdbProtocol.AUTH_TYPE_SIGNATURE,
                            0,
                            AdbAuthKeys.signToken(keyPair.private, message.payload),
                        )
                        else -> send(
                            AdbProtocol.CMD_AUTH,
                            AdbProtocol.AUTH_TYPE_RSAPUBLICKEY,
                            0,
                            AdbAuthKeys.androidPublicKeyPayload(keyPair.public, clientName),
                        )
                    }
                    if (authTokens > 8) throw IOException("ADB authentication rejected")
                }
                else -> throw IOException("Expected CNXN or AUTH, got ${AdbProtocol.commandName(message.header.command)}")
            }
        }
    }

    @Synchronized
    fun execute(command: String): AdbShellResult {
        check(connected) { "ADB connection is not established" }
        require(command.isNotBlank()) { "command is blank" }
        if ("shell_v2" in peerFeatures) {
            val result = executeV2(command)
            if (result != null) return result
        }
        return executeLegacy(command)
    }

    override fun close() {
        connected = false
        transport.close()
    }

    /** Physical USB DETACH: never touch the native USB handle (OEM crash risk). */
    fun abandonUsb() {
        connected = false
        val usb = transport as? AdbUsbTransport
        if (usb != null) {
            usb.abandon()
        } else {
            runCatching { transport.close() }
        }
    }

    private fun executeV2(command: String): AdbShellResult? {
        val ids = open(AdbShellV2.service(command)) ?: return null
        val parser = AdbShellV2Parser()
        val stdout = ArrayList<ByteArray>()
        val stderr = ArrayList<ByteArray>()
        var exitCode: Int? = null
        while (true) {
            val message = receive()
            validateStream(message, ids)
            when (message.header.command) {
                AdbProtocol.CMD_WRTE -> {
                    for (chunk in parser.feed(message.payload)) {
                        when (chunk.id) {
                            AdbShellV2.CHUNK_STDOUT -> stdout.add(chunk.data)
                            AdbShellV2.CHUNK_STDERR -> stderr.add(chunk.data)
                            AdbShellV2.CHUNK_EXIT -> if (chunk.data.isNotEmpty()) {
                                exitCode = chunk.data[0].toInt() and 0xFF
                            }
                        }
                    }
                    send(AdbProtocol.CMD_OKAY, ids.local, ids.remote)
                }
                AdbProtocol.CMD_CLSE -> {
                    send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
                    return AdbShellResult(
                        concat(stdout).toString(Charsets.UTF_8),
                        concat(stderr).toString(Charsets.UTF_8),
                        exitCode,
                        true,
                    )
                }
                AdbProtocol.CMD_OKAY -> Unit
                else -> throw IOException("Unexpected shell message ${AdbProtocol.commandName(message.header.command)}")
            }
        }
    }

    private fun executeLegacy(command: String): AdbShellResult {
        val ids = open(AdbShellV2.legacyService(command))
            ?: throw IOException("ADB shell service rejected")
        val output = ArrayList<ByteArray>()
        while (true) {
            val message = receive()
            validateStream(message, ids)
            when (message.header.command) {
                AdbProtocol.CMD_WRTE -> {
                    output.add(message.payload)
                    send(AdbProtocol.CMD_OKAY, ids.local, ids.remote)
                }
                AdbProtocol.CMD_CLSE -> {
                    send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
                    return AdbShellResult(
                        concat(output).toString(Charsets.UTF_8),
                        "",
                        null,
                        false,
                    )
                }
                AdbProtocol.CMD_OKAY -> Unit
                else -> throw IOException("Unexpected shell message ${AdbProtocol.commandName(message.header.command)}")
            }
        }
    }

    private fun open(service: String): StreamIds? {
        val localId = nextLocalId++
        send(AdbProtocol.CMD_OPEN, localId, 0, service.toByteArray(Charsets.UTF_8))
        while (true) {
            val message = receive()
            when (message.header.command) {
                AdbProtocol.CMD_OKAY -> {
                    if (message.header.arg1 != localId) {
                        throw IOException("ADB OPEN local id mismatch")
                    }
                    return StreamIds(localId, message.header.arg0)
                }
                AdbProtocol.CMD_CLSE -> {
                    if (message.header.arg1 != localId) {
                        throw IOException("ADB CLSE local id mismatch")
                    }
                    send(AdbProtocol.CMD_CLSE, localId, message.header.arg0)
                    return null
                }
                else -> throw IOException("Expected OKAY or CLSE, got ${AdbProtocol.commandName(message.header.command)}")
            }
        }
    }

    private fun validateStream(message: AdbMessage, ids: StreamIds) {
        if (message.header.arg0 != ids.remote || message.header.arg1 != ids.local) {
            throw IOException("ADB stream id mismatch")
        }
    }

    private fun send(command: Int, arg0: Int, arg1: Int, payload: ByteArray = ByteArray(0)) {
        if (payload.size > peerMaxPayload && connected) {
            throw IOException("ADB payload ${payload.size} exceeds peer maximum $peerMaxPayload")
        }
        val packet = AdbProtocol.encode(
            command,
            arg0,
            arg1,
            payload,
            useChecksum || command == AdbProtocol.CMD_CNXN,
        )
        transport.writePacket(packet)
    }

    private fun receive(): AdbMessage {
        val headerBytes = ByteArray(AdbProtocol.HEADER_SIZE)
        readFully(headerBytes)
        val header = AdbProtocol.decodeHeader(headerBytes)
        val payload = ByteArray(header.dataLength)
        readFully(payload)
        if (header.dataCheck != 0 && !AdbProtocol.verifyPayload(header, payload)) {
            throw IOException("Bad ADB payload checksum")
        }
        return AdbMessage(header, payload)
    }

    private fun readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = transport.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw EOFException("ADB transport closed")
            if (count == 0) throw IOException("ADB transport returned no data")
            offset += count
        }
    }

    private fun parseFeatures(banner: String): Set<String> {
        val properties = banner.substringAfter("::", "")
        return properties.split(';')
            .firstOrNull { it.startsWith("features=") }
            ?.removePrefix("features=")
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    private fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            System.arraycopy(part, 0, out, offset, part.size)
            offset += part.size
        }
        return out
    }

    private class StreamIds(val local: Int, val remote: Int)
}
