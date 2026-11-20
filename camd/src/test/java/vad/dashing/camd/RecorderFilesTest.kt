package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar

class RecorderFilesTest {
    @Test
    fun crashSafeSegmentIsShortForFrequentMoov() {
        // MediaMuxer writes moov only on stop — keep segments ≤ 30 s (P1-3).
        assertEquals(30_000L, RecorderFiles.SEGMENT_MS)
        assertTrue(RecorderFiles.SEGMENT_ESTIMATE_BYTES < 50L * 1024 * 1024)
    }

    @Test
    fun segmentNameEmbedsInputAndTimestamp() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 12, 30, 45)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("cam4_2026-11-17_12-30-45.mp4", RecorderFiles.segmentName(4, calendar.time))
    }

    @Test
    fun ownSegmentsMatchAndForeignDoNot() {
        assertTrue(RecorderFiles.isOwnSegment(File("cam0_2026-11-17_12-30-45.mp4")))
        assertTrue(RecorderFiles.isOwnSegment(File("cam9_2026-01-01_00-00-00.mp4")))
        assertTrue(RecorderFiles.isOwnSegment(File("cam10_2026-11-17_12-30-45.mp4")))
        assertFalse(RecorderFiles.isOwnSegment(File("cam0_2026-11-17_12-30-45.mp4.bak")))
        assertFalse(RecorderFiles.isOwnSegment(File("cam_2026-11-17_12-30-45.mp4")))
        assertFalse(RecorderFiles.isOwnSegment(File("notes.txt")))
    }

    @Test
    fun listsOnlyOwnSegmentsOldestFirst() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val first = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(10)) }
            val second = File(dir, "cam4_2026-11-17_10-03-00.mp4").apply { writeBytes(ByteArray(20)) }
            File(dir, "cam4_2026-11-17_10-06-00.mp4.bak").writeBytes(ByteArray(40))
            File(dir, "foreign.mp4").writeBytes(ByteArray(80))
            assertEquals(listOf(first, second), RecorderFiles.listSegments(dir))
            assertEquals(30L, RecorderFiles.totalBytes(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quotaRotationDeletesOldestOwnSegmentsOnly() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val a = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val b = File(dir, "cam4_2026-11-17_10-03-00.mp4").apply { writeBytes(ByteArray(100)) }
            val c = File(dir, "cam4_2026-11-17_10-06-00.mp4").apply { writeBytes(ByteArray(100)) }
            val foreign = File(dir, "notes.txt").apply { writeBytes(ByteArray(1000)) }

            assertTrue(RecorderFiles.enforceQuota(dir, quotaBytes = 250, reserveBytes = 0))
            assertFalse(a.exists())
            assertTrue(b.exists())
            assertTrue(c.exists())
            assertEquals(200L, RecorderFiles.totalBytes(dir))
            assertTrue(foreign.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun segmentsAcrossCamerasSortedByTimestampNotByCamera() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val cam5Newest = File(dir, "cam5_2026-11-17_10-09-00.mp4")
            val cam4Mid = File(dir, "cam4_2026-11-17_10-06-00.mp4")
            val cam2Mid = File(dir, "cam2_2026-11-17_10-03-00.mp4")
            val cam1Oldest = File(dir, "cam1_2026-11-17_10-00-00.mp4")
            listOf(cam5Newest, cam4Mid, cam2Mid, cam1Oldest).forEach { it.writeBytes(ByteArray(10)) }

            assertEquals(
                listOf(cam1Oldest, cam2Mid, cam4Mid, cam5Newest),
                RecorderFiles.listSegments(dir),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quotaRotationSpreadsDeletesAcrossCamerasByTime() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val cam1_0800 = File(dir, "cam1_2026-11-17_08-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val cam2_0800 = File(dir, "cam2_2026-11-17_08-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val cam4_0800 = File(dir, "cam4_2026-11-17_08-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val cam5_0800 = File(dir, "cam5_2026-11-17_08-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val cam1_0900 = File(dir, "cam1_2026-11-17_09-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val cam2_0900 = File(dir, "cam2_2026-11-17_09-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val cam4_0900 = File(dir, "cam4_2026-11-17_09-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val cam5_0900 = File(dir, "cam5_2026-11-17_09-00-00.mp4").apply { writeBytes(ByteArray(100)) }

            assertTrue(RecorderFiles.enforceQuota(dir, quotaBytes = 550, reserveBytes = 0))
            assertFalse(cam1_0800.exists())
            assertFalse(cam2_0800.exists())
            assertFalse(cam4_0800.exists())
            assertTrue(cam5_0800.exists())
            assertTrue(cam1_0900.exists())
            assertTrue(cam2_0900.exists())
            assertTrue(cam4_0900.exists())
            assertTrue(cam5_0900.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quotaFailsWithoutQuotaOrFreeSpace() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            File(dir, "cam4_2026-11-17_10-00-00.mp4").writeBytes(ByteArray(100))
            assertFalse(RecorderFiles.enforceQuota(dir, quotaBytes = 0, reserveBytes = 0))
            assertFalse(RecorderFiles.enforceQuota(dir, quotaBytes = 1000, reserveBytes = Long.MAX_VALUE))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun folderProbeChecksExistenceAndWrite() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            assertTrue(RecorderFiles.isWritable(dir))
            assertFalse(RecorderFiles.isWritable(null))
            assertFalse(RecorderFiles.isWritable(File(dir, "missing")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
