package vad.dashing.dataresize.gpt

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Both GPT copies as raw sectors: [primary] is LBA 0 until the first usable LBA,
 * [secondary] is from the last usable LBA + 1 to the end of the disk.
 */
class GptImages(val primary: ByteArray, val secondary: ByteArray, val diskSectors: Long)

class GptLayout(
    val header: GptHeader,
    val entries: List<GptEntry>,
    val primaryArrayOffset: Int,
    val secondaryArrayOffset: Int,
    val secondaryHeaderOffset: Int,
    val secondaryLba: Long,
    val arrayLength: Int,
)

class MovePlan(
    val entry: GptEntry,
    val newFirst: Long,
    val newLast: Long,
    val diskSectors: Long,
    val secondaryLba: Long,
    val oldPrimary: ByteArray,
    val newPrimary: ByteArray,
    val oldSecondary: ByteArray,
    val newSecondary: ByteArray,
) {
    val newSectors: Long get() = newLast - newFirst + 1
    val primarySectors: Long get() = oldPrimary.size / SECTOR.toLong()
    val secondarySectors: Long get() = oldSecondary.size / SECTOR.toLong()
    val copySeekMiB: Long get() = newFirst / GptPlanner.MIB_SECTORS
    val copyMiB: Long get() = entry.sectors / GptPlanner.MIB_SECTORS
}

sealed class PartitionState {
    data class Movable(val plan: MovePlan) : PartitionState()
    data class AlreadyMoved(val entry: GptEntry) : PartitionState()
    data class NotPossible(val reason: String) : PartitionState()
}

object GptPlanner {
    const val PART_NAME = "userdata"
    /** eMMC write-protect group (8 MiB); also keeps the copy offset whole in MiB. */
    const val ALIGN_SECTORS = 16384L
    const val MIB_SECTORS = 2048L

    fun parse(images: GptImages): GptLayout {
        val disk = images.diskSectors
        val ph = Gpt.parseHeader(images.primary, SECTOR)
        if (ph.currentLba != 1L || ph.backupLba != disk - 1) throw GptException("primary GPT does not match disk size")
        if (ph.firstUsable * SECTOR != images.primary.size.toLong()) throw GptException("primary GPT image size mismatch")
        val secondaryLba = ph.lastUsable + 1
        if ((disk - secondaryLba) * SECTOR != images.secondary.size.toLong()) {
            throw GptException("secondary GPT image size mismatch")
        }
        val shOff = ((disk - 1 - secondaryLba) * SECTOR).toInt()
        val sh = Gpt.parseHeader(images.secondary, shOff)
        if (sh.currentLba != disk - 1 || sh.backupLba != 1L) throw GptException("secondary GPT header position")
        if (sh.firstUsable != ph.firstUsable || sh.lastUsable != ph.lastUsable ||
            sh.entryCount != ph.entryCount || sh.entrySize != ph.entrySize
        ) {
            throw GptException("primary and secondary GPT headers differ")
        }
        val len = ph.entryCount * ph.entrySize
        val pOff = (ph.entriesLba * SECTOR).toInt()
        if (ph.entriesLba < 2 || pOff + len > images.primary.size) throw GptException("primary entry array out of range")
        if (sh.entriesLba < secondaryLba) throw GptException("secondary entry array out of range")
        val sOff = ((sh.entriesLba - secondaryLba) * SECTOR).toInt()
        if (sOff + len > shOff) throw GptException("secondary entry array out of range")
        val pArr = images.primary.copyOfRange(pOff, pOff + len)
        val sArr = images.secondary.copyOfRange(sOff, sOff + len)
        if (!pArr.contentEquals(sArr)) throw GptException("primary and secondary entry arrays differ")
        val crc = Gpt.crc32(pArr)
        if (crc != ph.entriesCrc || crc != sh.entriesCrc) throw GptException("bad entry array CRC")
        return GptLayout(
            header = ph,
            entries = Gpt.entries(pArr, ph.entryCount, ph.entrySize),
            primaryArrayOffset = pOff,
            secondaryArrayOffset = sOff,
            secondaryHeaderOffset = shOff,
            secondaryLba = secondaryLba,
            arrayLength = len,
        )
    }

    fun evaluate(images: GptImages): PartitionState {
        val layout = parse(images)
        val matches = layout.entries.filter { it.name == PART_NAME }
        if (matches.size != 1) return PartitionState.NotPossible("в GPT ${matches.size} записей «$PART_NAME»")
        val part = matches.single()
        val others = layout.entries - part
        val othersEnd = others.maxOf { it.last }
        if (part.first > othersEnd && part.last == layout.header.lastUsable) {
            return PartitionState.AlreadyMoved(part)
        }
        val newFirst = (othersEnd + 1 + ALIGN_SECTORS - 1) / ALIGN_SECTORS * ALIGN_SECTORS
        val newLast = layout.header.lastUsable
        if (part.first >= newFirst) return PartitionState.NotPossible("«$PART_NAME» уже не на штатном месте")
        if (newLast - newFirst + 1 <= part.sectors) {
            return PartitionState.NotPossible("за последним разделом нет места больше текущего «$PART_NAME»")
        }
        if (part.sectors % MIB_SECTORS != 0L) return PartitionState.NotPossible("размер «$PART_NAME» не кратен 1 МиБ")
        return PartitionState.Movable(build(images, layout, part, newFirst, newLast))
    }

    private fun build(images: GptImages, layout: GptLayout, part: GptEntry, newFirst: Long, newLast: Long): MovePlan {
        val h = layout.header
        val entryOff = (part.index - 1) * h.entrySize + Gpt.ENTRY_OFF_FIRST
        val newArray = images.primary.copyOfRange(layout.primaryArrayOffset, layout.primaryArrayOffset + layout.arrayLength)
        ByteBuffer.wrap(newArray).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(entryOff, newFirst)
            .putLong(entryOff + 8, newLast)
        val arrayCrc = Gpt.crc32(newArray)

        val newPrimary = images.primary.copyOf()
        newArray.copyInto(newPrimary, layout.primaryArrayOffset)
        sealHeader(newPrimary, SECTOR, h.headerSize, arrayCrc)

        val newSecondary = images.secondary.copyOf()
        newArray.copyInto(newSecondary, layout.secondaryArrayOffset)
        sealHeader(newSecondary, layout.secondaryHeaderOffset, h.headerSize, arrayCrc)

        val plan = MovePlan(
            entry = part,
            newFirst = newFirst,
            newLast = newLast,
            diskSectors = images.diskSectors,
            secondaryLba = layout.secondaryLba,
            oldPrimary = images.primary,
            newPrimary = newPrimary,
            oldSecondary = images.secondary,
            newSecondary = newSecondary,
        )
        verify(layout, plan)
        return plan
    }

    private fun sealHeader(buf: ByteArray, off: Int, size: Int, arrayCrc: Long) {
        Gpt.putUInt32(buf, off + Gpt.OFF_ENTRIES_CRC, arrayCrc)
        Gpt.putUInt32(buf, off + Gpt.OFF_HEADER_CRC, Gpt.headerCrc(buf, off, size))
    }

    /** Only the moved entry's first/last LBA and both CRC fields may differ. */
    private fun verify(layout: GptLayout, plan: MovePlan) {
        val entryOff = (plan.entry.index - 1) * layout.header.entrySize + Gpt.ENTRY_OFF_FIRST
        fun allowed(arrayOff: Int, headerOff: Int): Set<Int> =
            (arrayOff + entryOff until arrayOff + entryOff + 16).toSet() +
                (headerOff + Gpt.OFF_HEADER_CRC until headerOff + Gpt.OFF_HEADER_CRC + 4) +
                (headerOff + Gpt.OFF_ENTRIES_CRC until headerOff + Gpt.OFF_ENTRIES_CRC + 4)
        check(diff(plan.oldPrimary, plan.newPrimary).all { it in allowed(layout.primaryArrayOffset, SECTOR) }) {
            "unexpected primary GPT change"
        }
        check(
            diff(plan.oldSecondary, plan.newSecondary)
                .all { it in allowed(layout.secondaryArrayOffset, layout.secondaryHeaderOffset) },
        ) { "unexpected secondary GPT change" }
        val reparsed = parse(GptImages(plan.newPrimary, plan.newSecondary, plan.diskSectors))
        val moved = reparsed.entries.single { it.index == plan.entry.index }
        check(moved.first == plan.newFirst && moved.last == plan.newLast) { "moved entry mismatch" }
        val sorted = reparsed.entries.sortedBy { it.first }
        // The vendor preloader entry starts at LBA 0 and covers the GPT itself.
        sorted.zipWithNext().forEach { (a, b) ->
            check(a.last < b.first) { "overlap ${a.name} / ${b.name}" }
        }
        reparsed.entries.filter { it.first != 0L }.forEach {
            check(it.first >= reparsed.header.firstUsable && it.last <= reparsed.header.lastUsable) {
                "${it.name} out of usable range"
            }
        }
    }

    private fun diff(a: ByteArray, b: ByteArray): List<Int> = a.indices.filter { a[it] != b[it] }

    fun md5(data: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
}
