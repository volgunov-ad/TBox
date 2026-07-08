package vad.dashing.tbox.ui.launcher

/** Steering visual tuning for 3D car and virtual road. */
internal object LauncherSteerVisual {
    const val INVERT = true

    const val SENSITIVITY = 0.38f
    const val MAX_VISUAL_DEG = 45f

    /** Frame smoothing rate — lower = smoother, less jerky. */
    const val STEER_SMOOTH_RATE = 3.2f

    fun visualSteerDeg(rawDeg: Float): Float {
        val scaled = rawDeg * SENSITIVITY
        val signed = if (INVERT) -scaled else scaled
        return signed.coerceIn(-MAX_VISUAL_DEG, MAX_VISUAL_DEG)
    }

    fun visualSteerNorm(rawDeg: Float): Float =
        (visualSteerDeg(rawDeg) / MAX_VISUAL_DEG).coerceIn(-1f, 1f)
}
