package vad.dashing.tbox.speedcam

import vad.dashing.tbox.location.roadmatch.RoadMatchLeashMath
import vad.dashing.tbox.location.roadmatch.RoadMapMatcher

/**
 * Geometric coverage beams for SpeedCam map markers (direction + area of action).
 *
 * Not road-snapped: a short trapezoid along [directionDeg] (and opposite for dirType=2).
 * Closest visual match to typical SCO / Yandex overlay beams without a per-cam road walk.
 */
object SpeedCamMapCoverage {
    const val BEAM_LENGTH_M = 110.0
    const val BEAM_HALF_WIDTH_M = 8.5
    const val ALL_DIR_RADIUS_M = 28.0

    data class LatLon(val lat: Double, val lon: Double)

    enum class BeamKind {
        /** Single monitored direction (dirType 1) or primary of both-ways. */
        PRIMARY,
        /** Opposite direction for dirType 2. */
        OPPOSITE,
        /** Omnidirectional (dirType 0): filled disk. */
        ALL,
    }

    data class Beam(
        val kind: BeamKind,
        /** Trapezoid corners (4) or circle center as single point when [kind] == ALL. */
        val points: List<LatLon>,
        val radiusM: Double = 0.0,
    )

    fun beamsFor(
        lat: Double,
        lon: Double,
        dirType: Int,
        directionDeg: Int,
        lengthM: Double = BEAM_LENGTH_M,
        halfWidthM: Double = BEAM_HALF_WIDTH_M,
    ): List<Beam> {
        if (!lat.isFinite() || !lon.isFinite()) return emptyList()
        return when (dirType) {
            1 -> listOf(
                trapezoidBeam(lat, lon, directionDeg.toFloat(), lengthM, halfWidthM, BeamKind.PRIMARY),
            )
            2 -> listOf(
                trapezoidBeam(lat, lon, directionDeg.toFloat(), lengthM, halfWidthM, BeamKind.PRIMARY),
                trapezoidBeam(
                    lat,
                    lon,
                    RoadMapMatcher.normalizeDeg(directionDeg + 180f),
                    lengthM,
                    halfWidthM,
                    BeamKind.OPPOSITE,
                ),
            )
            else -> listOf(
                Beam(
                    kind = BeamKind.ALL,
                    points = listOf(LatLon(lat, lon)),
                    radiusM = ALL_DIR_RADIUS_M,
                ),
            )
        }
    }

    private fun trapezoidBeam(
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        lengthM: Double,
        halfWidthM: Double,
        kind: BeamKind,
    ): Beam {
        val leftBearing = RoadMapMatcher.normalizeDeg(bearingDeg - 90f)
        val rightBearing = RoadMapMatcher.normalizeDeg(bearingDeg + 90f)
        val tipHalf = halfWidthM * 0.55
        val baseHalf = halfWidthM
        val tip = RoadMatchLeashMath.destination(lat, lon, bearingDeg, lengthM)
        val nearL = RoadMatchLeashMath.destination(lat, lon, leftBearing, tipHalf)
        val nearR = RoadMatchLeashMath.destination(lat, lon, rightBearing, tipHalf)
        val farL = RoadMatchLeashMath.destination(tip.first, tip.second, leftBearing, baseHalf)
        val farR = RoadMatchLeashMath.destination(tip.first, tip.second, rightBearing, baseHalf)
        return Beam(
            kind = kind,
            points = listOf(
                LatLon(nearL.first, nearL.second),
                LatLon(farL.first, farL.second),
                LatLon(farR.first, farR.second),
                LatLon(nearR.first, nearR.second),
            ),
        )
    }
}
