package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import vad.dashing.tbox.location.roadmatch.MapKitRuntime
import vad.dashing.tbox.location.roadmatch.RoadMatchBasemapOpacity
import vad.dashing.tbox.location.roadmatch.RoadMatchCanvasViewport
import kotlin.math.abs
import kotlin.math.cos

/**
 * Optional Yandex MapKit basemap under the F2a Canvas overlays.
 * Non-interactive: pan/zoom/seed stay on the Compose Canvas layer.
 *
 * Camera updates are throttled: Compose follow lerps every frame, but MapKit
 * only moves when the geographic change is material (avoids tile thrash / disk).
 */
@Composable
fun RoadMatchMapKitBasemap(
    viewport: RoadMatchCanvasViewport,
    viewHeightPx: Int,
    transparencyPercent: Int,
    userMapkitApiKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewAlpha = RoadMatchBasemapOpacity.viewAlpha(transparencyPercent)
    val ready = remember(userMapkitApiKey) {
        MapKitRuntime.ensureInitialized(context, userMapkitApiKey)
    }
    if (!ready) return

    var mapView by remember { mutableStateOf<MapView?>(null) }
    val cameraGate = remember { MapKitCameraGate() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    MapKitFactory.getInstance().onStart()
                    mapView?.onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView?.onStop()
                    MapKitFactory.getInstance().onStop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            MapKitFactory.getInstance().onStart()
            mapView?.onStart()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onStop()
            MapKitFactory.getInstance().onStop()
            cameraGate.reset()
        }
    }

    AndroidView(
        modifier = modifier.alpha(viewAlpha),
        factory = { ctx ->
            MapView(ctx).also { view ->
                view.map.isScrollGesturesEnabled = false
                view.map.isZoomGesturesEnabled = false
                view.map.isRotateGesturesEnabled = false
                view.map.isTiltGesturesEnabled = false
                mapView = view
                cameraGate.reset()
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    MapKitFactory.getInstance().onStart()
                    view.onStart()
                }
            }
        },
        update = { view ->
            view.alpha = viewAlpha
            val zoom = MapKitRuntime.zoomForHalfHeightM(
                halfHeightM = viewport.halfHeightM,
                lat = viewport.centerLat,
                viewHeightPx = viewHeightPx,
            )
            val lat = viewport.centerLat
            val lon = viewport.centerLon
            val azimuth = viewport.rotationDeg
            if (cameraGate.shouldApply(lat, lon, zoom, azimuth, nowMs = System.currentTimeMillis())) {
                // Instant move: throttling already limits rate; avoid animation queue buildup.
                view.map.move(
                    CameraPosition(Point(lat, lon), zoom, azimuth, 0f),
                    Animation(Animation.Type.LINEAR, 0f),
                    null,
                )
            }
        },
    )
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

    fun shouldApply(
        lat: Double,
        lon: Double,
        zoom: Float,
        azimuth: Float,
        nowMs: Long,
    ): Boolean {
        if (!lat.isFinite() || !lon.isFinite() || !zoom.isFinite() || !azimuth.isFinite()) {
            return false
        }
        if (!lastLat.isFinite()) {
            commit(lat, lon, zoom, azimuth, nowMs)
            return true
        }
        val elapsed = nowMs - lastAppliedAtMs
        if (elapsed < MIN_INTERVAL_MS) {
            // Still allow a large jump (seed / hard pan) before the interval elapses.
            if (!isLargeJump(lat, lon, zoom, azimuth)) return false
        }
        val meters = approxDistanceM(lastLat, lastLon, lat, lon)
        val zoomDelta = abs(zoom - lastZoom)
        val azDelta = abs(shortestAzimuthDelta(lastAzimuth, azimuth))
        if (meters < MIN_MOVE_M && zoomDelta < MIN_ZOOM_DELTA && azDelta < MIN_AZIMUTH_DEG) {
            return false
        }
        commit(lat, lon, zoom, azimuth, nowMs)
        return true
    }

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
        const val MIN_INTERVAL_MS = 250L
        const val MIN_MOVE_M = 8.0
        const val MIN_ZOOM_DELTA = 0.08f
        const val MIN_AZIMUTH_DEG = 2.5f
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
