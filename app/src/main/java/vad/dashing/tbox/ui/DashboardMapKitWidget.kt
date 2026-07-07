package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.MAX_MAP_KIT_ZOOM
import vad.dashing.tbox.MIN_MAP_KIT_ZOOM
import vad.dashing.tbox.mapkit.rememberSystemLocationState
import vad.dashing.tbox.normalizeMapKitZoom

private val DefaultMapCenter = Point(55.751225, 37.62954)

@Composable
fun DashboardMapKitWidgetItem(
    widget: DashboardWidget,
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
    val context = LocalContext.current
    val systemLocation = rememberSystemLocationState()
    val defaultTitle = stringResource(R.string.data_title_map_kit_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val gesturesEnabled = !isEditMode && enableInnerInteractions
    val hasFix = systemLocation.hasFix &&
        systemLocation.hasPermission &&
        (systemLocation.latitude != 0.0 || systemLocation.longitude != 0.0)
    val zoom = normalizeMapKitZoom(mapZoom)
    var followCenterActive by remember { mutableStateOf(false) }

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
                layoutWeight = 0.1f,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (showTitle) 0.9f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    BuildConfig.MAPKIT_API_KEY.isBlank() -> {
                        Text(
                            text = stringResource(R.string.map_kit_widget_missing_key),
                            color = resolvedTextColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    !systemLocation.hasPermission -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.map_kit_widget_no_location_permission),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .combinedClickableWithSound(
                                        enabled = gesturesEnabled,
                                        onClick = { openAppLocationSettings(context) },
                                        onLongClick = onLongClick,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.widget_music_open_access_settings),
                                    color = resolvedTextColor,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                    else -> {
                        MapKitTileView(
                            latitude = systemLocation.latitude,
                            longitude = systemLocation.longitude,
                            bearing = systemLocation.bearing,
                            hasFix = hasFix,
                            zoom = zoom,
                            followCenterActive = followCenterActive,
                            onUserMapGesture = { followCenterActive = false },
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
                            MapKitFollowCenterButton(
                                active = followCenterActive,
                                enabled = hasFix,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(2.dp),
                                onFollowActiveChange = { followCenterActive = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapKitFollowCenterButton(
    active: Boolean,
    enabled: Boolean,
    onFollowActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val followCenterCd = stringResource(R.string.map_kit_widget_follow_center_cd)
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val idleColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    Box(
        modifier = modifier
            .semantics { contentDescription = followCenterCd }
            .size(36.dp)
            .background(
                color = if (active) activeColor else idleColor,
                shape = CircleShape,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onFollowActiveChange(true)
                        try {
                            awaitRelease()
                        } finally {
                            onFollowActiveChange(false)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "◎",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
    bearing: Float,
    hasFix: Boolean,
    zoom: Float,
    followCenterActive: Boolean,
    onUserMapGesture: () -> Unit,
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
    val cameraListenerHolder = remember { CameraListenerHolder() }

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

    DisposableEffect(mapView, onUserMapGesture) {
        val listener = CameraListener { _, _, reason, _ ->
            if (reason == CameraUpdateReason.GESTURES) {
                onUserMapGesture()
            }
        }
        cameraListenerHolder.listener = listener
        mapView.mapWindow.map.addCameraListener(listener)
        onDispose {
            mapView.mapWindow.map.removeCameraListener(listener)
            cameraListenerHolder.listener = null
        }
    }

    LaunchedEffect(latitude, longitude, bearing, hasFix) {
        val target = if (hasFix) Point(latitude, longitude) else DefaultMapCenter
        placemarkHolder.placemark?.let { placemark ->
            placemark.geometry = target
            placemark.direction = bearing
            placemark.isVisible = hasFix
        }
    }

    LaunchedEffect(latitude, longitude, hasFix, zoom, followCenterActive) {
        if (!followCenterActive || !hasFix) return@LaunchedEffect
        val target = Point(latitude, longitude)
        mapView.mapWindow.map.move(
            CameraPosition(target, zoom, 0f, 0f),
        )
    }

    LaunchedEffect(zoom, followCenterActive, hasFix, latitude, longitude) {
        if (followCenterActive && hasFix) return@LaunchedEffect
        mapView.mapWindow.map.move(
            CameraPosition(
                mapView.mapWindow.map.cameraPosition.target,
                zoom,
                mapView.mapWindow.map.cameraPosition.azimuth,
                mapView.mapWindow.map.cameraPosition.tilt,
            ),
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

private fun openAppLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }
}

private class PlacemarkHolder {
    var placemark: PlacemarkMapObject? = null
}

private class CameraListenerHolder {
    var listener: CameraListener? = null
}
