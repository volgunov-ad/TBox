package vad.dashing.camd

import android.content.SharedPreferences

/**
 * Per-camera visual lens tweak applied in the GLES preview shader.
 *
 * This is **not** a calibrated Brown–Conrady model (no fx/fy, no real intrinsics).
 * k1/k2 are practical radial coefficients; cx/cy are optional offsets from image center (0.5).
 * tiltV/tiltH are keystone factors for cameras mounted at an angle (perspective trapezoid).
 * Clients (DCL / Monitor) do not send these over OPEN — they live only in camd prefs.
 */
data class CamdLensCorrection(
    val k1: Float = 0f,
    val k2: Float = 0f,
    val cx: Float = 0f,
    val cy: Float = 0f,
    val tiltV: Float = 0f,
    val tiltH: Float = 0f,
) {
    fun isIdentity(): Boolean =
        k1 == 0f && k2 == 0f && cx == 0f && cy == 0f && tiltV == 0f && tiltH == 0f

    companion object {
        val IDENTITY = CamdLensCorrection()

        /** Mild barrel (edges pull outward when sampling). */
        val PRESET_MILD_BARREL = CamdLensCorrection(k1 = -0.25f, k2 = -0.05f)

        /** Stronger barrel for wide surround cams. */
        val PRESET_STRONG_BARREL = CamdLensCorrection(k1 = -0.45f, k2 = -0.12f)

        /** Mild pincushion. */
        val PRESET_MILD_PINCUSHION = CamdLensCorrection(k1 = 0.20f, k2 = 0.05f)

        const val K_MIN = -1f
        const val K_MAX = 1f
        const val C_MIN = -0.25f
        const val C_MAX = 0.25f
        const val TILT_MIN = -0.5f
        const val TILT_MAX = 0.5f

        fun clamp(value: CamdLensCorrection): CamdLensCorrection =
            CamdLensCorrection(
                k1 = value.k1.coerceIn(K_MIN, K_MAX),
                k2 = value.k2.coerceIn(K_MIN, K_MAX),
                cx = value.cx.coerceIn(C_MIN, C_MAX),
                cy = value.cy.coerceIn(C_MIN, C_MAX),
                tiltV = value.tiltV.coerceIn(TILT_MIN, TILT_MAX),
                tiltH = value.tiltH.coerceIn(TILT_MIN, TILT_MAX),
            )
    }
}

object CamdLensCorrectionStore {
    fun load(prefs: SharedPreferences, inputId: Int): CamdLensCorrection {
        return CamdLensCorrection.clamp(
            CamdLensCorrection(
                k1 = prefs.getFloat(key(inputId, "k1"), 0f),
                k2 = prefs.getFloat(key(inputId, "k2"), 0f),
                cx = prefs.getFloat(key(inputId, "cx"), 0f),
                cy = prefs.getFloat(key(inputId, "cy"), 0f),
                tiltV = prefs.getFloat(key(inputId, "tilt_v"), 0f),
                tiltH = prefs.getFloat(key(inputId, "tilt_h"), 0f),
            ),
        )
    }

    fun save(prefs: SharedPreferences, inputId: Int, value: CamdLensCorrection) {
        val clamped = CamdLensCorrection.clamp(value)
        prefs.edit()
            .putFloat(key(inputId, "k1"), clamped.k1)
            .putFloat(key(inputId, "k2"), clamped.k2)
            .putFloat(key(inputId, "cx"), clamped.cx)
            .putFloat(key(inputId, "cy"), clamped.cy)
            .putFloat(key(inputId, "tilt_v"), clamped.tiltV)
            .putFloat(key(inputId, "tilt_h"), clamped.tiltH)
            .apply()
    }

    fun reset(prefs: SharedPreferences, inputId: Int) {
        save(prefs, inputId, CamdLensCorrection.IDENTITY)
    }

    private fun key(inputId: Int, field: String): String = "lens_${inputId}_$field"
}
