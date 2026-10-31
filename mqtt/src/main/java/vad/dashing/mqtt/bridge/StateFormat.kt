package vad.dashing.mqtt.bridge

import vad.dashing.mqtt.ha.HaComponent
import java.util.Locale
import kotlin.math.abs

object StateFormat {
    /** HA binary_sensor/switch use ON/OFF payloads; a select must echo its option verbatim. */
    fun text(raw: String, unit: String, component: HaComponent): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (component == HaComponent.BINARY_SENSOR || component == HaComponent.SWITCH) {
            if (trimmed.equals("on", ignoreCase = true)) return "ON"
            if (trimmed.equals("off", ignoreCase = true)) return "OFF"
        }
        if (isMissingTemperature(trimmed, unit)) return null
        return trimmed
    }

    fun numberText(value: Double, unit: String): String? {
        if (!value.isFinite()) return null
        if (isMissingTemperature(value, unit)) return null
        return formatNumber(value)
    }

    fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (abs(rounded - rounded.toLong()) < 0.0001) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.3f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    private fun isMissingTemperature(value: Double, unit: String): Boolean =
        unit == "°C" && abs(value - (-40.0)) < 0.001

    private fun isMissingTemperature(raw: String, unit: String): Boolean {
        if (unit != "°C") return false
        val number = raw.toDoubleOrNull() ?: return false
        return abs(number - (-40.0)) < 0.001
    }
}
