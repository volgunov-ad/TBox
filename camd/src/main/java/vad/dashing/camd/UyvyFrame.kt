package vad.dashing.camd

import java.nio.ByteBuffer

internal const val UYVY_MAGIC = 0x31765955
internal const val UYVY_HEADER = 64
/** Per-slot sensor frame numbers in the header: slot 0 at 32, slot 1 at 36. */
internal const val UYVY_FRAME_NO = 32

internal class UyvySlot(
    val width: Int,
    val height: Int,
    val slot: Int,
    val slotBytes: Int,
    val seq: Int,
    /** Sensor frame number of this slot (grabber 7+), 0 from an older grabber. */
    val frameNo: Int = 0,
)

/**
 * Reads one published UYVY slot. Returns null when the header is incomplete or torn:
 * the grabber writes `slot` first and `seq` only after the bytes are in place.
 */
internal fun readUyvySlot(buffer: ByteBuffer): UyvySlot? {
    if (buffer.capacity() < UYVY_HEADER) return null
    if (buffer.getInt(0) != UYVY_MAGIC) return null
    val width = buffer.getInt(4)
    val height = buffer.getInt(8)
    val stride = buffer.getInt(12)
    val seq1 = buffer.getInt(16)
    val slot = buffer.getInt(20)
    val slotBytes = buffer.getInt(24)
    val seq2 = buffer.getInt(16)
    if (seq1 == 0 || seq1 != seq2) return null
    if (slot != 0 && slot != 1) return null
    if (width < 2 || height < 2 || width > 1920 || height > 1200 || width % 2 != 0) return null
    if (stride != width * 2 || slotBytes != stride * height) return null
    val end = UYVY_HEADER.toLong() + (slot + 1).toLong() * slotBytes.toLong()
    if (end > buffer.capacity()) return null
    return UyvySlot(width, height, slot, slotBytes, seq1, buffer.getInt(UYVY_FRAME_NO + slot * 4))
}
