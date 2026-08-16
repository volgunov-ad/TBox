package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GeoDebugLogRotateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun doesNotRotateEmptyFile() {
        assertFalse(
            GeoDebugLogRotate.shouldRotate(
                flushedBytes = 0L,
                pendingUtf8Bytes = 0,
                nextUtf8Bytes = 100,
                maxBytes = 50L,
            ),
        )
    }

    @Test
    fun rotatesWhenNextChunkWouldExceed() {
        assertTrue(
            GeoDebugLogRotate.shouldRotate(
                flushedBytes = 40L,
                pendingUtf8Bytes = 5,
                nextUtf8Bytes = 10,
                maxBytes = 50L,
            ),
        )
        assertFalse(
            GeoDebugLogRotate.shouldRotate(
                flushedBytes = 40L,
                pendingUtf8Bytes = 5,
                nextUtf8Bytes = 5,
                maxBytes = 50L,
            ),
        )
    }

    @Test
    fun uniqueFileAddsSuffixWhenStampExists() {
        val dir = tmp.newFolder("logs")
        val wallMs = 1_776_278_400_000L // 2026-03-11 00:00:00 UTC-ish; stamp is local
        val first = GeoDebugLogRotate.uniqueFile(dir, wallMs)
        first.writeText("a")
        val second = GeoDebugLogRotate.uniqueFile(dir, wallMs)
        assertTrue(second.name.endsWith("_2.txt"))
        assertFalse(first.name == second.name)
        second.writeText("b")
        val third = GeoDebugLogRotate.uniqueFile(dir, wallMs)
        assertTrue(third.name.endsWith("_3.txt"))
    }

    @Test
    fun uniqueFileUsesPrefix() {
        val dir = tmp.newFolder("empty")
        val file = GeoDebugLogRotate.uniqueFile(dir, 1_700_000_000_000L)
        assertTrue(file.name.startsWith(GeoDebugLogRotate.FILE_PREFIX))
        assertTrue(file.name.endsWith(".txt"))
        assertEquals(dir, file.parentFile)
    }

    @Test
    fun utf8CountsMultibyte() {
        assertEquals(1, GeoDebugLogRotate.utf8Bytes("a"))
        assertEquals(2, GeoDebugLogRotate.utf8Bytes("ж"))
    }

    @Test
    fun twentyMegLimitRotatesOnOverflowOnly() {
        val max = GeoDebugLogRecorder.MAX_FILE_BYTES
        val tick = 4_000
        assertFalse(
            GeoDebugLogRotate.shouldRotate(max - tick, 0, tick, max),
        )
        assertTrue(
            GeoDebugLogRotate.shouldRotate(max - tick + 1, 0, tick, max),
        )
    }
}
