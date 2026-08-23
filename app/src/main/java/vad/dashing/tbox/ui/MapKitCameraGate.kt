package vad.dashing.tbox.ui

import kotlin.math.abs
import kotlin.math.cos

/** Result of [MapKitCameraGate.decide]. */
internal enum class MapKitCameraDecision {
    SKIP,
    /** First lock / seed / large teleport — snap without animation. */
    INSTANT,
    /** Normal follow step — short SMOOTH glide. */
    SMOOTH,
}

/**
 * Gates MapKit camera updates so Compose frame-rate follow does not spam tile loads.
 * Visible for unit tests; no MapKit types.
 */
internal class MapKitCameraGate {
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastZoom = Float.NaN
    private var lastAzimuth = Float.NaN
    private var lastAppliedAtMs = 0L

    fun reset() {
        lastLat = Double.NaN
        lastLon = Double.NaN
        lastZoom = Float.NaN
        lastAzimuth = Float.NaN
        lastAppliedAtMs = 0L
    }

    fun decide(
        lat: Double,
        lon: Double,
        zoom: Float,
        azimuth: Float,
        nowMs: Long,
    ): MapKitCameraDecision {
        if (!lat.isFinite() || !lon.isFinite() || !zoom.isFinite() || !azimuth.isFinite()) {
            return MapKitCameraDecision.SKIP
        }
        if (!lastLat.isFinite()) {
            commit(lat, lon, zoom, azimuth, nowMs)
            return MapKitCameraDecision.INSTANT
        }
        val largeJump = isLargeJump(lat, lon, zoom, azimuth)
        val elapsed = nowMs - lastAppliedAtMs
        if (elapsed < MIN_INTERVAL_MS && !largeJump) {
            return MapKitCameraDecision.SKIP
        }
        val meters = approxDistanceM(lastLat, lastLon, lat, lon)
        val zoomDelta = abs(zoom - lastZoom)
        val azDelta = abs(shortestAzimuthDelta(lastAzimuth, azimuth))
        if (!largeJump &&
            meters < MIN_MOVE_M &&
            zoomDelta < MIN_ZOOM_DELTA &&
            azDelta < MIN_AZIMUTH_DEG
        ) {
            return MapKitCameraDecision.SKIP
        }
        commit(lat, lon, zoom, azimuth, nowMs)
        return if (largeJump) MapKitCameraDecision.INSTANT else MapKitCameraDecision.SMOOTH
    }

    /** Compatibility for older tests / callers. */
    fun shouldApply(
        lat: Double,
        lon: Double,
        zoom: Float,
        azimuth: Float,
        nowMs: Long,
    ): Boolean = decide(lat, lon, zoom, azimuth, nowMs) != MapKitCameraDecision.SKIP

    private fun isLargeJump(lat: Double, lon: Double, zoom: Float, azimuth: Float): Boolean {
        val meters = approxDistanceM(lastLat, lastLon, lat, lon)
        val zoomDelta = abs(zoom - lastZoom)
        val azDelta = abs(shortestAzimuthDelta(lastAzimuth, azimuth))
        return meters >= FORCE_MOVE_M || zoomDelta >= FORCE_ZOOM_DELTA || azDelta >= FORCE_AZIMUTH_DEG
    }

    private fun commit(lat: Double, lon: Double, zoom: Float, azimuth: Float, nowMs: Long) {
        lastLat = lat
        lastLon = lon
        lastZoom = zoom
        lastAzimuth = azimuth
        lastAppliedAtMs = nowMs
    }

    companion object {
        /** Max MapKit camera apply rate while Compose lerps every frame. */
        const val MIN_INTERVAL_MS = 280L
        /** Slightly longer than [MIN_INTERVAL_MS] so consecutive SMOOTH moves overlap. */
        const val SMOOTH_DURATION_SEC = 0.40f
        const val MIN_MOVE_M = 6.0
        const val MIN_ZOOM_DELTA = 0.06f
        const val MIN_AZIMUTH_DEG = 2.0f
        /** Bypass interval for seed / large teleports. */
        const val FORCE_MOVE_M = 40.0
        const val FORCE_ZOOM_DELTA = 0.35f
        const val FORCE_AZIMUTH_DEG = 20f

        fun approxDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val midLat = (lat1 + lat2) * 0.5
            val eastM = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians(midLat))
            val northM = (lat2 - lat1) * 111_320.0
            return kotlin.math.hypot(eastM, northM)
        }

        fun shortestAzimuthDelta(from: Float, to: Float): Float {
            var d = to - from
            while (d > 180f) d -= 360f
            while (d < -180f) d += 360f
            return d
        }
    }
}
