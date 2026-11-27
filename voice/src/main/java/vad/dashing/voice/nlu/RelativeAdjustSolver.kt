package vad.dashing.voice.nlu

import vad.dashing.voice.api.CatalogWriteOption
import vad.dashing.voice.api.CatalogWriteSchema
import kotlin.math.abs
import kotlin.math.round

/**
 * Maps a current signal reading + relative/absolute request onto a write option
 * or a continuous number suitable for External API invoke.
 */
object RelativeAdjustSolver {
    data class Solved(
        /** Human-facing state label when known (e.g. "22.5", "on"). */
        val state: String?,
        /** Value for can_command `value` or builtin intValue source. */
        val invokeValue: Any,
    )

    fun relative(
        current: Double,
        write: CatalogWriteSchema,
        steps: Int,
    ): Solved? {
        val step = write.step ?: defaultStep(write)
        val desired = current + steps * step
        return absolute(desired, write)
    }

    fun absolute(desired: Double, write: CatalogWriteSchema): Solved? {
        if (write.options.isNotEmpty()) {
            val nearest = nearestOption(write.options, desired) ?: return null
            return Solved(state = nearest.state, invokeValue = nearest.value)
        }
        // Continuous number (set_media_volume): clamp to min/max, round to step.
        val step = write.step ?: 1.0
        val min = write.min ?: Double.NEGATIVE_INFINITY
        val max = write.max ?: Double.POSITIVE_INFINITY
        var value = desired.coerceIn(min, max)
        if (step > 0) {
            value = round(value / step) * step
            value = value.coerceIn(min, max)
        }
        val asInt = if (abs(value - value.toLong()) < 1e-6) value.toLong().toInt() else null
        return if (asInt != null) {
            Solved(state = asInt.toString(), invokeValue = asInt)
        } else {
            Solved(state = formatNumber(value), invokeValue = value)
        }
    }

    fun binary(on: Boolean, write: CatalogWriteSchema): Solved? {
        val state = if (on) "on" else "off"
        val option = write.options.firstOrNull { it.state == state } ?: return null
        return Solved(state = option.state, invokeValue = option.value)
    }

    fun pulse(open: Boolean, write: CatalogWriteSchema): Solved? {
        val state = if (open) "open" else "close"
        val option = write.options.firstOrNull { it.state == state } ?: return null
        return Solved(state = option.state, invokeValue = option.value)
    }

    fun readingToDouble(value: Any?): Double? =
        when (value) {
            is Number -> value.toDouble()
            is String -> value.replace(',', '.').toDoubleOrNull()
            else -> null
        }

    private fun nearestOption(
        options: List<CatalogWriteOption>,
        desired: Double,
    ): CatalogWriteOption? =
        options
            .mapNotNull { option ->
                val number = option.state.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
                option to abs(number - desired)
            }
            .minByOrNull { it.second }
            ?.first

    private fun defaultStep(write: CatalogWriteSchema): Double =
        when (write.kind) {
            CatalogWriteSchema.KIND_NUMBER -> 1.0
            else -> 1.0
        }

    private fun formatNumber(value: Double): String {
        val nearest = round(value * 10.0) / 10.0
        return if (abs(nearest - nearest.toLong()) < 0.001) {
            nearest.toLong().toString()
        } else {
            String.format("%.1f", nearest)
        }
    }
}
