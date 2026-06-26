package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.MainScreenMapWindowConfig
import vad.dashing.tbox.SettingsViewModel
import kotlin.math.roundToInt

private data class MapWindowPxLayout(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private data class MapWindowRelLayout(
    val relX: Float,
    val relY: Float,
    val relWidth: Float,
    val relHeight: Float,
)

private fun mapWindowLayoutFromRel(
    relX: Float,
    relY: Float,
    relW: Float,
    relH: Float,
    containerW: Float,
    containerH: Float,
): MapWindowPxLayout {
    val w = (relW * containerW).coerceIn(1f, containerW)
    val h = (relH * containerH).coerceIn(1f, containerH)
    val rangeX = (containerW - w).coerceAtLeast(0f)
    val rangeY = (containerH - h).coerceAtLeast(0f)
    return MapWindowPxLayout(
        x = (relX * rangeX).coerceIn(0f, rangeX),
        y = (relY * rangeY).coerceIn(0f, rangeY),
        width = w,
        height = h,
    )
}

private fun mapWindowPxToRel(layout: MapWindowPxLayout, containerW: Float, containerH: Float): MapWindowRelLayout {
    val relW = (layout.width / containerW).coerceIn(0.08f, 1f)
    val relH = (layout.height / containerH).coerceIn(0.08f, 1f)
    val w = relW * containerW
    val h = relH * containerH
    val rangeX = (containerW - w).coerceAtLeast(1f)
    val rangeY = (containerH - h).coerceAtLeast(1f)
    return MapWindowRelLayout(
        relX = (layout.x / rangeX).coerceIn(0f, 1f),
        relY = (layout.y / rangeY).coerceIn(0f, 1f),
        relWidth = relW,
        relHeight = relH,
    )
}

private val MapPreviewBorderColor = Color(0xFF29B6F6)
private val MapHandleSizeDp = 44.dp
private val MapEditModeCornerHitDp = 56.dp

@Composable
fun MainScreenMapWindowOverlay(
    config: MainScreenMapWindowConfig,
    containerWidthPx: Float,
    containerHeightPx: Float,
    settingsViewModel: SettingsViewModel,
) {
    if (!config.enabled) return

    val density = LocalDensity.current
    val minPanelPx = with(density) { 80.dp.toPx() }
    val cw = containerWidthPx.coerceAtLeast(1f)
    val ch = containerHeightPx.coerceAtLeast(1f)

    var isEditMode by remember { mutableStateOf(false) }
    var layoutInteraction by remember { mutableStateOf(false) }
    var committedLayout by remember {
        mutableStateOf(
            mapWindowLayoutFromRel(
                config.relX,
                config.relY,
                config.relWidth,
                config.relHeight,
                cw,
                ch,
            )
        )
    }
    var dragPreview by remember { mutableStateOf<MapWindowPxLayout?>(null) }
    val committedLayoutState = rememberUpdatedState(committedLayout)

    LaunchedEffect(config.relX, config.relY, config.relWidth, config.relHeight, cw, ch) {
        if (layoutInteraction) return@LaunchedEffect
        committedLayout = mapWindowLayoutFromRel(config.relX, config.relY, config.relWidth, config.relHeight, cw, ch)
    }

    val displayLayout = dragPreview ?: committedLayout

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            kotlinx.coroutines.delay(300000)
            if (isEditMode) {
                isEditMode = false
                dragPreview = null
            }
        }
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(displayLayout.x.roundToInt(), displayLayout.y.roundToInt())
            }
            .size(
                width = with(density) { displayLayout.width.toDp() },
                height = with(density) { displayLayout.height.toDp() },
            )
            .border(
                width = when {
                    dragPreview != null -> 3.dp
                    isEditMode -> 2.dp
                    else -> 0.dp
                },
                color = when {
                    dragPreview != null -> MapPreviewBorderColor
                    isEditMode -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
            )
            .background(Color.Black.copy(alpha = 0.04f)),
    ) {
        if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
            Text(
                text = LocalContext.current.getString(vad.dashing.tbox.R.string.main_screen_map_window_missing_key),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp),
            )
        } else {
            YandexMapKitView(
                modifier = Modifier.fillMaxSize(),
                gesturesEnabled = !isEditMode,
            )
        }

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                isEditMode = false
                                dragPreview = null
                            },
                        )
                    },
            )
        }

        if (!isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(MapEditModeCornerHitDp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { isEditMode = true },
                        )
                    },
            )
        }

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(MapHandleSizeDp)
                    .background(MapPreviewBorderColor.copy(alpha = 0.35f))
                    .pointerInput(cw, ch, minPanelPx) {
                        detectDragGestures(
                            onDragStart = {
                                layoutInteraction = true
                                val base = committedLayoutState.value
                                dragPreview = base
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val base = committedLayoutState.value
                                val cur = dragPreview ?: base
                                dragPreview = cur.copy(
                                    x = (cur.x + dragAmount.x).coerceIn(
                                        0f,
                                        (cw - cur.width).coerceAtLeast(0f),
                                    ),
                                    y = (cur.y + dragAmount.y).coerceIn(
                                        0f,
                                        (ch - cur.height).coerceAtLeast(0f),
                                    ),
                                )
                            },
                            onDragEnd = {
                                dragPreview?.let { preview ->
                                    val rel = mapWindowPxToRel(preview, cw, ch)
                                    settingsViewModel.saveMainScreenMapWindowLayout(
                                        rel.relX,
                                        rel.relY,
                                        rel.relWidth,
                                        rel.relHeight,
                                    )
                                    committedLayout = preview
                                }
                                dragPreview = null
                                layoutInteraction = false
                            },
                            onDragCancel = {
                                dragPreview = null
                                layoutInteraction = false
                                committedLayout = mapWindowLayoutFromRel(
                                    config.relX,
                                    config.relY,
                                    config.relWidth,
                                    config.relHeight,
                                    cw,
                                    ch,
                                )
                            },
                        )
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(MapHandleSizeDp)
                    .background(MapPreviewBorderColor.copy(alpha = 0.35f))
                    .pointerInput(cw, ch, minPanelPx) {
                        detectDragGestures(
                            onDragStart = {
                                layoutInteraction = true
                                val base = committedLayoutState.value
                                dragPreview = base
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val base = committedLayoutState.value
                                val cur = dragPreview ?: base
                                val newW = (cur.width + dragAmount.x)
                                    .coerceIn(minPanelPx, cw - cur.x)
                                val newH = (cur.height + dragAmount.y)
                                    .coerceIn(minPanelPx, ch - cur.y)
                                dragPreview = cur.copy(width = newW, height = newH)
                            },
                            onDragEnd = {
                                dragPreview?.let { preview ->
                                    val rel = mapWindowPxToRel(preview, cw, ch)
                                    settingsViewModel.saveMainScreenMapWindowLayout(
                                        rel.relX,
                                        rel.relY,
                                        rel.relWidth,
                                        rel.relHeight,
                                    )
                                    committedLayout = preview
                                }
                                dragPreview = null
                                layoutInteraction = false
                            },
                            onDragCancel = {
                                dragPreview = null
                                layoutInteraction = false
                                committedLayout = mapWindowLayoutFromRel(
                                    config.relX,
                                    config.relY,
                                    config.relWidth,
                                    config.relHeight,
                                    cw,
                                    ch,
                                )
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun YandexMapKitView(
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember(context) {
        MapView(context).apply {
            mapWindow.map.move(
                CameraPosition(
                    Point(55.751225, 37.62954),
                    12f,
                    0f,
                    0f,
                ),
            )
        }
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
