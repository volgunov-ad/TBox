package vad.dashing.camd

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure file-level logic of the dashcam recorder: segment naming, listing and quota
 * rotation. No Android dependencies so the quota rules are unit-testable.
 *
 * Platform [android.media.MediaMuxer] MPEG-4 writes the moov atom only on [MediaMuxer.stop].
 * Short segments therefore act as crash-safe “frequent moov”: a power cut loses at most
 * one in-flight segment (~[SEGMENT_MS]), and every finished file remains playable (P1-3).
 */
object RecorderFiles {
    /** Wall-clock length of one MP4 segment. Short on purpose for power-cut safety. */
    const val SEGMENT_MS = 30_000L

    /** Rough bytes of one segment at the fixed 6 Mbps — used as free-space reserve. */
    const val SEGMENT_ESTIMATE_BYTES = 25L * 1024 * 1024

    val SEGMENT_REGEX = Regex("cam(\\d+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.mp4")

    fun segmentName(inputId: Int, at: Date = Date()): String =
        "cam${inputId}_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(at) + ".mp4"

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
}
