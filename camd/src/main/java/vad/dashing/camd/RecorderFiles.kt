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
        get() = BIT_RATE_BYTES_PER_SEC * (SEGMENT_MS / 1000L)

    private const val BIT_RATE_BYTES_PER_SEC = 750_000L

    /** Optional `_N` suffix disambiguates same-second segments (emergency-save close). */
    val SEGMENT_REGEX = Regex("cam(\\d+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?:_(\\d+))?\\.mp4")

    fun segmentName(inputId: Int, at: Date = Date(), suffix: Int = 0): String =
        "cam${inputId}_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(at) +
            (if (suffix > 0) "_$suffix" else "") + ".mp4"

    fun isOwnSegment(file: File): Boolean = SEGMENT_REGEX.matches(file.name)

    /** Own segments sorted oldest first (the name embeds the start timestamp). */
    fun listSegments(dir: File): List<File> =
        dir.listFiles()?.filter { it.isFile && isOwnSegment(it) }?.sortedWith(
            compareBy(
                { matchTimestamp(it) },
                { it.name },
            ),
        ) ?: emptyList()

    private fun matchTimestamp(file: File): String {
        val match = SEGMENT_REGEX.find(file.name) ?: return ""
        return match.groupValues[2]
    }

    fun totalBytes(dir: File): Long = listSegments(dir).sumOf { it.length() }

    /**
     * Deletes own oldest segments until the folder fits [quotaBytes] and the volume
     * keeps at least [reserveBytes] free. Foreign files are never touched.
     * Returns false when even a folder without own segments cannot satisfy the limits.
     */
    fun enforceQuota(dir: File, quotaBytes: Long, reserveBytes: Long): Boolean {
        if (quotaBytes <= 0) return false
        val segments = listSegments(dir).toMutableList()
        fun fits(): Boolean =
            segments.sumOf { it.length() } <= quotaBytes && dir.usableSpace >= reserveBytes
        while (!fits() && segments.isNotEmpty()) {
            segments.removeAt(0).delete()
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
     * Latest [count] own segments of one camera ([inputId]), newest first.
     * All finished files from [listSegments]; the in-flight segment is closed by the
     * engine before this runs ([RecorderEngine.requestCloseNow]).
     */
    fun sosSegmentsForCamera(dir: File, inputId: Int, count: Int): List<File> {
        if (count <= 0) return emptyList()
        return listSegments(dir).filter { segmentInput(it) == inputId }.takeLast(count)
    }

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
