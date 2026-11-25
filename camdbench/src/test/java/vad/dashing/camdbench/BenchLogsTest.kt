package vad.dashing.camdbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchLogsTest {

    @Test
    fun grabCopyLinesAreParsedAndWeighted() {
        val log = """
            camd_grab start
            copy 4: frames=750 fps=25.0 avg=10.00 ms max=14.00 ms bytes=2048000
            open 5 -> ok
            copy 4: frames=250 fps=8.3 avg=2.00 ms max=30.00 ms bytes=2048000
            copy 5: frames=0 fps=0.0 avg=0.00 ms max=0.00 ms bytes=2048000
        """.trimIndent()
        val rows = BenchLogs.parseGrabCopy(log)
        assertEquals(3, rows.size)
        val summary = BenchLogs.summarizeCopy(rows)
        assertEquals(1, summary.size)
        assertEquals(4, summary[0].inputId)
        assertEquals(8.0, summary[0].avgMs, 1e-9)
        assertEquals(30.0, summary[0].maxMs, 1e-9)
        assertEquals(2, summary[0].periods)
    }

    @Test
    fun recorderSegmentLinesFromLogcat() {
        val logcat = "11-25 10:03:00.123 I/CamdRecorder( 1234): cam4 cam4_2026-11-25_10-00-00.mp4: кадров 4498, пропущено 2, 180.0 с\n" +
            "11-25 10:03:00.200 I/CamdRecorder( 1234): запись стартовала: cam4"
        val rows = BenchLogs.parseRecorderSegments(logcat)
        assertEquals(1, rows.size)
        assertEquals(4, rows[0].inputId)
        assertEquals("cam4_2026-11-25_10-00-00.mp4", rows[0].file)
        assertEquals(4498, rows[0].frames)
        assertEquals(2, rows[0].skipped)
        assertEquals(180.0, rows[0].seconds, 1e-9)
    }

    @Test
    fun finalLogcatDumpDoesNotDoubleSegments() {
        val line = "11-25 10:03:00.123 I/CamdRecorder( 1234): cam4 cam4_2026-11-25_10-00-00.mp4: кадров 4498, пропущено 2, 180.0 с\n"
        assertEquals(1, BenchLogs.parseRecorderSegments(line + "read: unexpected EOF!\n" + line).size)
    }

    @Test
    fun segmentNames() {
        assertEquals(4, BenchLogs.segmentInput("cam4_2026-11-25_10-00-00.mp4"))
        assertEquals(5, BenchLogs.segmentInput("cam5_2026-11-25_10-00-00_2.mp4"))
        assertNull(BenchLogs.segmentInput("cam4_2026-11-25_10-00-00.broken.mp4"))
        assertNull(BenchLogs.segmentInput("video.mp4"))
    }

    @Test
    fun fpsPerCameraUsesFrameIntervals() {
        val fps = BenchLogs.summarizeFps(
            listOf(
                BenchLogs.SegmentFps(4, "a", 1501, 60.0),
                BenchLogs.SegmentFps(4, "b", 751, 60.0),
                BenchLogs.SegmentFps(5, "c", 10, 0.0),
            ),
        )
        assertEquals(1, fps.size)
        assertEquals(18.75, fps[0].fps, 1e-9)
        assertEquals(2, fps[0].segments)
        assertEquals(25.0, BenchLogs.SegmentFps(4, "a", 1501, 60.0).fps, 1e-9)
    }

    @Test
    fun recorderFolderFromPrefs() {
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <boolean name="recorder_enabled" value="true" />
                <string name="recorder_folder">/storage/1234-ABCD/DVR &amp; cams</string>
            </map>
        """.trimIndent()
        assertEquals("/storage/1234-ABCD/DVR & cams", BenchLogs.recorderFolder(xml))
        assertNull(BenchLogs.recorderFolder("<map></map>"))
        assertNull(BenchLogs.recorderFolder("<string name=\"recorder_folder\"></string>"))
    }

    @Test
    fun statusFields() {
        val fields = BenchLogs.statusFields("OK windows=none avm=stopped keep_avm=0 rec_enabled=1 rec_active=1 rec_sos=0 rec_seg=180")
        assertEquals("none", fields["windows"])
        assertEquals("1", fields["rec_active"])
        assertEquals("180", fields["rec_seg"])
        assertTrue(BenchLogs.statusFields("camd не отвечает").isEmpty())
    }
}
