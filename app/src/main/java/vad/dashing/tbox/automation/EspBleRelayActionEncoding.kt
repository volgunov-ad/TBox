package vad.dashing.tbox.automation

import vad.dashing.tbox.esp.EspBleRelayChannel
import vad.dashing.tbox.esp.EspBleRelayIds

/**
 * BLE relay actions keep a legacy board slot in [AutomationAction.Builtin.intValue].
 * The board that actually runs is [idOf]: eight hex characters. The coil is
 * [channelOf], a 0-based mask bit (0 = the X1 coil, shown as channel 1).
 *
 * Packed form: `id=<hex> ch=<0-7>` and, for a pulse, `ms=<digits>`.
 * Older saves are only the slot in intValue, a bare 8-hex id, or a pulse duration.
 */
internal object EspBleRelayActionEncoding {
    private val idOnly = Regex("^[0-9a-fA-F]{8}$")

    fun idOf(action: AutomationAction.Builtin): String = parse(action.stringValue).id

    /** Null when the text has no `ch=` field. Callers then use [EspBleRelayChannel.DEFAULT]. */
    fun channelField(action: AutomationAction.Builtin): Int? = parse(action.stringValue).channel

    fun channelOf(action: AutomationAction.Builtin): Int =
        channelField(action) ?: EspBleRelayChannel.DEFAULT

    fun pulseText(action: AutomationAction.Builtin): String = parse(action.stringValue).pulse

    fun pulseMs(action: AutomationAction.Builtin): Long? =
        pulseText(action).toLongOrNull()?.takeIf { it > 0L }

    fun pack(
        type: AutomationBuiltinActionType,
        id: String,
        channel: Int,
        pulseText: String,
    ): String {
        val parts = mutableListOf<String>()
        val hex = EspBleRelayIds.normalize(id)
        if (hex.isNotEmpty()) parts += "id=$hex"
        if (EspBleRelayChannel.inRange(channel)) parts += "ch=$channel"
        if (type == AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE) {
            val ms = pulseText.trim()
            if (ms.isNotEmpty()) parts += "ms=$ms"
        }
        return parts.joinToString(" ")
    }

    private fun parse(raw: String): Parsed {
        val text = raw.trim()
        if (text.isEmpty()) return Parsed("", null, "")
        if (idOnly.matches(text)) return Parsed(text.lowercase(), null, "")
        if (text.all { it.isDigit() }) return Parsed("", null, text)
        var id = ""
        var channel: Int? = null
        var pulse = ""
        for (part in text.split(Regex("\\s+"))) {
            when {
                part.startsWith("id=") -> id = part.removePrefix("id=")
                part.startsWith("ch=") -> channel = part.removePrefix("ch=").toIntOrNull()
                part.startsWith("ms=") -> pulse = part.removePrefix("ms=")
            }
        }
        return Parsed(
            id = if (idOnly.matches(id)) id.lowercase() else "",
            channel = channel,
            pulse = pulse,
        )
    }

    private data class Parsed(val id: String, val channel: Int?, val pulse: String)
}
