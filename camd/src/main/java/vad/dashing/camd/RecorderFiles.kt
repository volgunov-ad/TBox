package vad.dashing.camd

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure file-level logic of the dashcam recorder: segment naming, listing, quota
 * rotation, emergency-save selection and copying. No Android dependencies so the
 * rules are unit-testable.
 *
 * Platform [android.media.MediaMuxer] MPEG-4 writes the moov atom only on [MediaMuxer.stop].
 * Segments act as crash-safe "frequent moov": a power cut loses at most one in-flight
 * segment (~[SEGMENT_MS], user-selectable, 0.8.6), and every finished file remains playable.
 */
object RecorderFiles {
    /**
     * Wall-clock length of one MP4 segment (0.8.6: user-selectable 30 s / 1 / 3 / 5 min,
     * default 180 s — see [SEGMENT_OPTIONS_MS]). Longer segments hold more history per
     * file but lose more on a power cut: moov is written only on MediaMuxer.stop().
     */
    const val SEGMENT_MS = 180_000L

    /** Selectable segment lengths (0.8.6); UI shows the same order. */
    val SEGMENT_OPTIONS_MS = longArrayOf(30_000L, 60_000L, 180_000L, 300_000L)

    /** Rough bytes of one segment at the fixed 6 Mbps — used as free-space reserve. */
    val SEGMENT_ESTIMATE_BYTES: Long
        get() = segmentEstimateBytes(SEGMENT_MS)

    /** Rough bytes of one [segmentMs] segment at the fixed 6 Mbps; the reserve follows the selected segment length. */
    fun segmentEstimateBytes(segmentMs: Long): Long = BIT_RATE_BYTES_PER_SEC * (segmentMs / 1000L)

    private const val BIT_RATE_BYTES_PER_SEC = 750_000L

    /** Optional `_N` suffix disambiguates same-second segments (emergency-save close). */
    val SEGMENT_REGEX = Regex("cam(\\d+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?:_(\\d+))?\\.mp4")

    fun segmentName(inputId: Int, at: Date = Date(), suffix: Int = 0): String =
        "cam${inputId}_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(at) +
            (if (suffix > 0) "_$suffix" else "") + ".mp4"

    fun isOwnSegment(file: File): Boolean = SEGMENT_REGEX.matches(file.name)

    /** Repair leftovers: `.broken.mp4` (unrepairable evidence) and `.fixed.mp4` (rename failed). */
    val REPAIR_LEFTOVER_REGEX =
        Regex("cam(\\d+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?:_(\\d+))?\\.(?:broken|fixed)\\.mp4")

    private fun isQuotaFile(file: File): Boolean =
        isOwnSegment(file) || REPAIR_LEFTOVER_REGEX.matches(file.name)

    /** Everything the quota owns (segments and repair leftovers), oldest first. */
    fun listQuotaFiles(dir: File): List<File> =
        sortOldestFirst(dir.listFiles()?.filter { isQuotaFile(it) && it.isFile })

    /** Own segments sorted oldest first (the name embeds the start timestamp). */
    fun listSegments(dir: File): List<File> =
        sortOldestFirst(dir.listFiles()?.filter { isOwnSegment(it) && it.isFile })

    /** The regex runs once per file, not once per comparison: folders hold hundreds of segments. */
    private fun sortOldestFirst(files: List<File>?): List<File> =
        files.orEmpty()
            .map { it to segmentOrderKey(it) }
            .sortedWith { a, b -> compareKeys(a.second, b.second) }
            .map { it.first }

    private fun matchTimestamp(file: File): String {
        val match = SEGMENT_REGEX.matchEntire(file.name)
            ?: REPAIR_LEFTOVER_REGEX.matchEntire(file.name)
            ?: return ""
        return match.groupValues[2]
    }

    fun totalBytes(dir: File): Long =
        dir.listFiles()?.sumOf { if (isQuotaFile(it)) it.length() else 0L } ?: 0L

    /**
     * Deletes own oldest segments until the folder fits [quotaBytes] and the volume
     * keeps at least [reserveBytes] free. Foreign files are never touched. A file that
     * refuses to be deleted (locked by another process, bad clusters) is skipped — its
     * bytes keep counting against the quota, and the loop moves on to the next-oldest
     * candidate. Returns false when nothing deletable can satisfy the limits.
     */
    fun enforceQuota(
        dir: File,
        quotaBytes: Long,
        reserveBytes: Long,
        delete: (File) -> Boolean = { it.delete() },
    ): Boolean {
        if (quotaBytes <= 0) return false
        val segments = ArrayDeque(listQuotaFiles(dir).map { it to it.length() })
        // Undeletable files stay in the total: their bytes keep counting against the quota.
        var totalBytes = segments.sumOf { it.second }
        fun fits(): Boolean = totalBytes <= quotaBytes && dir.usableSpace >= reserveBytes
        while (!fits() && segments.isNotEmpty()) {
            val (candidate, length) = segments.removeFirst()
            if (!candidate.exists() || delete(candidate)) totalBytes -= length
        }
        return fits()
    }

    /** The folder exists, is a directory and accepts a probe write. */
    fun isWritable(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val probe = File(dir, ".rec_probe")
        return try {
            if (!probe.createNewFile() && !probe.isFile) return false
            probe.writeText("ok")
            probe.delete()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** `sos_yyyy-MM-dd_HH-mm-ss` — one subfolder per emergency-save tap. */
    fun sosFolderName(at: Date = Date()): String =
        "sos_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(at)

    /** Camera id from a segment file name, or null for foreign files. */
    fun segmentInput(file: File): Int? =
        SEGMENT_REGEX.find(file.name)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Latest [count] own segments of one camera ([inputId]), oldest first.
     * By the time an emergency save copies files the engine has already opened the next
     * segment (no moov yet), so [newest] bounds the selection to the just-closed file
     * and [exclude] drops any in-flight names.
     */
    fun sosSegmentsForCamera(
        dir: File,
        inputId: Int,
        count: Int,
        newest: File? = null,
        exclude: Set<String> = emptySet(),
    ): List<File> {
        if (count <= 0) return emptyList()
        val bound = newest?.takeIf { isOwnSegment(it) }?.let { segmentOrderKey(it) }
        return listSegments(dir)
            .filter { segmentInput(it) == inputId && it.name !in exclude }
            .filter { bound == null || compareKeys(segmentOrderKey(it), bound) <= 0 }
            .takeLast(count)
    }

    private fun segmentOrderKey(file: File): Pair<String, String> = matchTimestamp(file) to file.name

    private fun compareKeys(a: Pair<String, String>, b: Pair<String, String>): Int =
        compareValuesBy(a, b, { it.first }, { it.second })

    /** Best-effort file copy; an existing destination is skipped, failures remove the partial copy. */
    fun copyFile(source: File, destination: File): Boolean = try {
        if (destination.exists()) {
            false
        } else {
            destination.outputStream().use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            }
            true
        }
    } catch (_: Throwable) {
        runCatching { destination.delete() }
        false
    }
}
