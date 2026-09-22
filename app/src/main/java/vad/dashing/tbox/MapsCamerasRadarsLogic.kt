package vad.dashing.tbox

import vad.dashing.tbox.location.ConstantDrMath
import kotlin.math.abs

/** Default road/camera lookahead horizon (m). */
const val DEFAULT_MAPS_CAM_LOOKAHEAD_M = 1000
const val MIN_MAPS_CAM_LOOKAHEAD_M = 300
const val MAX_MAPS_CAM_LOOKAHEAD_M = 3000
const val MAPS_CAM_DISTANCE_STEP_M = 10

/** How long a passed radar/camera limit stays as current-limit fallback (m). */
const val DEFAULT_MAPS_CAM_RADAR_HOLD_M = 500
const val MIN_MAPS_CAM_RADAR_HOLD_M = 50
const val MAX_MAPS_CAM_RADAR_HOLD_M = 3000

/** Within this along-path delta, prefer the stricter (lower) ahead limit. */
const val AHEAD_CLOSE_DISTANCE_M = 50.0

/** Remember a radar/camera limit when we get this close (m). */
const val RADAR_PASS_CAPTURE_M = 100.0

fun normalizeMapsCamLookaheadM(raw: Int): Int =
    snapMapsCamDistanceM(raw, MIN_MAPS_CAM_LOOKAHEAD_M, MAX_MAPS_CAM_LOOKAHEAD_M)

fun normalizeMapsCamRadarHoldM(raw: Int): Int =
    snapMapsCamDistanceM(raw, MIN_MAPS_CAM_RADAR_HOLD_M, MAX_MAPS_CAM_RADAR_HOLD_M)

/** Round to [MAPS_CAM_DISTANCE_STEP_M] and clamp. */
fun snapMapsCamDistanceM(raw: Int, minM: Int, maxM: Int): Int {
    val clamped = raw.coerceIn(minM, maxM)
    val step = MAPS_CAM_DISTANCE_STEP_M
    val snapped = ((clamped + step / 2) / step) * step
    return snapped.coerceIn(minM, maxM)
}

/**
 * Pure helpers for the unified maps/cameras/radars speed-limit tile.
 */
object MapsCamerasRadarsLogic {
    data class AheadCandidate(
        val limitKmh: Int,
        val distanceM: Double,
    )

    data class AheadPick(
        val limitKmh: Int?,
        val distanceM: Double?,
    ) {
        companion object {
            val EMPTY = AheadPick(null, null)
        }
    }

    /**
     * Choose ahead limit: nearer wins; if distances differ by ≤ [AHEAD_CLOSE_DISTANCE_M],
     * pick the stricter (lower) limit.
     */
    fun pickAhead(
        osm: AheadCandidate?,
        camera: AheadCandidate?,
        closeDistanceM: Double = AHEAD_CLOSE_DISTANCE_M,
    ): AheadPick {
        val a = osm?.takeIf { it.limitKmh > 0 && it.distanceM.isFinite() && it.distanceM >= 0.0 }
        val b = camera?.takeIf { it.limitKmh > 0 && it.distanceM.isFinite() && it.distanceM >= 0.0 }
        if (a == null && b == null) return AheadPick.EMPTY
        if (a == null) return AheadPick(b!!.limitKmh, b.distanceM)
        if (b == null) return AheadPick(a.limitKmh, a.distanceM)
        val delta = abs(a.distanceM - b.distanceM)
        return if (delta <= closeDistanceM) {
            if (a.limitKmh <= b.limitKmh) {
                AheadPick(a.limitKmh, a.distanceM)
            } else {
                AheadPick(b.limitKmh, b.distanceM)
            }
        } else if (a.distanceM <= b.distanceM) {
            AheadPick(a.limitKmh, a.distanceM)
        } else {
            AheadPick(b.limitKmh, b.distanceM)
        }
    }

    /**
     * Current limit: OSM on matched edge, else last radar/camera with a limit while still
     * within [holdDistanceM] of that point.
     */
    fun resolveCurrentLimitKmh(
        osmCurrentKmh: Int?,
        lastRadar: LastRadarLimit?,
        vehicleLat: Double,
        vehicleLon: Double,
        holdDistanceM: Int,
    ): Int? {
        val osm = osmCurrentKmh?.takeIf { it > 0 }
        if (osm != null) return osm
        val radar = lastRadar ?: return null
        if (radar.speedKmh <= 0) return null
        if (!vehicleLat.isFinite() || !vehicleLon.isFinite()) return null
        val dist = ConstantDrMath.distanceMeters(vehicleLat, vehicleLon, radar.lat, radar.lon)
        if (!dist.isFinite() || dist > holdDistanceM.toDouble()) return null
        return radar.speedKmh
    }

    /**
     * When a camera/radar with a speed limit is within [RADAR_PASS_CAPTURE_M], remember it.
     * Clear when the vehicle has moved farther than [holdDistanceM] from the stored point.
     */
    fun updateLastRadar(
        previous: LastRadarLimit?,
        alertLat: Double?,
        alertLon: Double?,
        alertSpeedKmh: Int?,
        alertDistanceM: Double?,
        vehicleLat: Double,
        vehicleLon: Double,
        holdDistanceM: Int,
        captureDistanceM: Double = RADAR_PASS_CAPTURE_M,
    ): LastRadarLimit? {
        var next = previous
        if (alertLat != null && alertLon != null &&
            alertSpeedKmh != null && alertSpeedKmh > 0 &&
            alertDistanceM != null && alertDistanceM.isFinite() &&
            alertDistanceM <= captureDistanceM
        ) {
            next = LastRadarLimit(
                lat = alertLat,
                lon = alertLon,
                speedKmh = alertSpeedKmh,
            )
        }
        if (next == null) return null
        if (!vehicleLat.isFinite() || !vehicleLon.isFinite()) return next
        val dist = ConstantDrMath.distanceMeters(vehicleLat, vehicleLon, next.lat, next.lon)
        if (!dist.isFinite() || dist > holdDistanceM.toDouble()) return null
        return next
    }
}

data class LastRadarLimit(
    val lat: Double,
    val lon: Double,
    val speedKmh: Int,
)
