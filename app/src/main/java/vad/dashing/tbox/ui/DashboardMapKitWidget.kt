package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.MAX_MAP_KIT_ZOOM
import vad.dashing.tbox.MIN_MAP_KIT_ZOOM
import vad.dashing.tbox.normalizeMapKitZoom

private val DefaultMapCenter = Point(55.751225, 37.62954)

@Composable
fun DashboardMapKitWidgetItem(
    widget: DashboardWidget,
    viewModel: TboxViewModel,
    mapZoom: Float,
    onMapZoomChange: (Float) -> Unit,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    showTitle: Boolean = false,
    titleOverride: String = "",
    isEditMode: Boolean = false,
    enableInnerInteractions: Boolean = true,
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_map_kit_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val gesturesEnabled = !isEditMode && enableInnerInteractions
    val hasFix = locValues.locateStatus &&
        (locValues.latitude != 0.0 || locValues.longitude != 0.0)
    val zoom = normalizeMapKitZoom(mapZoom)

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        ) {
            DashboardWidgetTitleRowIfVisible(
                showTitle = showTitle,
                titleText = titleText,
                availableHeight = availableHeight,
                resolvedTextColor = resolvedTextColor,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
                    Text(
                        text = stringResource(R.string.map_kit_widget_missing_key),
                        color = resolvedTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    MapKitTileView(
                        latitude = locValues.latitude,
                        longitude = locValues.longitude,
                        trueDirection = locValues.trueDirection,
                        hasFix = hasFix,
                        zoom = zoom,
                        gesturesEnabled = gesturesEnabled,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (gesturesEnabled) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp),
                        ) {
                            MapKitZoomButton(
                                enabled = zoom > MIN_MAP_KIT_ZOOM,
                                onClick = { onMapZoomChange(normalizeMapKitZoom(zoom - 1f)) },
                            ) {
                                Text(
                                    text = "−",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            MapKitZoomButton(
                                enabled = zoom < MAX_MAP_KIT_ZOOM,
                                onClick = { onMapZoomChange(normalizeMapKitZoom(zoom + 1f)) },
                            ) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapKitZoomButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(32.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = CircleShape,
            ),
    ) {
        content()
    }
}

@Composable
private fun MapKitTileView(
    latitude: Double,
    longitude: Double,
    trueDirection: Float,
    hasFix: Boolean,
    zoom: Float,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember(context) {
        MapView(context).apply {
            mapWindow.map.move(
                CameraPosition(DefaultMapCenter, zoom, 0f, 0f),
            )
        }
    }
    val placemarkHolder = remember { PlacemarkHolder() }

    DisposableEffect(mapView) {
        val mapObjects = mapView.mapWindow.map.mapObjects
        placemarkHolder.placemark = mapObjects.addPlacemark(DefaultMapCenter).apply {
            setIcon(ImageProvider.fromResource(context, R.drawable.loc_0_ok))
            isVisible = false
        }
        onDispose {
            placemarkHolder.placemark?.let { mapObjects.remove(it) }
            placemarkHolder.placemark = null
        }
    }

    LaunchedEffect(latitude, longitude, trueDirection, hasFix, zoom) {
        val target = if (hasFix) Point(latitude, longitude) else DefaultMapCenter
        placemarkHolder.placemark?.let { placemark ->
            placemark.geometry = target
            placemark.direction = trueDirection
            placemark.isVisible = hasFix
        }
        mapView.mapWindow.map.move(
            CameraPosition(target, zoom, 0f, 0f),
        )
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    MapKitFactory.getInstance().onStart()
                    mapView.onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    MapKitFactory.getInstance().onStop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            MapKitFactory.getInstance().onStart()
            mapView.onStart()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                mapView.onStop()
                MapKitFactory.getInstance().onStop()
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.mapWindow.map.isRotateGesturesEnabled = gesturesEnabled
            view.mapWindow.map.isTiltGesturesEnabled = gesturesEnabled
            view.mapWindow.map.isZoomGesturesEnabled = gesturesEnabled
            view.mapWindow.map.isScrollGesturesEnabled = gesturesEnabled
        },
    )
}

private class PlacemarkHolder {
    var placemark: PlacemarkMapObject? = null
}
