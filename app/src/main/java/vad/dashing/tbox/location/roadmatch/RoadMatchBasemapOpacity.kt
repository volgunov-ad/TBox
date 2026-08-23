package vad.dashing.tbox.location.roadmatch

/** Basemap transparency presets for the road-match MapKit layer (F2b). */
object RoadMatchBasemapOpacity {
    /** Transparency percent: 0 = opaque basemap; 75 = most transparent. */
    val STEPS: List<Int> = listOf(0, 15, 30, 45, 60, 75)

    fun normalize(transparencyPercent: Int): Int {
        if (STEPS.isEmpty()) return 0
        return STEPS.minByOrNull { kotlin.math.abs(it - transparencyPercent) } ?: 0
    }

    /** Compose / View alpha for MapKit layer (inverse of transparency). */
    fun viewAlpha(transparencyPercent: Int): Float {
        val t = normalize(transparencyPercent).coerceIn(0, 75)
        return (1f - t / 100f).coerceIn(0.25f, 1f)
    }
}
