package vad.dashing.tbox.automation

import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.BodyComfortWrite
import vad.dashing.tbox.mbcan.MbCanCommandPolicy
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

/**
 * Portable CAN action values for A9/A10.
 *
 * Binary toggles and windows use different raw ints per head-unit backend. Rules may store either
 * a legacy raw [AutomationAction.CanCommand.value] or a semantic [AutomationAction.CanCommand.valueKey]
 * (`on`/`off`, `close`/`open`/`vent`, …). Resolve to the write int for the current mode at execute
 * time; legacy ints keep working and are remapped when possible.
 */
object AutomationCanValueCodec {
    const val KEY_ON = "on"
    const val KEY_OFF = "off"
    const val KEY_CLOSE = "close"
    const val KEY_OPEN = "open"
    const val KEY_VENT = "vent"
    /** A9 comfort-open stop (80%); maps to full open on A10. */
    const val KEY_COMFORT_OPEN = "comfort_open"

    private val WINDOW_KEYS = setOf(KEY_CLOSE, KEY_OPEN, KEY_VENT, KEY_COMFORT_OPEN)
    private val BINARY_KEYS = setOf(KEY_ON, KEY_OFF)

    fun normalizeKey(raw: String?): String? =
        raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /**
     * Normalizes a decoded command that already has a semantic [AutomationAction.CanCommand.valueKey].
     * Legacy int-only commands are left unchanged so existing JSON round-trips bit-for-bit.
     */
    fun canonicalize(action: AutomationAction.CanCommand): AutomationAction.CanCommand {
        if (action.operation == AutomationCanOperation.TOGGLE) {
            return action.copy(valueKey = null)
        }
        val key = normalizeKey(action.valueKey) ?: return action.copy(valueKey = null)
        val a9 = resolveWriteValue(action.copy(valueKey = key), HeadUnitCanMode.Android9MbCan)
            ?: return action.copy(valueKey = key)
        return action.copy(value = a9, valueKey = key)
    }

    /**
     * UI / new saves: attach a portable [AutomationAction.CanCommand.valueKey] when the raw int
     * is a known binary or window/trunk semantic so exports work on both head units.
     */
    fun withPortableKey(action: AutomationAction.CanCommand): AutomationAction.CanCommand {
        if (action.operation == AutomationCanOperation.TOGGLE) {
            return action.copy(valueKey = null)
        }
        val existing = normalizeKey(action.valueKey)
        if (existing != null) return canonicalize(action.copy(valueKey = existing))
        val entry = AutomationCanCatalog.get(action.bus, action.propertyId) ?: return action
        val inferred = inferKey(entry, action.operation, action.value) ?: return action
        return canonicalize(action.copy(valueKey = inferred))
    }

    fun inferKey(
        entry: AutomationCanCatalogEntry,
        operation: AutomationCanOperation,
        value: Int,
    ): String? {
        if (operation == AutomationCanOperation.TOGGLE) return null
        when (entry.policy) {
            is MbCanCommandPolicy.ToggleBinary -> {
                return when (value) {
                    entry.policy.offValue -> KEY_OFF
                    entry.policy.onValue -> KEY_ON
                    else -> null
                }
            }
            is MbCanCommandPolicy.SetWindowPosition -> return windowKeyFromRaw(value)
            else -> Unit
        }
        if (operation == AutomationCanOperation.TRUNK_PULSE) {
            return when (value) {
                1 -> KEY_OPEN
                2 -> KEY_CLOSE
                else -> null
            }
        }
        return null
    }

    fun windowKeyFromRaw(value: Int): String? = when (value) {
        0, MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE -> KEY_CLOSE
        BodyComfortDomain.WINDOW_A9_VENT_PERCENT,
        MbCanKnownVehiclePropertyId.WINDOW_A10_VENT,
        -> KEY_VENT
        BodyComfortDomain.WINDOW_A9_COMFORT_OPEN_PERCENT -> KEY_COMFORT_OPEN
        100, MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN -> KEY_OPEN
        else -> null
    }

    fun windowRawForMode(key: String, mode: HeadUnitCanMode): Int? {
        val normalized = normalizeKey(key) ?: return null
        return when (mode) {
            HeadUnitCanMode.Android9MbCan -> when (normalized) {
                KEY_CLOSE -> 0
                KEY_VENT -> BodyComfortDomain.WINDOW_A9_VENT_PERCENT
                KEY_COMFORT_OPEN -> BodyComfortDomain.WINDOW_A9_COMFORT_OPEN_PERCENT
                KEY_OPEN -> 100
                else -> null
            }
            HeadUnitCanMode.Android10Vhal -> when (normalized) {
                KEY_CLOSE -> MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE
                KEY_VENT -> MbCanKnownVehiclePropertyId.WINDOW_A10_VENT
                KEY_COMFORT_OPEN, KEY_OPEN -> MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN
                else -> null
            }
        }
    }

    /** Remap a raw window write between A9 percent steps and A10 1/2/3 commands. */
    fun remapWindowValue(value: Int, mode: HeadUnitCanMode): Int? =
        BodyComfortWrite.remapWindowValueForMode(value, mode)

    /**
     * Write int for [mode]. Prefer [AutomationAction.CanCommand.valueKey] when set; otherwise
     * remap portable legacy ints (windows). Binary legacy ints stay on the A9 policy scale —
     * [vad.dashing.tbox.mbcan.VhalBinaryToggleCodec] remaps them on A10 SetProperty.
     */
    fun resolveWriteValue(
        action: AutomationAction.CanCommand,
        mode: HeadUnitCanMode,
    ): Int? {
        val entry = AutomationCanCatalog.get(action.bus, action.propertyId) ?: return null
        if (action.operation == AutomationCanOperation.TOGGLE) return 0

        val key = normalizeKey(action.valueKey)
        if (key != null) {
            return when {
                entry.policy is MbCanCommandPolicy.ToggleBinary && key in BINARY_KEYS -> {
                    when (key) {
                        KEY_ON -> entry.policy.onValue
                        KEY_OFF -> entry.policy.offValue
                        else -> null
                    }
                }
                entry.policy is MbCanCommandPolicy.SetWindowPosition && key in WINDOW_KEYS ->
                    windowRawForMode(key, mode)
                action.operation == AutomationCanOperation.TRUNK_PULSE && key in setOf(KEY_OPEN, KEY_CLOSE) ->
                    when (key) {
                        KEY_OPEN -> 1
                        KEY_CLOSE -> 2
                        else -> null
                    }
                else -> null
            }
        }

        return when {
            entry.policy is MbCanCommandPolicy.SetWindowPosition ->
                remapWindowValue(action.value, mode)
            else -> action.value
        }
    }

    /** True when [action] can produce a legal write on [mode] (or on either mode if [mode] is null). */
    fun isResolvable(
        action: AutomationAction.CanCommand,
        mode: HeadUnitCanMode?,
    ): Boolean {
        val entry = AutomationCanCatalog.get(action.bus, action.propertyId) ?: return false
        if (action.operation == AutomationCanOperation.TOGGLE) {
            return AutomationCanOperation.TOGGLE in entry.allowedOperations
        }
        if (mode != null) {
            val write = resolveWriteValue(action, mode) ?: return false
            return when (action.operation) {
                AutomationCanOperation.TRUNK_PULSE -> write in setOf(1, 2)
                AutomationCanOperation.SET -> write in entry.allowedValuesFor(mode)
                AutomationCanOperation.TOGGLE -> true
            }
        }
        return HeadUnitCanMode.entries.any { candidate ->
            entry.supports(candidate) && isResolvable(action, candidate)
        }
    }

    fun encodeJsonValue(action: AutomationAction.CanCommand): Any {
        val key = normalizeKey(action.valueKey)
        return if (key != null) key else action.value
    }
}
