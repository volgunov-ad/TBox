package vad.dashing.dataresize.inventory

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Filename and USB/sdcard/intsd destination ranking for the partition report.
 * Pure helpers — unit-tested without a device.
 */
object PartitionInventoryPaths {
    /** Same search roots as tools/hu_rescue USB file lookup (udisk, media_rw, storage). */
    val SEARCH_GLOBS = listOf("/udisk*", "/mnt/media_rw/*", "/storage/*")

    private val NON_USB_BASENAMES = setOf(
        "internal_sdcard",
        "emulated",
        "self",
        "sdcard0",
        "sdcard1",
    )

    fun sanitizeIncremental(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(48)
        return cleaned.ifBlank { "unknown" }
    }

    fun reportFileName(incremental: String, at: Date = Date(), timeZone: TimeZone = TimeZone.getDefault()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply { this.timeZone = timeZone }.format(at)
        return "tbox_part_report_${sanitizeIncremental(incremental)}_$stamp.md"
    }

    /** True when [path] looks like removable USB (not intsd / emulated). */
    fun isUsbMount(path: String): Boolean {
        val normalized = path.trimEnd('/')
        if (normalized.startsWith("/udisk")) return true
        val base = normalized.substringAfterLast('/')
        if (base in NON_USB_BASENAMES) return false
        if (normalized.startsWith("/mnt/media_rw/")) return base.isNotEmpty() && base !in NON_USB_BASENAMES
        if (normalized.startsWith("/storage/")) {
            if (normalized.startsWith("/storage/emulated") || base == "self") return false
            // Volume UUID like ABCD-1234 is typical for USB on Adayo A10.
            return base.matches(Regex("[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}")) ||
                (base.isNotEmpty() && base !in NON_USB_BASENAMES)
        }
        return false
    }

    /**
     * Prefer USB, then `/sdcard`, then intsd. [candidates] are absolute existing writable dirs
     * discovered on the HU (one line each from the probe script).
     */
    fun pickOutputDir(candidates: List<String>): Pair<OutputKind, String>? {
        val unique = candidates.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.distinct()
        unique.firstOrNull { isUsbMount(it) }?.let { return OutputKind.Usb to it }
        unique.firstOrNull { it == "/sdcard" || it == "/storage/emulated/0" }?.let { return OutputKind.Sdcard to it }
        unique.firstOrNull {
            it.endsWith("/internal_sdcard") || it == "/sdcard/internal_sdcard"
        }?.let { return OutputKind.Intsd to it }
        unique.firstOrNull()?.let { return OutputKind.Sdcard to it }
        return null
    }

    fun kindLabel(kind: OutputKind): String = when (kind) {
        OutputKind.Usb -> "USB"
        OutputKind.Sdcard -> "/sdcard"
        OutputKind.Intsd -> "intsd"
    }
}
