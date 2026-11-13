package vad.dashing.camd

import android.content.SharedPreferences

/**
 * Sub-rectangle of the (lens-corrected) frame shown inside the window.
 * Fractions of the full frame; the crop is stretched to fill the window.
 */
data class CamdCrop(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 1f,
    val h: Float = 1f,
) {
    fun isIdentity(): Boolean =
        x == 0f && y == 0f && w == 1f && h == 1f

    companion object {
        val IDENTITY = CamdCrop()

        const val MIN_SIZE = 0.05f
        const val EDGE_TOLERANCE = 1.02f
        private const val GRID = 10_000f

        fun isValid(value: CamdCrop): Boolean {
            val fields = listOf(value.x, value.y, value.w, value.h)
            if (fields.any { it !in 0f..1f }) return false
            if (value.w < MIN_SIZE || value.h < MIN_SIZE) return false
            if (value.x + value.w > EDGE_TOLERANCE) return false
            if (value.y + value.h > EDGE_TOLERANCE) return false
            return true
        }

        fun clamp(value: CamdCrop): CamdCrop {
            val w = toGrid(value.w.coerceIn(MIN_SIZE, 1f))
            val h = toGrid(value.h.coerceIn(MIN_SIZE, 1f))
            val x = toGrid(value.x.coerceIn(0f, 1f).coerceAtMost(1f - w))
            val y = toGrid(value.y.coerceIn(0f, 1f).coerceAtMost(1f - h))
            return CamdCrop(x, y, w, h)
        }

        private fun toGrid(value: Float): Float = kotlin.math.round(value * GRID) / GRID
    }
}

object CamdCropStore {
    fun load(prefs: SharedPreferences, inputId: Int): CamdCrop {
        return CamdCrop.clamp(
            CamdCrop(
                x = prefs.getFloat(key(inputId, "x"), 0f),
                y = prefs.getFloat(key(inputId, "y"), 0f),
                w = prefs.getFloat(key(inputId, "w"), 1f),
                h = prefs.getFloat(key(inputId, "h"), 1f),
            ),
        )
    }

    fun save(prefs: SharedPreferences, inputId: Int, value: CamdCrop) {
        val clamped = CamdCrop.clamp(value)
        prefs.edit()
            .putFloat(key(inputId, "x"), clamped.x)
            .putFloat(key(inputId, "y"), clamped.y)
            .putFloat(key(inputId, "w"), clamped.w)
            .putFloat(key(inputId, "h"), clamped.h)
            .apply()
    }

    fun reset(prefs: SharedPreferences, inputId: Int) {
        save(prefs, inputId, CamdCrop.IDENTITY)
    }

    private fun key(inputId: Int, field: String): String = "crop_${inputId}_$field"
}
