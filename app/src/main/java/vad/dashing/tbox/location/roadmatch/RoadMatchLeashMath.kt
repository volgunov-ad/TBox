package vad.dashing.tbox.location.roadmatch

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lateral leash + junction free-DR particle.
 *
 * On a normal road the matched pose is still snapped. When sensors say we are
 * leaving, lateral snap fades and the leash can break (courtyard). At a complex
 * junction a second pose follows instruments only; after the manoeuvre it is
 * promoted if it disagrees strongly in position (heading-only is not enough —
 * that is gyro undershoot, which catch-up must still fix).
 */
object RoadMatchLeashMath {
    const val BREAK_XT_M = 18.0
    /**
     * Yard / residential: 15 m beside the street is the neighbour, not a
     * tight-curve chord (`145417` 14 m stays on ordinary roads).
     */
    const val BREAK_XT_YARD_M = 15.0
    const val BREAK_PATH_M = 8.0
    const val XT_GROW_EPS_M = 0.5

    const val JUNCTION_MIN_ROADS = 3
    const val JUNCTION_RADIUS_M = 100.0
    const val HEADING_CLUSTER_SEP_DEG = 25f
    const val JUNCTION_MIN_PATH_M = 18.0

    /** Position-only promote: clearly off the snapped road. */
    const val PROMOTE_POS_M = 15.0
    /** Smaller gap is enough when heading also disagrees hard. */
    const val PROMOTE_POS_WITH_HEADING_M = 8.0
    const val PROMOTE_HEADING_DEG = 30f

    const val SETTLE_YAW_DEG = 5f
    const val SETTLE_RESIDUAL_DEG = 14f

    /** Yaw must be a real turn, not a 2° residual wiggle (inhibit heading still uses 1.5°). */
    const val STRETCH_SENSOR_YAW_DEG = 8f
    /**
     * Residual-only stretch needs some already-visible leave. Gyro undershoot
     * grows heading while cross-track stays small (`124442`); do not drop snap then.
     */
    const val STRETCH_XT_M = 4.0

    /** One-tick teleport is a reject, not a courtyard leave. */
    const val MAX_LEAVE_STEP_M = 40.0

    fun shouldStretch(
        leavingSameEdge: Boolean,
        sensorsOppose: Boolean,
        drYawAbs: Float = 0f,
        crossTrackM: Double = 0.0,
        dueTurn: Boolean = false,
    ): Boolean {
        if (sensorsOppose && drYawAbs >= STRETCH_SENSOR_YAW_DEG) return true
        // Residual + xt without a turn is gyro undershoot walking off the
        // snapped road (`124442`). Courtyard leave always turns.
        return dueTurn && leavingSameEdge && crossTrackM >= STRETCH_XT_M
    }

    fun xtGrowing(previousXt: Double?, currentXt: Double): Boolean {
        if (previousXt == null || !previousXt.isFinite() || !currentXt.isFinite()) return false
        return currentXt > previousXt + XT_GROW_EPS_M
    }

    fun shouldBreakLeash(
        crossTrackM: Double,
        leavingPathM: Double,
        xtGrowing: Boolean,
        turning: Boolean = false,
        courtyardLike: Boolean = false,
    ): Boolean {
        // Mid-circle xt is to the old chord, not a courtyard leave (`151302`).
        // On a yard street the same xt is the parallel neighbour — allow a break
        // even while [turning] so we do not keep the wrong residential.
        if (turning && !courtyardLike) return false
        if (!crossTrackM.isFinite() || !leavingPathM.isFinite()) return false
        val xtLimit = if (courtyardLike) BREAK_XT_YARD_M else BREAK_XT_M
        if (crossTrackM < xtLimit) return false
        if (leavingPathM >= BREAK_PATH_M) return true
        return xtGrowing && leavingPathM >= BREAK_PATH_M * 0.5
    }

    /** How many distinct travel headings are in [azimuths] (wrap-aware). */
    fun headingClusters(
        azimuths: List<Float>,
        sepDeg: Float = HEADING_CLUSTER_SEP_DEG,
    ): Int {
        val clean = azimuths.filter { it.isFinite() }
        if (clean.isEmpty()) return 0
        val sorted = clean.map { RoadMapMatcher.normalizeDeg(it) }.sorted()
        var clusters = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] - sorted[i - 1] >= sepDeg) clusters++
        }
        val wrap = (sorted.first() + 360f) - sorted.last()
        if (wrap < sepDeg && clusters > 1) clusters--
        return clusters
    }

    fun isComplexJunction(outgoing: Int, nearbyClusters: Int): Boolean =
        outgoing >= JUNCTION_MIN_ROADS || nearbyClusters >= JUNCTION_MIN_ROADS

    fun maneuverSettled(
        drYawAbs: Float,
        residualDeg: Float?,
        dueTurn: Boolean,
        stretching: Boolean,
    ): Boolean {
        if (dueTurn || stretching) return false
        if (drYawAbs >= SETTLE_YAW_DEG) return false
        if (residualDeg != null && residualDeg >= SETTLE_RESIDUAL_DEG) return false
        return true
    }

    fun shouldPromoteFree(posDistM: Double, headingDeltaDeg: Float): Boolean {
        if (!posDistM.isFinite() || !headingDeltaDeg.isFinite()) return false
        if (posDistM >= PROMOTE_POS_M) return true
        return posDistM >= PROMOTE_POS_WITH_HEADING_M &&
            headingDeltaDeg >= PROMOTE_HEADING_DEG
    }

    /**
     * Apply the same path length and heading change that took [fromOutput] → [toInput]
     * onto [free] (instrument-only particle).
     */
    fun stepFreePose(
        free: RoadMatchPose,
        fromOutput: RoadMatchPose,
        toInput: RoadMatchPose,
    ): RoadMatchPose {
        val dist = RoadGraph.haversineM(fromOutput.lat, fromOutput.lon, toInput.lat, toInput.lon)
        val yaw = RoadMapMatcher.signedAngleDeg(fromOutput.bearingDeg, toInput.bearingDeg)
        val heading = RoadMapMatcher.normalizeDeg(free.bearingDeg + yaw)
        if (dist < 0.05) {
            return free.copy(bearingDeg = heading)
        }
        val dest = destination(free.lat, free.lon, heading, dist)
        return RoadMatchPose(dest.first, dest.second, heading)
    }

    fun destination(
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        distM: Double,
    ): Pair<Double, Double> {
        if (distM < 1e-6) return lat to lon
        val br = Math.toRadians(bearingDeg.toDouble())
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val ang = distM / 6_371_000.0
        val lat2 = asin(sin(lat1) * cos(ang) + cos(lat1) * sin(ang) * cos(br))
        val lon2 = lon1 + atan2(
            sin(br) * sin(ang) * cos(lat1),
            cos(ang) - sin(lat1) * sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }
}
