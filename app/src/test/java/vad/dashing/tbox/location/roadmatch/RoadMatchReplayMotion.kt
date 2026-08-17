package vad.dashing.tbox.location.roadmatch

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Open-loop DR step for field geo-debug replay.
 *
 * Journal `integ.dDistM` is raw CAN path (no [drive.speedScale]);
 * `integ.dYawDebDeg` is debiased gyro without L/R yaw scale — both scales
 * are applied here so replay can override calib independently of the log.
 */
object RoadMatchReplayMotion {
    enum class Mode {
        /** Lat/lon/bearing deltas from the logged preMatch/mock path. */
        DELTA,

        /** Field hybrid heading minus that tick's match yaw; path from integ. */
        STRIP,

        /**
         * Re-integrate from tick aggregates: path = dDistM × speedScale,
         * yaw = dYawDebDeg × yawScale × yawSign. Falls back to [STRIP] when
         * dYawDebDeg is missing (old journals).
         */
        DR,
    }

    fun parseMode(raw: String?): Mode = when (raw?.trim()?.lowercase()) {
        null, "", "0", "off", "delta" -> Mode.DELTA
        "1", "strip" -> Mode.STRIP
        "gyro", "dr" -> Mode.DR
        else -> Mode.DELTA
    }

    data class Calib(
        val yawScale: Float,
        val yawSign: Float,
        val speedScale: Float,
    )

    fun resolveCalib(
        loggedYawScale: Float?,
        loggedYawSign: Float,
        loggedSpeedScale: Float?,
        overrideYawScale: Float?,
        overrideYawSign: Float?,
        overrideSpeedScale: Float?,
    ): Calib = Calib(
        yawScale = overrideYawScale ?: loggedYawScale ?: 1f,
        yawSign = overrideYawSign ?: loggedYawSign,
        speedScale = overrideSpeedScale ?: loggedSpeedScale ?: 1f,
    )

    fun step(
        mode: Mode,
        from: RoadMatchPose,
        dDistM: Double?,
        dYawDebDeg: Float?,
        calib: Calib,
        loggedYawDeltaDeg: Float,
        loggedBearingDeltaDeg: Float,
        fallbackPathM: Double,
    ): RoadMatchPose {
        val pathM = when (mode) {
            Mode.DELTA -> fallbackPathM
            // Strip keeps historical path = raw integ.dDistM (no speedScale).
            Mode.STRIP -> dDistM ?: fallbackPathM
            // DR re-applies drive.speedScale (integ path is raw CAN).
            Mode.DR -> (dDistM ?: fallbackPathM) * calib.speedScale.toDouble()
        }
        val drYaw = when (mode) {
            Mode.DELTA -> loggedYawDeltaDeg
            Mode.STRIP -> loggedYawDeltaDeg - loggedBearingDeltaDeg
            Mode.DR -> {
                if (dYawDebDeg != null) {
                    dYawDebDeg * calib.yawScale * calib.yawSign
                } else {
                    // Old journals without integ yaw: same as strip.
                    loggedYawDeltaDeg - loggedBearingDeltaDeg
                }
            }
        }
        val heading = normalizeDeg(from.bearingDeg + drYaw)
        if (mode == Mode.DELTA) {
            // Caller applies lat/lon delta separately for DELTA; heading-only here
            // is unused. Kept for symmetry in tests.
            return from.copy(bearingDeg = heading)
        }
        if (pathM < 0.05) {
            return from.copy(bearingDeg = heading)
        }
        val dest = destination(from.lat, from.lon, heading, pathM)
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

    fun normalizeDeg(deg: Float): Float {
        var x = deg % 360f
        if (x < 0f) x += 360f
        return x
    }

    fun signedAngleDelta(fromDeg: Float, toDeg: Float): Float {
        var d = toDeg - fromDeg
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }
}
