package vad.dashing.tbox.adb

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbMessageHeader(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val dataLength: Int,
    val dataCheck: Int,
) {
    val magic: Int get() = AdbProtocol.magic(command)
}

object AdbProtocol {

    private val emptyPayload = ByteArray(0)

    const val HEADER_SIZE = 24
    const val MAX_PAYLOAD = 4096
    const val MAX_INBOUND_PAYLOAD = 1024 * 1024
    const val VERSION = 0x01000001

    const val CMD_SYNC = 0x434E5953
    const val CMD_CNXN = 0x4E584E43
    const val CMD_AUTH = 0x48545541
    const val CMD_OPEN = 0x4E45504F
    const val CMD_OKAY = 0x59414B4F
    const val CMD_CLSE = 0x45534C43
    const val CMD_WRTE = 0x45545257

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSAPUBLICKEY = 3

    fun magic(command: Int): Int = command.inv()

    fun checksum(data: ByteArray): Int = data.sumOf { it.toInt() and 0xFF }

    fun encode(
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray = emptyPayload,
        useChecksum: Boolean = true,
    ): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload ${payload.size} > $MAX_PAYLOAD" }
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(payload.size)
        buffer.putInt(if (useChecksum) checksum(payload) else 0)
        buffer.putInt(magic(command))
        buffer.put(payload)
        return buffer.array()
    }

    fun decodeHeader(bytes: ByteArray): AdbMessageHeader {
        if (bytes.size < HEADER_SIZE) throw IOException("Short ADB header: ${bytes.size}")
        val buffer = ByteBuffer.wrap(bytes, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val dataCheck = buffer.int
        val magicValue = buffer.int
        if (magicValue != magic(command)) {
            throw IOException("Bad ADB magic for ${commandName(command)}")
        }
        if (dataLength < 0 || dataLength > MAX_INBOUND_PAYLOAD) {
            throw IOException("Bad ADB payload length: $dataLength")
        }
        return AdbMessageHeader(command, arg0, arg1, dataLength, dataCheck)
    }

    fun verifyPayload(header: AdbMessageHeader, payload: ByteArray): Boolean =
        payload.size == header.dataLength && checksum(payload) == header.dataCheck

    fun commandName(command: Int): String {
        val chars = byteArrayOf(
            (command and 0xFF).toByte(),
            ((command ushr 8) and 0xFF).toByte(),
            ((command ushr 16) and 0xFF).toByte(),
            ((command ushr 24) and 0xFF).toByte(),
        )
        return if (chars.all { it.toInt() in 0x20..0x7E }) {
            String(chars, Charsets.US_ASCII)
        } else {
            "0x%08X".format(command)
        }
    }
}

object AdbShellV2 {

    const val CHUNK_STDIN = 0
    const val CHUNK_STDOUT = 1
    const val CHUNK_STDERR = 2
    const val CHUNK_EXIT = 3
    const val CHUNK_CLOSE_STDIN = 4

    const val MAX_CHUNK = 1024 * 1024

    fun service(command: String): String = "shell,v2:$command\u0000"

    fun legacyService(command: String): String = "shell:$command\u0000"
}

class AdbShellV2Chunk(
    val id: Int,
    val data: ByteArray,
)

class AdbShellV2Parser {

    private var buffer = ByteArray(8192)
    private var readPos = 0
    private var writePos = 0

    fun feed(src: ByteArray, length: Int = src.size): List<AdbShellV2Chunk> {
        require(length in 0..src.size)
        compact()
        ensureCapacity(writePos + length)
        System.arraycopy(src, 0, buffer, writePos, length)
        writePos += length
        val chunks = ArrayList<AdbShellV2Chunk>()
        while (true) {
            val available = writePos - readPos
            if (available < 5) break
            val chunkLength = readIntLE(readPos + 1)
            if (chunkLength < 0 || chunkLength > AdbShellV2.MAX_CHUNK) {
                throw IOException("Bad shell v2 chunk length: $chunkLength")
            }
            if (available < 5 + chunkLength) break
            val id = buffer[readPos].toInt() and 0xFF
            val data = buffer.copyOfRange(readPos + 5, readPos + 5 + chunkLength)
            readPos += 5 + chunkLength
            chunks.add(AdbShellV2Chunk(id, data))
        }
        return chunks
    }

    private fun compact() {
        if (readPos == writePos) {
            readPos = 0
            writePos = 0
        } else if (readPos > 0) {
            System.arraycopy(buffer, readPos, buffer, 0, writePos - readPos)
            writePos -= readPos
            readPos = 0
        }
    }

    private fun ensureCapacity(needed: Int) {
        if (buffer.size >= needed) return
        var size = buffer.size
        while (size < needed) size = size shl 1
        buffer = buffer.copyOf(size)
    }

    private fun readIntLE(offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24)
}
