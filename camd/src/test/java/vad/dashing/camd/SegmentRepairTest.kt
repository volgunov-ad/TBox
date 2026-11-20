package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files

class SegmentRepairTest {
    private fun box(type: String, payload: ByteArray): ByteArray {
        val bytes = ByteArray(8 + payload.size)
        ByteBuffer.wrap(bytes).putInt(8 + payload.size)
        bytes[4] = type[0].code.toByte(); bytes[5] = type[1].code.toByte()
        bytes[6] = type[2].code.toByte(); bytes[7] = type[3].code.toByte()
        payload.copyInto(bytes, 8)
        return bytes
    }

    private fun nal(type: Int, filler: Int = 0xAB): ByteArray {
        val body = ByteArray(12)
        body[0] = type.toByte()
        for (i in 1 until body.size) body[i] = filler.toByte()
        return avcc(body)
    }

    private fun avcc(body: ByteArray): ByteArray {
        val bytes = ByteArray(4 + body.size)
        ByteBuffer.wrap(bytes).putInt(body.size)
        body.copyInto(bytes, 4)
        return bytes
    }

    /** ftyp + mdat without moov, mimicking a power-cut segment. */
    private fun writeTruncated(dir: File, name: String, mdatPayload: ByteArray): File {
        val file = File(dir, name)
        file.writeBytes(box("ftyp", ByteArray(16)) + box("mdat", mdatPayload))
        return file
    }

    @Test
    fun findMdatReturnsTruncatedPayloadRegion() {
        val dir = Files.createTempDirectory("camdrep").toFile()
        try {
            val payload = ByteArray(1000)
            val file = writeTruncated(dir, "cam4_2026-11-20_10-00-00.mp4", payload)
            val mdat = SegmentRepair.findMdat(file)
            assertNotNull(mdat)
            assertEquals(8L + 24L, mdat!!.offset) // ftyp box + mdat header
            assertEquals(1000L, mdat.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun findMdatRejectsIntactAndForeignFiles() {
        val dir = Files.createTempDirectory("camdrep").toFile()
        try {
            val intact = File(dir, "cam4_2026-11-20_10-00-00.mp4")
            intact.writeBytes(box("ftyp", ByteArray(16)) + box("mdat", ByteArray(32)) + box("moov", ByteArray(64)))
            assertNull(SegmentRepair.findMdat(intact))
            assertNull(SegmentRepair.findMdat(File(dir, "notes.txt")))
            assertNull(SegmentRepair.findMdat(File(dir, "missing.mp4")))
            val foreign = File(dir, "foreign.mp4")
            foreign.writeBytes(ByteArray(300))
            assertNull(SegmentRepair.findMdat(foreign))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanSamplesGroupsNalsIntoFramesAndDetectsIdr() {
        val dir = Files.createTempDirectory("camdrep").toFile()
        try {
            val idr = nal(7) + nal(8) + nal(5) // SPS/PPS before the IDR slice
            val inter = nal(1)
            val sei = nal(6)
            val file = writeTruncated(dir, "cam4_2026-11-20_10-00-00.mp4", idr + inter + sei + nal(1))
            val samples = SegmentRepair.scanSamples(file, SegmentRepair.findMdat(file)!!)
            assertEquals(3, samples.size)
            // First AU: SPS+PPS+IDR as one keyframe unit.
            assertEquals(true, samples[0].keyframe)
            assertEquals((idr.size).toLong(), samples[0].size.toLong())
            assertEquals(false, samples[1].keyframe)
            // Trailing AU is SEI + the following slice — a valid inter frame, hence 3 total.
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanSamplesStopsAtTruncatedTail() {
        val dir = Files.createTempDirectory("camdrep").toFile()
        try {
            val complete = nal(5)
            val file = writeTruncated(dir, "cam4_2026-11-20_10-00-00.mp4", complete + byteArrayOf(0, 0, 0))
            val samples = SegmentRepair.scanSamples(file, SegmentRepair.findMdat(file)!!)
            assertEquals(1, samples.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun repairCandidatesListsOnlyBigMoovlessSegments() {
        val dir = Files.createTempDirectory("camdrep").toFile()
        try {
            val big = writeTruncated(dir, "cam4_2026-11-20_10-00-00.mp4", ByteArray(200 * 1024))
            val tiny = writeTruncated(dir, "cam2_2026-11-20_10-00-00.mp4", ByteArray(100))
            val intact = File(dir, "cam1_2026-11-20_10-00-00.mp4")
            intact.writeBytes(box("ftyp", ByteArray(16)) + box("mdat", ByteArray(200 * 1024)) + box("moov", ByteArray(64)))
            val foreign = File(dir, "foreign.mp4")
            foreign.writeBytes(box("ftyp", ByteArray(16)) + box("mdat", ByteArray(200 * 1024)))

            val candidates = SegmentRepair.repairCandidates(dir, skipNames = setOf(big.name))
            assertEquals(0, candidates.size) // big is skipped by name; tiny below threshold, intact has moov, foreign not own
            val candidates2 = SegmentRepair.repairCandidates(dir)
            assertEquals(1, candidates2.size)
            assertEquals(big.name, candidates2[0].file.name)
        } finally {
            dir.deleteRecursively()
        }
    }
}
