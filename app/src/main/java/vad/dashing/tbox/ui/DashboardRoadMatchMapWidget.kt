package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.location.roadmatch.OverlayEdgePolyline
import vad.dashing.tbox.location.roadmatch.OverlayPoseMarker
import vad.dashing.tbox.location.roadmatch.RoadMatchCanvasProjection
import vad.dashing.tbox.location.roadmatch.RoadMatchCanvasViewport
import vad.dashing.tbox.location.roadmatch.RoadMatchOverlayRepository
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phase F2a: road-match map tile without basemap, network, MapKit, or Android GPS.
 * It renders only F1 data from [RoadMatchOverlayRepository] on Compose Canvas.
 */
@Composable
fun DashboardRoadMatchMapWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
) {
    val state by RoadMatchOverlayRepository.state.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_road_match_map_widget)
    val title = titleOverride.trim().ifBlank { defaultTitle }
    val noData = stringResource(
        when (state.fallbackReason) {
            "no_graph" -> R.string.road_match_map_widget_no_graph
            "no_edge" -> R.string.road_match_map_widget_no_edge
            else -> R.string.road_match_map_widget_no_data
        },
    )

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { _, resolvedTextColor ->
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (showTitle) 18.dp else 6.dp),
            ) {
                val viewport = RoadMatchCanvasProjection.viewport(
                    state = state,
                    aspectRatio = size.width / size.height.coerceAtLeast(1f),
                )
                if (viewport != null) {
                    state.neighborEdges.forEach {
                        drawOverlayEdge(
                            edge = it,
                            viewport = viewport,
                            color = resolvedTextColor.copy(alpha = 0.28f),
                            widthPx = 1.5.dp.toPx(),
                        )
                    }
                    state.matchedEdge?.let {
                        drawOverlayEdge(
                            edge = it,
                            viewport = viewport,
                            color = Color(0xFF2180F3),
                            widthPx = 3.5.dp.toPx(),
                        )
                    }
                    // Shadow first, GNSS on top as a ring — yellow must not hide under green
                    // when soft-blend leaves them nearly coincident.
                    drawPoseMarker(
                        marker = state.shadow,
                        viewport = viewport,
                        color = Color(0xFF35C46A),
                        radiusPx = 6.dp.toPx(),
                    )
                    if (state.gnss.visible) {
                        drawGnssRingMarker(
                            marker = state.gnss,
                            viewport = viewport,
                            color = Color(0xFFF3A721),
                            radiusPx = 7.dp.toPx(),
                        )
                    }
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
            if (!state.shadow.visible) {
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
        }
    }
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
    val bearing = marker.bearingDeg ?: return
    val angle = Math.toRadians(bearing.toDouble())
    val tip = Offset(
        x = center.x + (sin(angle) * radiusPx * 2.6).toFloat(),
        y = center.y - (cos(angle) * radiusPx * 2.6).toFloat(),
    )
    drawLine(
        color = color,
        start = center,
        end = tip,
        strokeWidth = (radiusPx * 0.65f).coerceAtLeast(2f),
        cap = StrokeCap.Round,
    )
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
    val bearing = marker.bearingDeg ?: return
    val angle = Math.toRadians(bearing.toDouble())
    val tip = Offset(
        x = center.x + (sin(angle) * radiusPx * 2.4).toFloat(),
        y = center.y - (cos(angle) * radiusPx * 2.4).toFloat(),
    )
    drawLine(
        color = color,
        start = center,
        end = tip,
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}
