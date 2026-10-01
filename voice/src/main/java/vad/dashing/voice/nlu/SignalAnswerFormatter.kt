package vad.dashing.voice.nlu

import vad.dashing.voice.api.CatalogSignal
import vad.dashing.voice.api.SignalReading
import kotlin.math.abs

object SignalAnswerFormatter {
    private val SOURCE_PRIORITY = listOf("head_unit", "app", "tbox")

    fun preferredSource(signal: CatalogSignal): String? {
        for (preferred in SOURCE_PRIORITY) {
            if (preferred in signal.sources) return preferred
        }
        return signal.sources.firstOrNull()
    }

    fun format(signal: CatalogSignal, reading: SignalReading?): String {
        if (reading == null || !reading.available || reading.value == null) {
            return "Нет данных: ${signal.label}"
        }
        if (isSentinelUnavailable(signal.id, reading.value)) {
            return "Нет данных: ${signal.label}"
        }
        return when (signal.valueType) {
            "state" -> {
                val raw = reading.value.toString()
                val named = signal.namedValues[raw]
                if (named != null) {
                    "${signal.label}: $named"
                } else {
                    "${signal.label}: $raw"
                }
            }
            "number" -> {
                val number = when (val v = reading.value) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                    else -> null
                }
                if (number == null) {
                    "${signal.label}: ${reading.value}"
                } else {
                    val pretty = if (abs(number % 1.0) < 1e-6) {
                        number.toLong().toString()
                    } else {
                        String.format("%.1f", number)
                    }
                    val unit = signal.unit.trim()
                    if (unit.isEmpty()) {
                        "${signal.label}: $pretty"
                    } else {
                        "${signal.label}: $pretty $unit"
                    }
                }
            }
            else -> "${signal.label}: ${reading.value}"
        }
    }

    private fun isSentinelUnavailable(signalId: String, value: Any): Boolean {
        if (signalId != "outside_temperature" && signalId != "inside_temperature") return false
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: return false
            else -> return false
        }
        return number <= -40.0
    }
}
