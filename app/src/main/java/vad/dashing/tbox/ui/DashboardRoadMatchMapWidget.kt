package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.location.roadmatch.OverlayEdgePolyline
import vad.dashing.tbox.location.roadmatch.OverlayPoseMarker
import vad.dashing.tbox.location.roadmatch.RoadGraphStore
import vad.dashing.tbox.location.roadmatch.RoadMatchCanvasProjection
import vad.dashing.tbox.location.roadmatch.RoadMatchCanvasViewport
import vad.dashing.tbox.location.roadmatch.RoadMatchManualSeed
import vad.dashing.tbox.location.roadmatch.RoadMatchManualSeedRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchOverlayBuilder
import vad.dashing.tbox.location.roadmatch.RoadMatchOverlayRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchSeedMath
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Heading tick from the pose center, in marker radii. */
private const val HEADING_LINE_LENGTH_RADII = 3.6f
private const val HEADING_LINE_STROKE_RADII = 0.95f

/**
 * Phase F2a + F3: road-match map tile without basemap, network, MapKit, or Android GPS.
 * Follow mode renders F1 data. Set-mode pins the shadow and pans/zooms the map under it.
 */
@Composable
fun DashboardRoadMatchMapWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean = true,
    isEditMode: Boolean = false,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
) {
    val live by RoadMatchOverlayRepository.state.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_road_match_map_widget)
    val title = titleOverride.trim().ifBlank { defaultTitle }
    val noData = stringResource(
        when (live.fallbackReason) {
            "no_graph" -> R.string.road_match_map_widget_no_graph
            "no_edge" -> R.string.road_match_map_widget_no_edge
            else -> R.string.road_match_map_widget_no_data
        },
    )
    val setLabel = stringResource(R.string.road_match_map_widget_set)
    val applyLabel = stringResource(R.string.road_match_map_widget_apply)
    val cancelLabel = stringResource(R.string.road_match_map_widget_cancel)

    var setMode by remember { mutableStateOf(false) }
    var draftLat by remember { mutableDoubleStateOf(0.0) }
    var draftLon by remember { mutableDoubleStateOf(0.0) }
    var draftBearing by remember { mutableFloatStateOf(0f) }
    var halfHeightM by remember { mutableDoubleStateOf(RoadMatchCanvasProjection.MIN_HALF_SPAN_M) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val canOfferSet = enableInnerInteractions && !isEditMode && live.shadow.visible
    LaunchedEffect(enableInnerInteractions, isEditMode) {
        if (!enableInnerInteractions || isEditMode) {
            setMode = false
        }
    }

    val aspect = if (canvasSize.height > 0) {
        canvasSize.width.toFloat() / canvasSize.height.toFloat()
    } else {
        1f
    }
    val graphsSize = RoadGraphStore.cachedGraphs().size
    val neighborCell = if (setMode) {
        Pair((draftLat * 8_000.0).toInt(), (draftLon * 8_000.0).toInt())
    } else {
        null
    }
    val setNeighbors = remember(neighborCell, graphsSize, live.matchedEdge?.edgeId) {
        if (neighborCell == null) {
            emptyList()
        } else {
            RoadMatchOverlayBuilder.neighborsAround(
                graphs = RoadGraphStore.cachedGraphs(),
                lat = draftLat,
                lon = draftLon,
                excludeEdgeId = live.matchedEdge?.edgeId,
                excludeRegionId = live.matchedEdge?.regionId,
            )
        }
    }
    val displayState = if (setMode) {
        live.copy(
            shadow = OverlayPoseMarker(
                lat = draftLat,
                lon = draftLon,
                bearingDeg = draftBearing,
                visible = true,
            ),
            neighborEdges = setNeighbors,
        )
    } else {
        live
    }
    val viewport = if (setMode) {
        RoadMatchCanvasProjection.viewportAt(
            centerLat = draftLat,
            centerLon = draftLon,
            halfHeightM = halfHeightM,
            aspectRatio = aspect,
        )
    } else {
        RoadMatchCanvasProjection.viewport(displayState, aspect)
    }

    fun enterSetMode() {
        val shadow = live.shadow
        if (!shadow.visible) return
        draftLat = shadow.lat
        draftLon = shadow.lon
        draftBearing = shadow.bearingDeg ?: 0f
        halfHeightM = RoadMatchSeedMath.clampSetHalfSpanM(
            viewport?.halfHeightM ?: RoadMatchCanvasProjection.MIN_HALF_SPAN_M,
        )
        setMode = true
    }

    fun applySetMode() {
        val seed = RoadMatchManualSeed.create(draftLat, draftLon, draftBearing) ?: return
        RoadMatchManualSeedRepository.request(seed)
        setMode = false
    }

    fun cancelSetMode() {
        setMode = false
    }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = if (setMode) ({}) else onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        cardGesturesEnabled = !setMode,
    ) { _, resolvedTextColor ->
        Box(modifier = Modifier.fillMaxSize()) {
            val draftLatLatest by rememberUpdatedState(draftLat)
            val draftLonLatest by rememberUpdatedState(draftLon)
            val halfHeightLatest by rememberUpdatedState(halfHeightM)
            val gestureModifier = if (setMode) {
                Modifier.pointerInput(setMode) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val minDim = min(size.width, size.height)
                        val ringR = RoadMatchSeedMath.headingRingRadiusPx(minDim)
                        val band = RoadMatchSeedMath.headingRingBandPx(minDim)
                        val dx = centroid.x - size.width * 0.5f
                        val dy = centroid.y - size.height * 0.5f
                        val onRing = zoom == 1f &&
                            RoadMatchSeedMath.isOnHeadingRing(dx, dy, ringR - band, ringR + band)
                        if (onRing) {
                            draftBearing = RoadMatchSeedMath.bearingFromCanvasDelta(dx, dy)
                            return@detectTransformGestures
                        }
                        var span = halfHeightLatest
                        if (zoom != 1f) {
                            span = RoadMatchSeedMath.applyPinchZoom(span, zoom)
                            halfHeightM = span
                        }
                        if (pan.x != 0f || pan.y != 0f) {
                            val vp = RoadMatchCanvasProjection.viewportAt(
                                centerLat = draftLatLatest,
                                centerLon = draftLonLatest,
                                halfHeightM = span,
                                aspectRatio = size.width / size.height.coerceAtLeast(1f),
                            )
                            val (eastM, northM) = RoadMatchSeedMath.panToEastNorthM(
                                panXpx = pan.x,
                                panYpx = pan.y,
                                widthPx = size.width,
                                heightPx = size.height,
                                halfWidthM = vp.halfWidthM,
                                halfHeightM = vp.halfHeightM,
                            )
                            val moved = RoadMatchSeedMath.shiftCenter(
                                lat = draftLatLatest,
                                lon = draftLonLatest,
                                eastM = eastM,
                                northM = northM,
                            )
                            draftLat = moved.lat
                            draftLon = moved.lon
                        }
                    }
                }
            } else {
                Modifier
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (showTitle) 18.dp else 6.dp)
                    .onSizeChanged { canvasSize = it }
                    .then(gestureModifier),
            ) {
                val vp = viewport ?: return@Canvas
                displayState.neighborEdges.forEach {
                    drawOverlayEdge(
                        edge = it,
                        viewport = vp,
                        color = resolvedTextColor.copy(alpha = 0.28f),
                        widthPx = 1.5.dp.toPx(),
                    )
                }
                displayState.matchedEdge?.let {
                    drawOverlayEdge(
                        edge = it,
                        viewport = vp,
                        color = Color(0xFF2180F3),
                        widthPx = 3.5.dp.toPx(),
                    )
                }
                if (setMode) {
                    val minDim = min(size.width, size.height)
                    val ringR = RoadMatchSeedMath.headingRingRadiusPx(minDim)
                    drawHeadingRing(
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radiusPx = ringR,
                        color = Color(0xFF35C46A),
                    )
                }
                drawPoseMarker(
                    marker = displayState.shadow,
                    viewport = vp,
                    color = Color(0xFF35C46A),
                    radiusPx = 6.dp.toPx(),
                )
                if (displayState.gnss.visible) {
                    drawGnssRingMarker(
                        marker = displayState.gnss,
                        viewport = vp,
                        color = Color(0xFFF3A721),
                        radiusPx = 7.dp.toPx(),
                    )
                }
            }

            if (showTitle) {
                Text(
                    text = title,
                    color = resolvedTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            if (!displayState.shadow.visible) {
                Text(
                    text = noData,
                    color = resolvedTextColor.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
                )
            }
            if (canOfferSet && !setMode) {
                SeedActionText(
                    text = setLabel,
                    color = resolvedTextColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    onClick = { enterSetMode() },
                )
            } else if (setMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SeedActionText(
                        text = cancelLabel,
                        color = resolvedTextColor,
                        onClick = { cancelSetMode() },
                    )
                    SeedActionText(
                        text = applyLabel,
                        color = resolvedTextColor,
                        onClick = { applySetMode() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedActionText(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

private fun DrawScope.toOffset(
    lat: Double,
    lon: Double,
    viewport: RoadMatchCanvasViewport,
): Offset {
    val p = viewport.project(lat, lon)
    return Offset(p.x * size.width, p.y * size.height)
}

private fun DrawScope.drawOverlayEdge(
    edge: OverlayEdgePolyline,
    viewport: RoadMatchCanvasViewport,
    color: Color,
    widthPx: Float,
) {
    if (edge.points.size < 2) return
    val path = Path()
    edge.points.forEachIndexed { index, point ->
        val p = toOffset(point.lat, point.lon, viewport)
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = widthPx, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawPoseMarker(
    marker: OverlayPoseMarker,
    viewport: RoadMatchCanvasViewport,
    color: Color,
    radiusPx: Float,
) {
    if (!marker.visible) return
    val center = toOffset(marker.lat, marker.lon, viewport)
    drawCircle(color = Color.Black.copy(alpha = 0.34f), radius = radiusPx + 2f, center = center)
    drawCircle(color = color, radius = radiusPx, center = center)
    drawHeadingTick(center = center, bearingDeg = marker.bearingDeg, color = color, radiusPx = radiusPx)
}

/** Hollow ring so GNSS stays readable when it sits on the green shadow. */
private fun DrawScope.drawGnssRingMarker(
    marker: OverlayPoseMarker,
    viewport: RoadMatchCanvasViewport,
    color: Color,
    radiusPx: Float,
) {
    if (!marker.visible) return
    val center = toOffset(marker.lat, marker.lon, viewport)
    val stroke = (radiusPx * 0.35f).coerceAtLeast(2.5f)
    drawCircle(
        color = Color.Black.copy(alpha = 0.40f),
        radius = radiusPx + 1.5f,
        center = center,
        style = Stroke(width = stroke + 1.5f),
    )
    drawCircle(
        color = color,
        radius = radiusPx,
        center = center,
        style = Stroke(width = stroke),
    )
    drawHeadingTick(center = center, bearingDeg = marker.bearingDeg, color = color, radiusPx = radiusPx)
}

private fun DrawScope.drawHeadingRing(
    center: Offset,
    radiusPx: Float,
    color: Color,
) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.28f),
        radius = radiusPx,
        center = center,
        style = Stroke(width = 5.5f),
    )
    drawCircle(
        color = color.copy(alpha = 0.88f),
        radius = radiusPx,
        center = center,
        style = Stroke(width = 3.5f),
    )
}

private fun DrawScope.drawHeadingTick(
    center: Offset,
    bearingDeg: Float?,
    color: Color,
    radiusPx: Float,
) {
    val bearing = bearingDeg ?: return
    val angle = Math.toRadians(bearing.toDouble())
    val length = radiusPx * HEADING_LINE_LENGTH_RADII
    val tip = Offset(
        x = center.x + (sin(angle) * length).toFloat(),
        y = center.y - (cos(angle) * length).toFloat(),
    )
    drawLine(
        color = color,
        start = center,
        end = tip,
        strokeWidth = (radiusPx * HEADING_LINE_STROKE_RADII).coerceAtLeast(3.5f),
        cap = StrokeCap.Round,
    )
}
