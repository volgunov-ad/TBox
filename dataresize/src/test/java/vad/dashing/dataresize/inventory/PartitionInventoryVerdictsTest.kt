package vad.dashing.dataresize.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartitionInventoryVerdictsTest {
    private fun part(
        name: String,
        sectors: Long = 2048L * 4096L,
        usedKiB: Long? = null,
        notes: List<String> = emptyList(),
        topDirs: List<Pair<String, Long>> = emptyList(),
        fotaKiB: Long? = null,
    ) = PartitionInfo(
        name = name,
        firstLba = 0,
        lastLba = sectors - 1,
        sectors = sectors,
        neighborRight = null,
        fstype = "ext4",
        mountPoint = "/$name",
        mountOptions = "rw",
        mounted = usedKiB != null,
        usedKiB = usedKiB,
        freeKiB = usedKiB?.let { 1000L },
        sizeKiB = usedKiB?.let { it + 1000L },
        topDirs = topDirs,
        notes = notes,
        sampleFiles = emptyList(),
        fotaKiB = fotaKiB,
    )

    @Test
    fun mediaShrinkOkWhenAlmostEmptyAndAdjacent() {
        val v = PartitionInventoryVerdicts.mediaShrink(
            part("media", usedKiB = 48L * 1024L),
            adjacent = true,
        )
        assertTrue(v.ok == true)
        assertTrue(v.text.contains("ДА", ignoreCase = false))
    }

    @Test
    fun mediaShrinkBlockedWhenFull() {
        val v = PartitionInventoryVerdicts.mediaShrink(
            part("media", usedKiB = 2200L * 1024L),
            adjacent = true,
        )
        assertFalse(v.ok == true)
        assertTrue(v.text.contains("НЕТ"))
    }

    @Test
    fun mediaShrinkBlockedWhenNotAdjacent() {
        val v = PartitionInventoryVerdicts.mediaShrink(
            part("media", usedKiB = 10L * 1024L),
            adjacent = false,
        )
        assertEquals(false, v.ok)
        assertTrue(v.text.contains("не смежны"))
    }

    @Test
    fun intsdDoNotTouchWhenMapsPresent() {
        val v = PartitionInventoryVerdicts.intsdTouch(
            part(
                "intsd",
                usedKiB = 3100L * 1024L,
                topDirs = listOf("Maps" to 1800L * 1024L, "Music" to 700L * 1024L),
                notes = listOf("есть Maps (навигация/карты)"),
            ),
        )
        assertEquals(false, v.ok)
        assertTrue(v.text.contains("НЕТ"))
    }

    @Test
    fun cacheFotaSuggestsCopyWhenLarge() {
        val v = PartitionInventoryVerdicts.cacheFota(part("cache", fotaKiB = 1500L * 1024L))
        assertEquals(true, v.ok)
        assertTrue(v.text.contains("ПК"))
    }

    @Test
    fun dataresizeTailUnavailableWhenSmall() {
        val userdataSectors = 2048L * 2048L // 2 GiB
        val tailSectors = 2048L * 328L // 328 MiB
        val v = PartitionInventoryVerdicts.dataresizeTail(tailSectors, userdataSectors)
        assertEquals(false, v.ok)
        assertTrue(v.text.contains("недоступен"))
    }

    @Test
    fun dataresizeTailOkWhenLarge() {
        val userdataSectors = 2048L * 2048L
        val tailSectors = 2048L * 30_000L
        val v = PartitionInventoryVerdicts.dataresizeTail(tailSectors, userdataSectors)
        assertEquals(true, v.ok)
    }

    @Test
    fun fromBuildsFourVerdicts() {
        val inv = PartitionInventory(
            takenAt = "2026-11-06 12:04",
            model = "test",
            displayId = "id",
            incremental = "1.0.5",
            hardware = "ac8x",
            diskSectors = 61_079_552L,
            lastUsable = 61_079_500L,
            freeTailSectors = 2048L * 328L,
            partitions = listOf(
                part("userdata", sectors = 2048L * 2048L, usedKiB = 900L * 1024L),
                part("media", sectors = 2048L * 4096L, usedKiB = 48L * 1024L),
                part(
                    "intsd",
                    sectors = 2048L * 7678L,
                    usedKiB = 3100L * 1024L,
                    topDirs = listOf("Maps" to 1800L * 1024L),
                    notes = listOf("есть Maps"),
                ),
                part("cache", sectors = 2048L * 4608L, usedKiB = 1700L * 1024L, fotaKiB = 1460L * 1024L),
            ),
            userdataMediaAdjacent = true,
        )
        val verdicts = PartitionInventoryVerdicts.from(inv)
        assertEquals(4, verdicts.size)
        assertTrue(verdicts[0].ok == true) // media
        assertTrue(verdicts[1].ok == false) // intsd
        assertTrue(verdicts[2].ok == true) // fota
        assertTrue(verdicts[3].ok == false) // dataresize tail
    }
}
