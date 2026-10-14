package vad.dashing.tbox.adb

import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
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

/** How a long-running [AdbConnection.streamShell] ended. */
sealed class AdbShellStreamEnd {
    data class Completed(val exitCode: Int?, val shellV2: Boolean) : AdbShellStreamEnd()
    data object Stopped : AdbShellStreamEnd()
}

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
    /** When set, [readFully] retries idle timeouts so [streamShell] can honor [shouldStop]. */
    @Volatile private var streamStopCheck: (() -> Boolean)? = null

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

    /**
     * Streams a long-running shell command (e.g. `logcat`) until the peer closes the
     * stream, [shouldStop] returns true, or I/O fails.
     *
     * While streaming, the transport read timeout is shortened so Stop can close the
     * shell with CLSE without waiting for the default 10 s idle timeout.
     */
    @Synchronized
    fun streamShell(
        command: String,
        shouldStop: () -> Boolean,
        onStdout: (ByteArray) -> Unit,
        onStderr: (ByteArray) -> Unit = {},
        readTimeoutMs: Int = STREAM_READ_TIMEOUT_MS,
    ): AdbShellStreamEnd {
        check(connected) { "ADB connection is not established" }
        require(command.isNotBlank()) { "command is blank" }
        val previousTimeout = STREAM_DEFAULT_TIMEOUT_MS
        transport.setReadTimeoutMs(readTimeoutMs.coerceAtLeast(1))
        streamStopCheck = shouldStop
        try {
            if ("shell_v2" in peerFeatures) {
                val end = streamV2(command, shouldStop, onStdout, onStderr)
                if (end != null) return end
            }
            return streamLegacy(command, shouldStop, onStdout)
        } finally {
            streamStopCheck = null
            transport.setReadTimeoutMs(previousTimeout)
        }
    }

    override fun close() {
        connected = false
        transport.close()
    }

    companion object {
        private const val STREAM_READ_TIMEOUT_MS = 500
        private const val STREAM_DEFAULT_TIMEOUT_MS = 10_000
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
            if (!belongsToStream(message, ids)) {
                drainStale(message)
                continue
            }
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
            if (!belongsToStream(message, ids)) {
                drainStale(message)
                continue
            }
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

    private fun streamV2(
        command: String,
        shouldStop: () -> Boolean,
        onStdout: (ByteArray) -> Unit,
        onStderr: (ByteArray) -> Unit,
    ): AdbShellStreamEnd? {
        val ids = open(AdbShellV2.service(command)) ?: return null
        val parser = AdbShellV2Parser()
        var exitCode: Int? = null
        var stopRequested = false
        while (true) {
            if (!stopRequested && shouldStop()) {
                stopRequested = true
                // Further idle timeouts only retry receive (wait for peer CLSE).
                streamStopCheck = null
                send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
            }
            val message = try {
                receive()
            } catch (_: AdbStreamStopRequested) {
                if (!stopRequested) {
                    stopRequested = true
                    streamStopCheck = null
                    send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
                }
                continue
            }
            if (!belongsToStream(message, ids)) {
                drainStale(message)
                continue
            }
            when (message.header.command) {
                AdbProtocol.CMD_WRTE -> {
                    for (chunk in parser.feed(message.payload)) {
                        when (chunk.id) {
                            AdbShellV2.CHUNK_STDOUT -> if (chunk.data.isNotEmpty()) onStdout(chunk.data)
                            AdbShellV2.CHUNK_STDERR -> if (chunk.data.isNotEmpty()) onStderr(chunk.data)
                            AdbShellV2.CHUNK_EXIT -> if (chunk.data.isNotEmpty()) {
                                exitCode = chunk.data[0].toInt() and 0xFF
                            }
                        }
                    }
                    send(AdbProtocol.CMD_OKAY, ids.local, ids.remote)
                }
                AdbProtocol.CMD_CLSE -> {
                    send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
                    return if (stopRequested) {
                        AdbShellStreamEnd.Stopped
                    } else {
                        AdbShellStreamEnd.Completed(exitCode, shellV2 = true)
                    }
                }
                AdbProtocol.CMD_OKAY -> Unit
                else -> throw IOException(
                    "Unexpected shell message ${AdbProtocol.commandName(message.header.command)}",
                )
            }
        }
    }

    private fun streamLegacy(
        command: String,
        shouldStop: () -> Boolean,
        onStdout: (ByteArray) -> Unit,
    ): AdbShellStreamEnd {
        val ids = open(AdbShellV2.legacyService(command))
            ?: throw IOException("ADB shell service rejected")
        var stopRequested = false
        while (true) {
            if (!stopRequested && shouldStop()) {
                stopRequested = true
                streamStopCheck = null
                send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
            }
            val message = try {
                receive()
            } catch (_: AdbStreamStopRequested) {
                if (!stopRequested) {
                    stopRequested = true
                    streamStopCheck = null
                    send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
                }
                continue
            }
            if (!belongsToStream(message, ids)) {
                drainStale(message)
                continue
            }
            when (message.header.command) {
                AdbProtocol.CMD_WRTE -> {
                    if (message.payload.isNotEmpty()) onStdout(message.payload)
                    send(AdbProtocol.CMD_OKAY, ids.local, ids.remote)
                }
                AdbProtocol.CMD_CLSE -> {
                    send(AdbProtocol.CMD_CLSE, ids.local, ids.remote)
                    return if (stopRequested) {
                        AdbShellStreamEnd.Stopped
                    } else {
                        AdbShellStreamEnd.Completed(exitCode = null, shellV2 = false)
                    }
                }
                AdbProtocol.CMD_OKAY -> Unit
                else -> throw IOException(
                    "Unexpected shell message ${AdbProtocol.commandName(message.header.command)}",
                )
            }
        }
    }

    private class AdbStreamStopRequested : IOException("ADB shell stream stop requested")

    /**
     * Opens a stream and waits for OKAY/CLSE addressed to [localId].
     *
     * Per ADB protocol, READY/CLOSE for a stream that is already closed (or never opened
     * on our side) must be ignored — late frames from a previous shell are common on
     * TCP/`adbd` after sequential `execute()` (grant-all). Throwing on id mismatch caused
     * `ADB CLSE local id mismatch` even with a single client and no ADB tab session.
     */
    private fun open(service: String): StreamIds? {
        val localId = nextLocalId++
        send(AdbProtocol.CMD_OPEN, localId, 0, service.toByteArray(Charsets.UTF_8))
        while (true) {
            val message = receive()
            when (message.header.command) {
                AdbProtocol.CMD_OKAY -> {
                    if (message.header.arg1 != localId) continue
                    return StreamIds(localId, message.header.arg0)
                }
                AdbProtocol.CMD_CLSE -> {
                    if (message.header.arg1 != localId) continue
                    send(AdbProtocol.CMD_CLSE, localId, message.header.arg0)
                    return null
                }
                AdbProtocol.CMD_WRTE -> {
                    // Stale write from a prior stream — ACK with its ids, then keep waiting.
                    if (message.header.arg1 != localId) {
                        send(AdbProtocol.CMD_OKAY, message.header.arg1, message.header.arg0)
                        continue
                    }
                    throw IOException("Unexpected WRTE during OPEN for local id $localId")
                }
                else -> throw IOException(
                    "Expected OKAY or CLSE, got ${AdbProtocol.commandName(message.header.command)}",
                )
            }
        }
    }

    private fun belongsToStream(message: AdbMessage, ids: StreamIds): Boolean =
        message.header.arg0 == ids.remote && message.header.arg1 == ids.local

    private fun drainStale(message: AdbMessage) {
        when (message.header.command) {
            AdbProtocol.CMD_WRTE ->
                send(AdbProtocol.CMD_OKAY, message.header.arg1, message.header.arg0)
            AdbProtocol.CMD_OKAY, AdbProtocol.CMD_CLSE -> Unit
            else -> throw IOException(
                "Unexpected stale ${AdbProtocol.commandName(message.header.command)}",
            )
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
            val count = try {
                transport.read(buffer, offset, buffer.size - offset)
            } catch (e: SocketTimeoutException) {
                // Idle timeout with no bytes of this frame yet: honor stream stop, else retry.
                if (offset == 0) {
                    if (streamStopCheck?.invoke() == true) throw AdbStreamStopRequested()
                    continue
                }
                throw e
            }
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
