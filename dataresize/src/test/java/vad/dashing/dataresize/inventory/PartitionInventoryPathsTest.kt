package vad.dashing.dataresize.inventory

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartitionInventoryPathsTest {
    @Test
    fun sanitizeIncrementalKeepsSafeChars() {
        assertEquals("Jenkins03261716", PartitionInventoryPaths.sanitizeIncremental("Jenkins03261716"))
        assertEquals("1.0.5", PartitionInventoryPaths.sanitizeIncremental("1.0.5"))
        assertEquals("bad_name", PartitionInventoryPaths.sanitizeIncremental("bad name"))
        assertEquals("unknown", PartitionInventoryPaths.sanitizeIncremental("   "))
    }

    @Test
    fun reportFileNameUsesStampAndIncremental() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 6, 12, 4, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val name = PartitionInventoryPaths.reportFileName(
            "1.0.5",
            cal.time,
            TimeZone.getTimeZone("UTC"),
        )
        assertEquals("tbox_part_report_1.0.5_20261106-1204.md", name)
    }

    @Test
    fun isUsbMountDetectsRemovableAndSkipsIntsd() {
        assertTrue(PartitionInventoryPaths.isUsbMount("/mnt/media_rw/ABCD-1234"))
        assertTrue(PartitionInventoryPaths.isUsbMount("/udisk0"))
        assertTrue(PartitionInventoryPaths.isUsbMount("/storage/ABCD-1234"))
        assertFalse(PartitionInventoryPaths.isUsbMount("/mnt/media_rw/internal_sdcard"))
        assertFalse(PartitionInventoryPaths.isUsbMount("/storage/emulated/0"))
        assertFalse(PartitionInventoryPaths.isUsbMount("/storage/self"))
    }

    @Test
    fun pickOutputDirPrefersUsbThenSdcardThenIntsd() {
        val usb = PartitionInventoryPaths.pickOutputDir(
            listOf(
                "/mnt/media_rw/internal_sdcard",
                "/sdcard",
                "/mnt/media_rw/ABCD-1234",
            ),
        )
        assertEquals(OutputKind.Usb to "/mnt/media_rw/ABCD-1234", usb)

        val sd = PartitionInventoryPaths.pickOutputDir(listOf("/sdcard", "/mnt/media_rw/internal_sdcard"))
        assertEquals(OutputKind.Sdcard to "/sdcard", sd)

        val intsd = PartitionInventoryPaths.pickOutputDir(listOf("/mnt/media_rw/internal_sdcard"))
        assertEquals(OutputKind.Intsd to "/mnt/media_rw/internal_sdcard", intsd)

        assertNull(PartitionInventoryPaths.pickOutputDir(emptyList()))
    }
}
