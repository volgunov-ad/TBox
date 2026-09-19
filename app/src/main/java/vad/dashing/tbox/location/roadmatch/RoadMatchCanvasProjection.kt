package vad.dashing.tbox.location.roadmatch

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Map-agnostic projection used by the F2a Canvas widget and unit tests. */
data class OverlayCanvasPoint(val x: Float, val y: Float)

data class RoadMatchCanvasViewport(
    val centerLat: Double,
    val centerLon: Double,
    /** Half-width in local east metres (before heading rotation). */
    val halfWidthM: Double,
    /** Half-height in local north metres (before heading rotation). */
    val halfHeightM: Double,
    /**
     * Travel heading that maps to screen-up, degrees clockwise from north.
     * `0` is north-up.
     */
    val rotationDeg: Float = 0f,
) {
    fun project(lat: Double, lon: Double): OverlayCanvasPoint {
        val eastM = (lon - centerLon) * METRES_PER_DEG *
            cos(Math.toRadians(centerLat))
        val northM = (lat - centerLat) * METRES_PER_DEG
        val heading = rotationDeg
        val eastR: Double
        val northR: Double
        if (heading == 0f || !heading.isFinite()) {
            eastR = eastM
            northR = northM
        } else {
            val rad = Math.toRadians(heading.toDouble())
            val c = cos(rad)
            val s = sin(rad)
            eastR = eastM * c - northM * s
            northR = eastM * s + northM * c
        }
        return OverlayCanvasPoint(
            x = ((eastR / halfWidthM + 1.0) * 0.5).toFloat(),
            y = ((1.0 - northR / halfHeightM) * 0.5).toFloat(),
        )
    }

    /**
     * Geographic bearing (0° = north, clockwise) in the rotated canvas:
     * 0° is screen-up after [rotationDeg].
     */
    fun screenBearingDeg(geoBearingDeg: Float): Float {
        if (!geoBearingDeg.isFinite()) return 0f
        return RoadMatchCanvasProjection.wrapHeadingDeg(geoBearingDeg - rotationDeg)
    }

    companion object {
        const val METRES_PER_DEG = 111_320.0
    }
}

object RoadMatchCanvasProjection {
    const val MIN_HALF_SPAN_M = 70.0
    const val MAX_HALF_SPAN_M = 280.0
    /** Below this speed the follow camera stays at [MIN_HALF_SPAN_M]. */
    const val FOLLOW_SPAN_MIN_KMH = 15.0
    /** At and above this speed the follow camera uses [MAX_HALF_SPAN_M]. */
    const val FOLLOW_SPAN_MAX_KMH = 120.0
    /**
     * Heading-up: shift the geographic center ahead so the shadow sits below
     * mid-frame (`y = 0.5 + fraction/2`).
     */
    const val HEADING_UP_AHEAD_FRACTION = 0.22f
    const val FOLLOW_ZOOM_TAU_SEC = 0.40
    const val FOLLOW_HEADING_TAU_SEC = 0.28
    /** Chase-camera lag for geographic follow center (same feel as heading). */
    const val FOLLOW_POS_TAU_SEC = 0.28
    /** Snap follow center instead of gliding across the map (hard resync / teleport). */
    const val FOLLOW_POS_SNAP_M = 40.0

    /**
     * Follow-mode span from vehicle speed. GNSS and nearby edges do not drive zoom.
     */
    fun followHalfSpanM(speedKmh: Double): Double {
        val speed = speedKmh.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val span = FOLLOW_SPAN_MAX_KMH - FOLLOW_SPAN_MIN_KMH
        val t = if (span <= 0.0) {
            0.0
        } else {
            ((speed - FOLLOW_SPAN_MIN_KMH) / span).coerceIn(0.0, 1.0)
        }
        return MIN_HALF_SPAN_M + (MAX_HALF_SPAN_M - MIN_HALF_SPAN_M) * t
    }

    fun lerpSpan(current: Double, target: Double, t: Double): Double {
        val tt = t.coerceIn(0.0, 1.0)
        if (!current.isFinite()) return target
        if (!target.isFinite()) return current
        return current + (target - current) * tt
    }

    fun wrapHeadingDeg(deg: Float): Float {
        if (!deg.isFinite()) return 0f
        var x = deg % 360f
        if (x < 0f) x += 360f
        return x
    }

    fun shortestHeadingDeltaDeg(from: Float, to: Float): Float {
        var d = wrapHeadingDeg(to) - wrapHeadingDeg(from)
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun lerpHeadingDeg(from: Float, to: Float, t: Float): Float {
        val tt = t.coerceIn(0f, 1f)
        return wrapHeadingDeg(from + shortestHeadingDeltaDeg(from, to) * tt)
    }

    /**
     * Linear blend in local east/north metres (avoids longitude stretch near poles).
     */
    fun lerpLatLon(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        t: Double,
    ): OverlayLatLon {
        val tt = t.coerceIn(0.0, 1.0)
        if (!fromLat.isFinite() || !fromLon.isFinite()) {
            return OverlayLatLon(toLat, toLon)
        }
        if (!toLat.isFinite() || !toLon.isFinite() || tt <= 0.0) {
            return OverlayLatLon(fromLat, fromLon)
        }
        if (tt >= 1.0) return OverlayLatLon(toLat, toLon)
        val midLat = (fromLat + toLat) * 0.5
        val cosLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.1)
        val metres = RoadMatchCanvasViewport.METRES_PER_DEG
        val eastM = (toLon - fromLon) * metres * cosLat
        val northM = (toLat - fromLat) * metres
        return RoadMatchSeedMath.shiftCenter(
            lat = fromLat,
            lon = fromLon,
            eastM = eastM * tt,
            northM = northM * tt,
        )
    }

    fun approxDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (!lat1.isFinite() || !lon1.isFinite() || !lat2.isFinite() || !lon2.isFinite()) {
            return Double.POSITIVE_INFINITY
        }
        val midLat = (lat1 + lat2) * 0.5
        val eastM = (lon2 - lon1) * RoadMatchCanvasViewport.METRES_PER_DEG *
            cos(Math.toRadians(midLat))
        val northM = (lat2 - lat1) * RoadMatchCanvasViewport.METRES_PER_DEG
        return kotlin.math.hypot(eastM, northM)
    }

    fun followBlendT(dtSec: Double, tauSec: Double): Float {
        val dt = dtSec.takeIf { it.isFinite() && it > 0.0 } ?: return 0f
        val tau = tauSec.takeIf { it.isFinite() && it > 1e-4 } ?: return 1f
        return (1.0 - exp(-dt / tau)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Follow camera: shadow-centered (or slightly ahead when [aheadFraction] > 0),
     * span from [halfHeightM], optional heading-up rotation.
     *
     * [followLat]/[followLon] override the geographic base (smoothed chase center);
     * when NaN, uses [RoadMatchOverlayState.shadow].
     */
    fun viewport(
        state: RoadMatchOverlayState,
        aspectRatio: Float,
        halfHeightM: Double,
        headingDeg: Float = 0f,
        aheadFraction: Float = 0f,
        followLat: Double = Double.NaN,
        followLon: Double = Double.NaN,
    ): RoadMatchCanvasViewport? {
        if (!state.shadow.visible) return null
        val heading = headingDeg.takeIf { it.isFinite() } ?: 0f
        val ahead = aheadFraction.takeIf { it.isFinite() }?.coerceIn(0f, 0.6f) ?: 0f
        val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0.1f } ?: 1f
        val halfHeight = halfHeightM.takeIf { it.isFinite() && it > 0.0 } ?: MIN_HALF_SPAN_M
        val baseLat = if (followLat.isFinite()) followLat else state.shadow.lat
        val baseLon = if (followLon.isFinite()) followLon else state.shadow.lon
        val center = if (ahead > 0f) {
            val aheadM = halfHeight * ahead.toDouble()
            val rad = Math.toRadians(heading.toDouble())
            RoadMatchSeedMath.shiftCenter(
                lat = baseLat,
                lon = baseLon,
                eastM = aheadM * sin(rad),
                northM = aheadM * cos(rad),
            )
        } else {
            OverlayLatLon(baseLat, baseLon)
        }
        return RoadMatchCanvasViewport(
            centerLat = center.lat,
            centerLon = center.lon,
            halfWidthM = (halfHeight * safeAspect).coerceAtLeast(MIN_HALF_SPAN_M),
            halfHeightM = halfHeight,
            rotationDeg = heading,
        )
    }

    /** Fixed-span viewport for F3 set-mode (user pan / pinch-zoom). */
    fun viewportAt(
        centerLat: Double,
        centerLon: Double,
        halfHeightM: Double,
        aspectRatio: Float,
        rotationDeg: Float = 0f,
    ): RoadMatchCanvasViewport {
        val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0.1f } ?: 1f
        val halfHeight = halfHeightM.takeIf { it.isFinite() && it > 0.0 } ?: MIN_HALF_SPAN_M
        return RoadMatchCanvasViewport(
            centerLat = centerLat,
            centerLon = centerLon,
            halfWidthM = (halfHeight * safeAspect).coerceAtLeast(MIN_HALF_SPAN_M),
            halfHeightM = halfHeight,
            rotationDeg = rotationDeg.takeIf { it.isFinite() } ?: 0f,
        )
    }
}
