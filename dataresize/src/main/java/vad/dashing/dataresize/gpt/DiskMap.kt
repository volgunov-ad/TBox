package vad.dashing.dataresize.gpt

enum class SegmentKind { Userdata, Partition, Group, Free, Freed }

data class DiskSegment(
    val label: String,
    val kind: SegmentKind,
    val first: Long,
    val last: Long,
    val names: List<String> = emptyList(),
) {
    val sectors: Long get() = last - first + 1
}

data class DiskMapView(val title: String, val diskSectors: Long, val segments: List<DiskSegment>)

/**
 * eMMC layout as a bar: big partitions and gaps are shown on their own, runs of small ones (the
 * A/B boot slots, configs) are merged into one group so they stay visible next to 30 GiB of tail.
 */
object DiskMap {
    /** Segments below this share of the disk are merged into groups. */
    const val MIN_SHARE = 0.015

    /**
     * [moveUserdataTo] previews the table with «userdata» at that range; its current place becomes
     * a [SegmentKind.Freed] gap.
     */
    fun view(
        title: String,
        diskSectors: Long,
        entries: List<GptEntry>,
        moveUserdataTo: LongRange? = null,
    ): DiskMapView {
        val userdata = entries.firstOrNull { it.name == GptPlanner.PART_NAME }
        val freed = if (moveUserdataTo != null && userdata != null) userdata.first..userdata.last else null
        val placed = entries.map {
            if (moveUserdataTo != null && it == userdata) it.copy(first = moveUserdataTo.first, last = moveUserdataTo.last) else it
        }
        return DiskMapView(title, diskSectors, merge(raw(diskSectors, placed, freed), diskSectors))
    }

    private fun raw(diskSectors: Long, entries: List<GptEntry>, freed: LongRange?): List<DiskSegment> {
        val out = mutableListOf<DiskSegment>()
        var next = 0L
        fun gap(until: Long) {
            if (until <= next) return
            val last = until - 1
            if (freed == null || freed.last < next || freed.first > last) {
                out += DiskSegment("свободно", SegmentKind.Free, next, last)
                return
            }
            val from = maxOf(next, freed.first)
            val to = minOf(last, freed.last)
            if (from > next) out += DiskSegment("свободно", SegmentKind.Free, next, from - 1)
            out += DiskSegment("освобождается", SegmentKind.Freed, from, to)
            if (to < last) out += DiskSegment("свободно", SegmentKind.Free, to + 1, last)
        }
        for (e in entries.sortedBy { it.first }) {
            gap(e.first)
            val kind = if (e.name == GptPlanner.PART_NAME) SegmentKind.Userdata else SegmentKind.Partition
            out += DiskSegment(e.name, kind, e.first, e.last, listOf(e.name))
            next = maxOf(next, e.last + 1)
        }
        gap(diskSectors)
        return out
    }

    private fun merge(raw: List<DiskSegment>, diskSectors: Long): List<DiskSegment> {
        val min = (diskSectors * MIN_SHARE).toLong()
        fun small(s: DiskSegment) = s.sectors < min && s.kind != SegmentKind.Userdata && s.kind != SegmentKind.Freed
        val out = mutableListOf<DiskSegment>()
        var run = mutableListOf<DiskSegment>()
        fun flush() {
            if (run.isEmpty()) return
            val names = run.flatMap { it.names }
            val prev = out.lastOrNull()
            if (names.isEmpty() && prev != null && run.last().last - run.first().first + 1 < GptPlanner.MIB_SECTORS * 64) {
                // Alignment slack and the secondary GPT: not worth a slice of their own.
                out[out.lastIndex] = prev.copy(last = run.last().last)
                run = mutableListOf()
                return
            }
            out += when {
                names.isEmpty() -> DiskSegment("свободно", SegmentKind.Free, run.first().first, run.last().last)
                run.size == 1 -> run.single()
                names.size == 1 -> DiskSegment(names.single(), SegmentKind.Partition, run.first().first, run.last().last, names)
                else -> DiskSegment(
                    "${names.first()} … ${names.last()}",
                    SegmentKind.Group,
                    run.first().first,
                    run.last().last,
                    names,
                )
            }
            run = mutableListOf()
        }
        for (s in raw) {
            if (small(s)) {
                run += s
            } else {
                flush()
                out += s
            }
        }
        flush()
        return out
    }
}
