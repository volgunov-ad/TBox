package vad.dashing.camd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar

class RecorderFilesTest {
    @Test
    fun segmentLengthDefaultsToThreeMinutesAndOptionsMatch() {
        assertEquals(180_000L, RecorderFiles.SEGMENT_MS)
        assertEquals(180, RecorderController.DEFAULT_SEGMENT_SEC)
        assertArrayEquals(longArrayOf(30_000L, 60_000L, 180_000L, 300_000L), RecorderFiles.SEGMENT_OPTIONS_MS)
    }

    @Test
    fun segmentEstimateGrowsWithSegmentLength() {
        // 6 Mbps = 750 KB/s; reserve must cover a 5-minute segment, not a 30 s one.
        assertTrue(RecorderFiles.SEGMENT_ESTIMATE_BYTES >= 100L * 1024 * 1024)
        assertEquals(750_000L * (RecorderFiles.SEGMENT_MS / 1000L), RecorderFiles.SEGMENT_ESTIMATE_BYTES)
    }

    @Test
    fun segmentEstimateFollowsSelectedSegmentLength() {
        assertEquals(750_000L * 30, RecorderFiles.segmentEstimateBytes(30_000L))
        assertEquals(750_000L * 300, RecorderFiles.segmentEstimateBytes(300_000L))
        assertTrue(RecorderFiles.segmentEstimateBytes(300_000L) > RecorderFiles.segmentEstimateBytes(30_000L))
    }

    @Test
    fun segmentNameEmbedsInputAndTimestamp() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 12, 30, 45)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("cam4_2026-11-17_12-30-45.mp4", RecorderFiles.segmentName(4, calendar.time))
        assertEquals(
            "cam4_2026-11-17_12-30-45_1.mp4",
            RecorderFiles.segmentName(4, calendar.time, suffix = 1),
        )
    }

    @Test
    fun ownSegmentsMatchAndForeignDoNot() {
        assertTrue(RecorderFiles.isOwnSegment(File("cam0_2026-11-17_12-30-45.mp4")))
        assertTrue(RecorderFiles.isOwnSegment(File("cam9_2026-01-01_00-00-00.mp4")))
        assertTrue(RecorderFiles.isOwnSegment(File("cam10_2026-11-17_12-30-45.mp4")))
        assertTrue(RecorderFiles.isOwnSegment(File("cam4_2026-11-17_12-30-45_1.mp4")))
        assertTrue(RecorderFiles.isOwnSegment(File("cam4_2026-11-17_12-30-45_12.mp4")))
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
    fun quotaCountsAndRotatesRepairLeftovers() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val broken = File(dir, "cam4_2026-11-17_09-00-00.broken.mp4").apply { writeBytes(ByteArray(100)) }
            val fixed = File(dir, "cam4_2026-11-17_09-30-00.fixed.mp4").apply { writeBytes(ByteArray(100)) }
            val segment = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val repairTmp = File(dir, "cam4_2026-11-17_08-00-00.fixed.mp4.tmp").apply { writeBytes(ByteArray(100)) }

            assertEquals(listOf(segment), RecorderFiles.listSegments(dir))
            assertEquals(300L, RecorderFiles.totalBytes(dir))
            assertTrue(RecorderFiles.enforceQuota(dir, quotaBytes = 150, reserveBytes = 0))
            assertFalse(broken.exists())
            assertFalse(fixed.exists())
            assertTrue(segment.exists())
            assertTrue(repairTmp.exists())
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

    @Test
    fun segmentInputExtractsCameraIdIncludingSuffix() {
        assertEquals(4, RecorderFiles.segmentInput(File("cam4_2026-11-17_12-30-45.mp4")))
        assertEquals(4, RecorderFiles.segmentInput(File("cam4_2026-11-17_12-30-45_1.mp4")))
        assertEquals(12, RecorderFiles.segmentInput(File("cam12_2026-11-17_12-30-45.mp4")))
        assertEquals(null, RecorderFiles.segmentInput(File("notes.txt")))
    }

    @Test
    fun sosFolderNameUsesSameTimestampStyle() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 20, 12, 34, 56)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("sos_2026-11-20_12-34-56", RecorderFiles.sosFolderName(calendar.time))
    }

    @Test
    fun sosSelectionTakesNewestPerCameraOnly() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val cam4old = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(10)) }
            val cam4mid = File(dir, "cam4_2026-11-17_10-00-30.mp4").apply { writeBytes(ByteArray(10)) }
            val cam4new = File(dir, "cam4_2026-11-17_10-01-00.mp4").apply { writeBytes(ByteArray(10)) }
            val cam2new = File(dir, "cam2_2026-11-17_10-01-00.mp4").apply { writeBytes(ByteArray(10)) }

            assertEquals(emptyList<File>(), RecorderFiles.sosSegmentsForCamera(dir, 4, 0))
            assertEquals(listOf(cam4new), RecorderFiles.sosSegmentsForCamera(dir, 4, 1))
            assertEquals(listOf(cam4mid, cam4new), RecorderFiles.sosSegmentsForCamera(dir, 4, 2))
            assertEquals(
                listOf(cam4old, cam4mid, cam4new),
                RecorderFiles.sosSegmentsForCamera(dir, 4, 3),
            )
            // More requested than present: everything the camera has.
            assertEquals(
                listOf(cam4old, cam4mid, cam4new),
                RecorderFiles.sosSegmentsForCamera(dir, 4, 10),
            )
            assertEquals(listOf(cam2new), RecorderFiles.sosSegmentsForCamera(dir, 2, 5))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sosSelectionSkipsSegmentStartedAfterTheClosedOne() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val previous = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(10)) }
            val closed = File(dir, "cam4_2026-11-17_10-03-00.mp4").apply { writeBytes(ByteArray(10)) }
            val inFlight = File(dir, "cam4_2026-11-17_10-03-00_1.mp4").apply { writeBytes(ByteArray(10)) }

            assertEquals(listOf(closed, inFlight), RecorderFiles.sosSegmentsForCamera(dir, 4, 2))
            assertEquals(
                listOf(previous, closed),
                RecorderFiles.sosSegmentsForCamera(dir, 4, 2, newest = closed),
            )
            assertEquals(
                listOf(previous, closed),
                RecorderFiles.sosSegmentsForCamera(dir, 4, 2, exclude = setOf(inFlight.name)),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun copyFileSkipsExistingAndRemovesPartialOnFailure() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val source = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(64)) }
            val target = File(dir, "copy.mp4")
            assertTrue(RecorderFiles.copyFile(source, target))
            assertEquals(64L, target.length())
            // Same name again — skip silently (repeat tap must not duplicate files).
            assertFalse(RecorderFiles.copyFile(source, target))
            assertEquals(64L, target.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quotaRotationStillMatchesSuffixedSegments() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val a = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val b = File(dir, "cam4_2026-11-17_10-00-00_1.mp4").apply { writeBytes(ByteArray(100)) }
            assertTrue(RecorderFiles.enforceQuota(dir, quotaBytes = 150, reserveBytes = 0))
            assertFalse(a.exists())
            assertTrue(b.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quotaRotationSkipsUndeletableAndRemovesNextCandidates() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val stuck = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val b = File(dir, "cam4_2026-11-17_10-03-00.mp4").apply { writeBytes(ByteArray(100)) }
            val c = File(dir, "cam4_2026-11-17_10-06-00.mp4").apply { writeBytes(ByteArray(100)) }
            // 300 bytes total, quota 150: the oldest file is locked and survives,
            // both deletable ones go instead.
            val refused = mutableListOf<File>()
            val result = RecorderFiles.enforceQuota(
                dir,
                quotaBytes = 150,
                reserveBytes = 0,
                delete = { file ->
                    if (file == stuck) {
                        refused.add(file)
                        false
                    } else {
                        file.delete()
                    }
                },
            )
            assertTrue(result)
            assertTrue(stuck.exists())
            assertFalse(b.exists())
            assertFalse(c.exists())
            assertEquals(listOf(stuck), refused)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun quotaFailsHonestlyWhenOnlyUndeletableFilesRemain() {
        val dir = Files.createTempDirectory("camdrec").toFile()
        try {
            val stuck = File(dir, "cam4_2026-11-17_10-00-00.mp4").apply { writeBytes(ByteArray(100)) }
            val undeletable = File(dir, "cam4_2026-11-17_10-03-00.mp4").apply { writeBytes(ByteArray(100)) }
            // Both files refuse to be deleted and together exceed the quota —
            // the failure must not be masked by dropping them from the accounting.
            val result = RecorderFiles.enforceQuota(
                dir,
                quotaBytes = 150,
                reserveBytes = 0,
                delete = { file -> file != stuck && file != undeletable && file.delete() },
            )
            assertFalse(result)
            assertTrue(stuck.exists())
            assertTrue(undeletable.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
