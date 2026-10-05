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

/**
 * Optional Yandex MapKit basemap under the F2a Canvas overlays.
 * Non-interactive: pan/zoom/seed stay on the Compose Canvas layer.
 *
 * Camera updates are throttled: Compose follow lerps every frame, but MapKit
 * only moves when the geographic change is material (avoids tile thrash / disk).
 * Follow steps use a short SMOOTH animation so the basemap glides between gates.
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
            val decision = cameraGate.decide(
                lat = lat,
                lon = lon,
                zoom = zoom,
                azimuth = azimuth,
                nowMs = System.currentTimeMillis(),
            )
            if (decision != MapKitCameraDecision.SKIP) {
                val animation = when (decision) {
                    MapKitCameraDecision.SMOOTH ->
                        Animation(Animation.Type.SMOOTH, MapKitCameraGate.SMOOTH_DURATION_SEC)
                    MapKitCameraDecision.INSTANT -> null
                    MapKitCameraDecision.SKIP -> null
                }
                if (animation != null) {
                    view.map.move(
                        CameraPosition(Point(lat, lon), zoom, azimuth, 0f),
                        animation,
                        null,
                    )
                } else {
                    view.map.move(CameraPosition(Point(lat, lon), zoom, azimuth, 0f))
                }
            }
        },
    )
}
