package vad.dashing.dataresize.gpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskMapTest {
    private val gib = 2097152L
    private val mib = 2048L
    private val disk = 64 * gib

    /** Shaped like the A10 table: small boot slots, a big system, userdata in the middle, free tail. */
    private val entries = listOf(
        GptEntry(1, 0, 4 * mib - 1, "preloader"),
        GptEntry(2, 4 * mib, 36 * mib - 1, "lk_a"),
        GptEntry(3, 36 * mib, 4 * gib + 36 * mib - 1, "system_a"),
        GptEntry(4, 4 * gib + 36 * mib, 4 * gib + 52 * mib - 1, "misc"),
        GptEntry(5, 4 * gib + 52 * mib, 6 * gib + 52 * mib - 1, "userdata"),
        GptEntry(6, 6 * gib + 52 * mib, 8 * gib + 52 * mib - 1, "media"),
    )

    @Test
    fun smallPartitionsAreGroupedAndTailIsFree() {
        val segs = DiskMap.view("Сейчас", disk, entries).segments
        assertEquals(
            listOf("preloader … lk_a", "system_a", "misc", "userdata", "media", "свободно"),
            segs.map { it.label },
        )
        assertEquals(SegmentKind.Group, segs[0].kind)
        assertEquals(listOf("preloader", "lk_a"), segs[0].names)
        assertEquals(SegmentKind.Partition, segs[2].kind)
        assertEquals(SegmentKind.Userdata, segs[3].kind)
        assertEquals(SegmentKind.Free, segs.last().kind)
        assertEquals(disk - 1, segs.last().last)
        assertContiguous(segs)
    }

    @Test
    fun afterMoveShowsFreedOldPlaceAndUserdataInTail() {
        val newFirst = 8 * gib + 56 * mib
        val segs = DiskMap.view("После переноса", disk, entries, moveUserdataTo = newFirst until disk).segments
        val freed = segs.single { it.kind == SegmentKind.Freed }
        assertEquals(entries[4].first, freed.first)
        assertEquals(entries[4].last, freed.last)
        val userdata = segs.single { it.kind == SegmentKind.Userdata }
        assertEquals(newFirst, userdata.first)
        assertEquals(disk - 1, userdata.last)
        // The 4 MiB alignment slack before the new userdata is folded into «media».
        assertTrue(segs.none { it.kind == SegmentKind.Free })
        assertEquals(newFirst - 1, segs.single { it.label == "media" }.last)
        assertContiguous(segs)
    }

    @Test
    fun afterRollbackFreesTheTail() {
        val moved = entries.map { if (it.name == "userdata") it.copy(first = 9 * gib, last = disk - 1) else it }
        val back = entries[4].first..entries[4].last
        val segs = DiskMap.view("После отката", disk, moved, moveUserdataTo = back).segments
        assertEquals(back.first, segs.single { it.kind == SegmentKind.Userdata }.first)
        val freed = segs.single { it.kind == SegmentKind.Freed }
        assertEquals(9 * gib, freed.first)
        assertEquals(disk - 1, freed.last)
        assertContiguous(segs)
    }

    private fun assertContiguous(segs: List<DiskSegment>) {
        assertEquals(0L, segs.first().first)
        segs.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
    }
}
