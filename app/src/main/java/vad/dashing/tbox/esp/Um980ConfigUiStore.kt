package vad.dashing.tbox.esp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot + busy/log for UM980 settings opened over **direct USB**
 * (separate from [EspCompanionRepository] companion snapshot).
 */
object Um980ConfigUiStore {
    private val _snapshot = MutableStateFlow(Um980ConfigSnapshot())
    val snapshot: StateFlow<Um980ConfigSnapshot> = _snapshot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _trafficLog = MutableStateFlow<List<String>>(emptyList())
    val trafficLog: StateFlow<List<String>> = _trafficLog.asStateFlow()

    fun setBusy(value: Boolean) {
        _busy.value = value
    }

    fun replaceSnapshot(snapshot: Um980ConfigSnapshot) {
        _snapshot.value = snapshot
    }

    fun mergeReplyLines(cmd: String, lines: List<String>) {
        appendLog("TX $cmd")
        for (line in lines.take(40)) {
            appendLog("RX $line")
        }
        val merged = (_snapshot.value.rawLines + lines).takeLast(400)
        if (cmd.equals("CONFIG", ignoreCase = true) ||
            cmd.equals("MODE", ignoreCase = true) ||
            cmd.equals("MASK", ignoreCase = true) ||
            cmd.equals("VERSIONA", ignoreCase = true) ||
            lines.any { it.contains("VERSIONA", ignoreCase = true) }
        ) {
            _snapshot.value = Um980Commands.parseConfigSnapshot(merged)
        } else {
            _snapshot.value = _snapshot.value.copy(rawLines = merged)
        }
    }

    fun appendLog(line: String) {
        _trafficLog.value = (_trafficLog.value + line).takeLast(200)
    }

    fun clearLog() {
        _trafficLog.value = emptyList()
    }
}
