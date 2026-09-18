package vad.dashing.tbox.speedcam

import vad.dashing.tbox.location.ConstantDrMath
import vad.dashing.tbox.location.roadmatch.RoadMapMatcher
import java.util.Locale

/**
 * Picks the nearest relevant SpeedCam point ahead of travel within [radiusM].
 */
object SpeedCamLookahead {
    /** Max heading delta to treat camera direction as matching travel. */
    const val DIR_MATCH_DEG = 60f
    /** Point must lie roughly ahead of the vehicle. */
    const val AHEAD_MAX_DEG = 90f

    fun findNearest(
        index: SpeedCamIndex?,
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        radiusM: Int,
        vehicleSpeedKmh: Float,
        overageKmh: Int,
    ): SpeedCamAlert? {
        if (index == null || index.size == 0) return null
        if (!lat.isFinite() || !lon.isFinite() || !bearingDeg.isFinite()) return null
        val radius = normalizeSpeedCamRadiusM(radiusM).toDouble()
        val candidates = index.query(lat, lon, radius)
        var best: SpeedCamPoint? = null
        var bestDist = Double.POSITIVE_INFINITY
        for (p in candidates) {
            val dist = ConstantDrMath.distanceMeters(lat, lon, p.lat, p.lon)
            if (!dist.isFinite() || dist > radius) continue
            val toCam = RoadMapMatcher.bearingBetweenDeg(lat, lon, p.lat, p.lon)
            if (RoadMapMatcher.smallestAngleDeg(bearingDeg, toCam) > AHEAD_MAX_DEG) continue
            if (!directionRelevant(bearingDeg, p)) continue
            if (dist < bestDist) {
                bestDist = dist
                best = p
            }
        }
        val point = best ?: return null
        val relative = relativeDirection(bearingDeg, point.directionDeg)
        val overLimit = point.hasSpeedLimit &&
            vehicleSpeedKmh.isFinite() &&
            vehicleSpeedKmh > point.speedKmh + normalizeSpeedCamOverageKmh(overageKmh)
        return SpeedCamAlert(
            point = point,
            distanceM = bestDist,
            relative = relative,
            overLimit = overLimit,
        )
    }

    fun nearbyMarkers(
        index: SpeedCamIndex?,
        lat: Double,
        lon: Double,
        radiusM: Int,
        alertId: Int?,
    ): List<SpeedCamMapMarker> {
        if (index == null || index.size == 0) return emptyList()
        if (!lat.isFinite() || !lon.isFinite()) return emptyList()
        val radius = normalizeSpeedCamRadiusM(radiusM).toDouble()
        return index.query(lat, lon, radius).mapNotNull { p ->
            val dist = ConstantDrMath.distanceMeters(lat, lon, p.lat, p.lon)
            if (!dist.isFinite() || dist > radius) return@mapNotNull null
            SpeedCamMapMarker(
                lat = p.lat,
                lon = p.lon,
                category = p.category,
                speedKmh = p.speedKmh,
                isAlertTarget = alertId != null && p.id == alertId,
            )
        }
    }

    fun directionRelevant(travelBearingDeg: Float, point: SpeedCamPoint): Boolean {
        return when (point.dirType) {
            0 -> true
            1 -> RoadMapMatcher.smallestAngleDeg(
                travelBearingDeg,
                point.directionDeg.toFloat(),
            ) <= DIR_MATCH_DEG
            2 -> {
                val a = RoadMapMatcher.smallestAngleDeg(
                    travelBearingDeg,
                    point.directionDeg.toFloat(),
                )
                val b = RoadMapMatcher.smallestAngleDeg(
                    travelBearingDeg,
                    RoadMapMatcher.normalizeDeg(point.directionDeg + 180f),
                )
                a <= DIR_MATCH_DEG || b <= DIR_MATCH_DEG
            }
            else -> true
        }
    }

    fun relativeDirection(travelBearingDeg: Float, cameraDirectionDeg: Int): SpeedCamRelativeDirection {
        val delta = RoadMapMatcher.smallestAngleDeg(
            travelBearingDeg,
            cameraDirectionDeg.toFloat(),
        )
        return if (delta <= 90f) SpeedCamRelativeDirection.SAME else SpeedCamRelativeDirection.ONCOMING
    }

    fun formatDistanceM(distanceM: Double): String {
        if (!distanceM.isFinite() || distanceM < 0.0) return "—"
        return if (distanceM >= 1000.0) {
            String.format(Locale.US, "%.1f км", distanceM / 1000.0)
        } else {
            "${distanceM.toInt()} м"
        }
    }

    fun formatDistanceMEn(distanceM: Double): String {
        if (!distanceM.isFinite() || distanceM < 0.0) return "—"
        return if (distanceM >= 1000.0) {
            String.format(Locale.US, "%.1f km", distanceM / 1000.0)
        } else {
            "${distanceM.toInt()} m"
        }
    }
}
