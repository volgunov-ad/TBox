package vad.dashing.tbox.trip

import kotlin.math.roundToInt

/** Trip odometer display and storage precision (0.1 km). */
object TripDistanceFormat {
    const val DECIMALS = 1
    private const val SCALE = 10f

    fun roundKm(km: Float): Float {
        if (!km.isFinite()) return 0f
        return (km * SCALE).roundToInt() / SCALE
    }

    fun addKm(currentKm: Float, deltaKm: Float): Float = roundKm(currentKm + deltaKm)
}
