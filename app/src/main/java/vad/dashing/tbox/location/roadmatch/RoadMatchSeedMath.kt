package vad.dashing.tbox.location.roadmatch

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import vad.dashing.tbox.location.ConstantDrMath

/**
 * Pure F3 set-mode math: pan the map under a pinned shadow, pinch-zoom, heading ring.
 */
object RoadMatchSeedMath {
    const val SET_MIN_HALF_SPAN_M = 40.0
    const val SET_MAX_HALF_SPAN_M = 400.0
    /** Rebuild neighbors when the draft center moves this far from the last query. */
    const val NEIGHBOR_REQUERY_M = 15.0

    fun wrapBearingDeg(deg: Float): Float = ConstantDrMath.wrapBearingDeg(deg)

    /**
     * Navigation bearing from a canvas delta relative to center.
     * Screen 0° = up (−Y), clockwise. [rotationDeg] is the geographic heading
     * that currently maps to screen-up (`0` = north-up).
     */
    fun bearingFromCanvasDelta(
        dxPx: Float,
        dyPx: Float,
        rotationDeg: Float = 0f,
    ): Float {
        if (!dxPx.isFinite() || !dyPx.isFinite()) return 0f
        if (dxPx == 0f && dyPx == 0f) return 0f
        val screenDeg = Math.toDegrees(atan2(dxPx.toDouble(), -dyPx.toDouble())).toFloat()
        val rot = rotationDeg.takeIf { it.isFinite() } ?: 0f
        return wrapBearingDeg(screenDeg + rot)
    }

    fun isOnHeadingRing(dxPx: Float, dyPx: Float, innerPx: Float, outerPx: Float): Boolean {
        if (innerPx < 0f || outerPx <= innerPx) return false
        val r = hypot(dxPx.toDouble(), dyPx.toDouble())
        return r in innerPx.toDouble()..outerPx.toDouble()
    }

    fun headingRingRadiusPx(minDimPx: Float): Float =
        (minDimPx * 0.22f).coerceIn(28f, 96f)

    fun headingRingBandPx(minDimPx: Float): Float =
        (minDimPx * 0.08f).coerceIn(16f, 36f)

    /**
     * Canvas offset from ring center to the heading tick on the ring.
     * [bearingDeg] is geographic (0° = north). [rotationDeg] is screen-up heading.
     */
    fun headingRingTickOffset(
        bearingDeg: Float,
        radiusPx: Float,
        rotationDeg: Float = 0f,
    ): Pair<Float, Float> {
        val rot = rotationDeg.takeIf { it.isFinite() } ?: 0f
        val screen = wrapBearingDeg(bearingDeg - rot)
        val a = Math.toRadians(screen.toDouble())
        return (kotlin.math.sin(a) * radiusPx).toFloat() to
            (-kotlin.math.cos(a) * radiusPx).toFloat()
    }

    fun clampSetHalfSpanM(halfHeightM: Double): Double {
        if (!halfHeightM.isFinite() || halfHeightM <= 0.0) return SET_MIN_HALF_SPAN_M
        return halfHeightM.coerceIn(SET_MIN_HALF_SPAN_M, SET_MAX_HALF_SPAN_M)
    }

    /** Pinch-out (zoom > 1) shrinks the span so the map appears closer. */
    fun applyPinchZoom(halfHeightM: Double, zoom: Float): Double {
        if (!zoom.isFinite() || zoom <= 0f) return clampSetHalfSpanM(halfHeightM)
        return clampSetHalfSpanM(halfHeightM / zoom.toDouble())
    }

    /**
     * Finger pan in canvas pixels → east/north metres to move the camera center.
     * The map follows the finger, so the pinned shadow's geopoint moves the opposite way.
     */
    fun panToEastNorthM(
        panXpx: Float,
        panYpx: Float,
        widthPx: Float,
        heightPx: Float,
        halfWidthM: Double,
        halfHeightM: Double,
        rotationDeg: Float = 0f,
    ): Pair<Double, Double> {
        if (widthPx <= 0f || heightPx <= 0f) return 0.0 to 0.0
        val eastR = -panXpx / widthPx * (2.0 * halfWidthM)
        val northR = panYpx / heightPx * (2.0 * halfHeightM)
        val rot = rotationDeg.takeIf { it.isFinite() } ?: 0f
        if (rot == 0f) return eastR to northR
        val rad = Math.toRadians(rot.toDouble())
        val c = cos(rad)
        val s = sin(rad)
        return (eastR * c + northR * s) to (-eastR * s + northR * c)
    }

    fun shiftCenter(
        lat: Double,
        lon: Double,
        eastM: Double,
        northM: Double,
    ): OverlayLatLon {
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.1)
        val metres = RoadMatchCanvasViewport.METRES_PER_DEG
        return OverlayLatLon(
            lat = lat + northM / metres,
            lon = lon + eastM / (metres * cosLat),
        )
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) +
            cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2.0 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}
