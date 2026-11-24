package vad.dashing.tbox.automation

/**
 * BLE relay actions keep the board index in [AutomationAction.Builtin.intValue].
 * The board id, when the user set one, lives in [AutomationAction.Builtin.stringValue]:
 * eight hex characters for set/toggle, or `id=<hex> ms=<digits>` for a pulse.
 * A pulse that is only a number is the older duration-only form.
 */
internal object EspBleRelayActionEncoding {
    private val idOnly = Regex("^[0-9a-fA-F]{8}$")
    private val packed = Regex("""id=([0-9a-fA-F]{8})(?:\s+ms=(\d+))?""")
    private val packedPartial = Regex("""id=([0-9a-fA-F]{0,8})(?:\s+ms=(\d*))?""")

    fun idOf(action: AutomationAction.Builtin): String {
        val raw = action.stringValue.trim()
        if (action.type == AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE) {
            return packed.find(raw)?.groupValues?.get(1)?.lowercase().orEmpty()
        }
        return if (idOnly.matches(raw)) raw.lowercase() else ""
    }

    /** What the editor shows. A short id is kept so typing is not wiped. */
    fun idText(action: AutomationAction.Builtin): String {
        val raw = action.stringValue.trim()
        if (action.type == AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE) {
            return packedPartial.find(raw)?.groupValues?.get(1)?.lowercase().orEmpty()
        }
        return raw.lowercase()
    }

    fun pulseText(action: AutomationAction.Builtin): String {
        val raw = action.stringValue.trim()
        val partial = packedPartial.find(raw)
        if (partial != null) return partial.groupValues.getOrNull(2).orEmpty()
        if (idOnly.matches(raw)) return ""
        return raw
    }

    fun pulseMs(action: AutomationAction.Builtin): Long? =
        pulseText(action).toLongOrNull()?.takeIf { it > 0L }

    fun pack(type: AutomationBuiltinActionType, id: String, pulseText: String): String {
        val hex = id.trim().lowercase()
        if (type != AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE) {
            return hex
        }
        val ms = pulseText.trim()
        return when {
            hex.isEmpty() -> ms
            ms.isEmpty() -> "id=$hex"
            else -> "id=$hex ms=$ms"
        }
    }
}
