package vad.dashing.tbox.obd

import vad.dashing.tbox.ThemeBundleExport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export last-read OBD DTC / freeze-frame snapshot to Downloads as UTF-8 `.txt`.
 */
object ObdDtcExport {
    const val FILE_PREFIX = "tbox_obd_dtc_"
    const val FILE_EXTENSION = "txt"

    data class Snapshot(
        val exportedAtMs: Long = System.currentTimeMillis(),
        val protocolDescription: String? = null,
        val protocolNumber: String? = null,
        val adapterVersion: String? = null,
        val stored: List<ObdDtc> = emptyList(),
        val storedReadAtMs: Long = 0L,
        val storedRead: Boolean = false,
        val pending: List<ObdDtc> = emptyList(),
        val pendingReadAtMs: Long = 0L,
        val pendingRead: Boolean = false,
        val freezeFrameDtc: ObdDtc? = null,
        val freezeFrameValues: Map<String, Double> = emptyMap(),
        val freezeFrameReadAtMs: Long = 0L,
        val freezeFrameRead: Boolean = false,
    )

    fun snapshotFromRepository(nowMs: Long = System.currentTimeMillis()): Snapshot =
        Snapshot(
            exportedAtMs = nowMs,
            protocolDescription = ObdRepository.protocolDescription.value,
            protocolNumber = ObdRepository.protocolNumber.value,
            adapterVersion = ObdRepository.adapterVersion.value,
            stored = ObdRepository.dtcCodes.value,
            storedReadAtMs = ObdRepository.dtcLastReadAtMs.value,
            storedRead = ObdRepository.dtcReadEverSucceeded.value,
            pending = ObdRepository.pendingDtcCodes.value,
            pendingReadAtMs = ObdRepository.pendingDtcLastReadAtMs.value,
            pendingRead = ObdRepository.pendingDtcReadEverSucceeded.value,
            freezeFrameDtc = ObdRepository.freezeFrameDtc.value,
            freezeFrameValues = ObdRepository.freezeFrameValues.value,
            freezeFrameReadAtMs = ObdRepository.freezeFrameLastReadAtMs.value,
            freezeFrameRead = ObdRepository.freezeFrameReadEverSucceeded.value,
        )

    fun fileName(timestampMs: Long = System.currentTimeMillis()): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestampMs))
        return "$FILE_PREFIX$ts.$FILE_EXTENSION"
    }

    fun formatText(snapshot: Snapshot): String {
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fun ts(ms: Long): String =
            if (ms > 0L) timeFmt.format(Date(ms)) else "—"

        return buildString {
            appendLine("TBox OBD DTC export")
            appendLine("Exported: ${ts(snapshot.exportedAtMs)}")
            snapshot.adapterVersion?.takeIf { it.isNotBlank() }?.let {
                appendLine("Adapter: $it")
            }
            snapshot.protocolDescription?.takeIf { it.isNotBlank() }?.let {
                appendLine("Protocol: $it")
            }
            snapshot.protocolNumber?.takeIf { it.isNotBlank() }?.let {
                appendLine("Protocol #: $it")
            }
            appendLine()

            appendLine("=== Stored (Mode 03) ===")
            appendLine("Last read: ${if (snapshot.storedRead) ts(snapshot.storedReadAtMs) else "not read"}")
            appendCodes(snapshot.stored, snapshot.storedRead)
            appendLine()

            appendLine("=== Pending (Mode 07) ===")
            appendLine("Last read: ${if (snapshot.pendingRead) ts(snapshot.pendingReadAtMs) else "not read"}")
            appendCodes(snapshot.pending, snapshot.pendingRead)
            appendLine()

            appendLine("=== Freeze frame (Mode 02) ===")
            appendLine("Last read: ${if (snapshot.freezeFrameRead) ts(snapshot.freezeFrameReadAtMs) else "not read"}")
            if (!snapshot.freezeFrameRead) {
                appendLine("(not read)")
            } else if (snapshot.freezeFrameDtc == null && snapshot.freezeFrameValues.isEmpty()) {
                appendLine("(empty / NO DATA)")
            } else {
                appendLine("DTC: ${snapshot.freezeFrameDtc?.code ?: "—"}")
                val byId = ObdPid.entries.associateBy { it.id }
                for ((id, value) in snapshot.freezeFrameValues.toSortedMap()) {
                    val label = byId[id]?.id ?: id
                    appendLine("$label=$value")
                }
            }
        }
    }

    private fun StringBuilder.appendCodes(codes: List<ObdDtc>, read: Boolean) {
        when {
            !read -> appendLine("(not read)")
            codes.isEmpty() -> appendLine("(none)")
            else -> codes.forEach { appendLine(it.code) }
        }
    }

    fun writeToDownloads(snapshot: Snapshot = snapshotFromRepository()): File {
        val dest = File(ThemeBundleExport.downloadsDir(), fileName(snapshot.exportedAtMs))
        dest.writeText(formatText(snapshot), Charsets.UTF_8)
        return dest
    }
}
