package vad.dashing.dataresize.gpt

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

const val SECTOR = 512

class GptException(message: String) : Exception(message)

class GptHeader(
    val headerSize: Int,
    val headerCrc: Long,
    val currentLba: Long,
    val backupLba: Long,
    val firstUsable: Long,
    val lastUsable: Long,
    val entriesLba: Long,
    val entryCount: Int,
    val entrySize: Int,
    val entriesCrc: Long,
)

data class GptEntry(
    val index: Int,
    val first: Long,
    val last: Long,
    val name: String,
) {
    val sectors: Long get() = last - first + 1
}

object Gpt {
    private val SIGNATURE = "EFI PART".toByteArray(Charsets.US_ASCII)
    const val OFF_HEADER_CRC = 16
    const val OFF_ENTRIES_CRC = 88
    const val ENTRY_OFF_FIRST = 32

    private fun le(buf: ByteArray): ByteBuffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

    fun parseHeader(buf: ByteArray, off: Int): GptHeader {
        if (off + 92 > buf.size) throw GptException("GPT header out of range")
        for (i in SIGNATURE.indices) {
            if (buf[off + i] != SIGNATURE[i]) throw GptException("no GPT signature")
        }
        val b = le(buf)
        val header = GptHeader(
            headerSize = b.getInt(off + 12),
            headerCrc = b.getInt(off + OFF_HEADER_CRC).toLong() and 0xFFFFFFFFL,
            currentLba = b.getLong(off + 24),
            backupLba = b.getLong(off + 32),
            firstUsable = b.getLong(off + 40),
            lastUsable = b.getLong(off + 48),
            entriesLba = b.getLong(off + 72),
            entryCount = b.getInt(off + 80),
            entrySize = b.getInt(off + 84),
            entriesCrc = b.getInt(off + OFF_ENTRIES_CRC).toLong() and 0xFFFFFFFFL,
        )
        if (header.headerSize !in 92..SECTOR) throw GptException("bad GPT header size ${header.headerSize}")
        if (headerCrc(buf, off, header.headerSize) != header.headerCrc) throw GptException("bad GPT header CRC")
        if (header.entrySize < 128 || header.entrySize % 128 != 0 || header.entryCount !in 1..1024) {
            throw GptException("bad GPT entry layout ${header.entryCount} x ${header.entrySize}")
        }
        return header
    }

    fun crc32(buf: ByteArray, off: Int = 0, len: Int = buf.size): Long =
        CRC32().apply { update(buf, off, len) }.value

    fun headerCrc(buf: ByteArray, off: Int, size: Int): Long {
        val copy = buf.copyOfRange(off, off + size)
        for (i in OFF_HEADER_CRC until OFF_HEADER_CRC + 4) copy[i] = 0
        return crc32(copy)
    }

    fun putUInt32(buf: ByteArray, off: Int, value: Long) {
        le(buf).putInt(off, value.toInt())
    }

    /** Used entries only (non-zero type GUID). */
    fun entries(array: ByteArray, count: Int, size: Int): List<GptEntry> = (0 until count).mapNotNull { i ->
        val off = i * size
        if ((0 until 16).all { array[off + it].toInt() == 0 }) return@mapNotNull null
        val b = le(array)
        val nameBytes = array.copyOfRange(off + 56, off + 128)
        GptEntry(
            index = i + 1,
            first = b.getLong(off + ENTRY_OFF_FIRST),
            last = b.getLong(off + ENTRY_OFF_FIRST + 8),
            name = String(nameBytes, Charsets.UTF_16LE).trimEnd('\u0000'),
        )
    }
}
