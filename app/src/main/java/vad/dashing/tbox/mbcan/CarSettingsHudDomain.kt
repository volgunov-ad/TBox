package vad.dashing.tbox.mbcan

/** Normalized HUD and overspeed values shared by the Android 9 and Android 10 backends. */
object CarSettingsHudDomain {
    const val HUD_LEVEL_MIN = 1
    const val HUD_LEVEL_MAX = 10
    const val HUD_MODE_STANDARD = 1
    const val HUD_MODE_SNOW = 2

    /** Stock CarSet5 formula: raw = (km/h - 30) / 5. */
    const val OVERSPEED_MIN_KMH = 30
    const val OVERSPEED_STEP_KMH = 5
    val OVERSPEED_RAW_RANGE: IntRange = 0..40

    fun decodeOverspeedKmh(raw: Int): Int? =
        raw.takeIf { it in OVERSPEED_RAW_RANGE }?.let { it * OVERSPEED_STEP_KMH + OVERSPEED_MIN_KMH }

    fun encodeOverspeedKmh(kmh: Int): Int? {
        if (kmh < OVERSPEED_MIN_KMH || (kmh - OVERSPEED_MIN_KMH) % OVERSPEED_STEP_KMH != 0) return null
        return ((kmh - OVERSPEED_MIN_KMH) / OVERSPEED_STEP_KMH).takeIf { it in OVERSPEED_RAW_RANGE }
    }
}
