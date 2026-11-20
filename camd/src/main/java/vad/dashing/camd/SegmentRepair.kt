package vad.dashing.camd

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Pure detection and sample scanning of truncated MP4 segments (0.8.7).
 * MediaMuxer writes ftyp, then mdat with AVCC-framed samples, and moov only on stop() —
 * a power cut leaves moov-less files no player can open. The scanner walks top-level
 * boxes, finds the truncated mdat and lists its access units ([scanSamples]) so the
 * Android-side engine can rebuild a playable file around them. No Android imports so
 * the framing rules are unit-testable on the JVM.
 */
object SegmentRepair {

    /** Do not bother with stubs whose mdat payload is smaller than this. */
    const val MIN_MDAT_BYTES = 64L * 1024

    /** Guard against garbage drift: no single NAL can legitimately exceed this. */
    private const val MAX_NAL_BYTES = 4L * 1024 * 1024

    /** mdat payload region; the box header is not included. */
    data class MdatRegion(val offset: Long, val size: Long)

    /**
     * One recovered access unit: raw [offset]..[offset]+[size) bytes in the file;
     * [keyframe] = the unit contains an IDR slice (NAL type 5).
     */
    data class Sample(val offset: Long, val size: Int, val keyframe: Boolean)

    /** A broken own-named segment worth repairing. */
    data class Candidate(val file: File, val mdat: MdatRegion)

    /**
     * The mdat region of a truncated segment, or null when the file has moov (intact),
     * has no mdat at all, or cannot be read. MediaMuxer writes ftyp + mdat + moov, so the
     * scanner must reach moov or EOF before deciding: an mdat box alone proves nothing —
     * intact files contain one too. A size==0 box extends to EOF (truncated mdat).
     */
    fun findMdat(file: File): MdatRegion? {
        if (!file.isFile) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                val fileLength = file.length()
                var pos = 0L
                var mdat: MdatRegion? = null
                val header = ByteArray(8)
                while (pos + 8 <= fileLength) {
                    channel.position(pos)
                    if (channel.read(ByteBuffer.wrap(header)) != 8) return null
                    val type = String(header, 4, 4, Charsets.US_ASCII)
                    var headerSize = 8L
                    var size = (header[0].toLong() and 0xffL shl 24) or
                        (header[1].toLong() and 0xffL shl 16) or
                        (header[2].toLong() and 0xffL shl 8) or
                        (header[3].toLong() and 0xffL)
                    when (size) {
                        0L -> size = fileLength - pos
                        1L -> {
                            val large = ByteArray(8)
                            if (channel.read(ByteBuffer.wrap(large)) != 8) return null
                            size = ByteBuffer.wrap(large).long
                            headerSize = 16L
                        }
                    }
                    if (type == "moov") return null
                    if (type == "mdat") mdat = MdatRegion(pos + headerSize, size - headerSize)
                    if (size < headerSize) return mdat
                    pos += size
                }
                mdat
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Groups AVCC length-prefixed NAL units inside the mdat payload into access units:
     * parameter NALs (SEI/SPS/PPS/AUD = 6/7/8/9) extend the pending unit, a slice NAL
     * (1..5) finishes it. A truncated tail or a garbage byte stream just ends the list —
     * everything collected before stays usable.
     */
    fun scanSamples(file: File, mdat: MdatRegion): List<Sample> {
        val end = minOf(mdat.offset + mdat.size, file.length())
        val samples = ArrayList<Sample>()
        try {
            RandomAccessFile(file, "r").use { raf ->
                var auStart = -1L
                var auEnd = -1L
                var auKey = false
                var pos = mdat.offset
                val header = ByteArray(4)
                while (pos + 4 <= end) {
                    raf.seek(pos)
                    if (raf.read(header) != 4) break
                    val length = (header[0].toLong() and 0xffL shl 24) or
                        (header[1].toLong() and 0xffL shl 16) or
                        (header[2].toLong() and 0xffL shl 8) or
                        (header[3].toLong() and 0xffL)
                    if (length < 1 || length > MAX_NAL_BYTES || pos + 4 + length > end) break
                    val nalHeader = raf.read()
                    if (nalHeader < 0) break
                    when (val type = nalHeader and 0x1F) {
                        in 1..5 -> {
                            if (auStart < 0) auStart = pos
                            auEnd = pos + 4 + length
                            auKey = auKey || type == 5
                            samples += Sample(auStart, (auEnd - auStart).toInt(), auKey)
                            auStart = -1L
                            auKey = false
                        }
                        6, 7, 8, 9 -> {
                            if (auStart < 0) auStart = pos
                            auEnd = pos + 4 + length
                        }
                        else -> break
                    }
                    pos += 4 + length
                }
            }
        } catch (_: Throwable) {
            // Return what was collected before the failure.
        }
        return samples
    }

    /**
     * Own-named segments without moov and with a big-enough mdat. [skipNames] (in-flight
     * segments) and intact files are excluded.
     */
    fun repairCandidates(dir: File, skipNames: Set<String> = emptySet()): List<Candidate> =
        RecorderFiles.listSegments(dir)
            .filter { it.name !in skipNames }
            .mapNotNull { file ->
                findMdat(file)?.takeIf { it.size >= MIN_MDAT_BYTES }?.let { Candidate(file, it) }
            }
}
