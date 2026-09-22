package vad.dashing.tbox.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.MapsCamerasRadarsLogic
import vad.dashing.tbox.R
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorState
import vad.dashing.tbox.speedcam.DEFAULT_SPEED_CAM_OVERAGE_KMH
import vad.dashing.tbox.speedcam.SpeedCamLookahead
import vad.dashing.tbox.speedcam.SpeedCamRepository
import vad.dashing.tbox.speedcam.normalizeSpeedCamOverageKmh
import vad.dashing.tbox.ui.theme.WidgetActiveColors
import vad.dashing.tbox.ui.theme.scaledWidgetText
import java.util.Locale

/** Red ring of a round speed-limit road sign (same visual language as SLA tile). */
private val OsmSignRingColor = Color(0xFFE53935)
private const val OsmInactiveAlpha = 0.4f
private val OsmSignRingWidth = 8.dp

/**
 * Unified maps / cameras / radars speed-limit tile (`osmSpeedLimitWidget`).
 *
 * Layout (when all columns enabled): three equal columns —
 * left camera+distance, center current limit, right ahead limit+distance.
 * Disabled columns collapse; remaining stretch.
 *
 * Overspeed highlighting uses control active/inactive content colors:
 * - camera ahead overspeed → icon + text in blocks 1 / 2 / 4 / 5
 * - current limit overspeed → digits in block 3
 * Custom icons can keep their own colors via «preserve colors» in icon settings.
 */
@Composable
fun DashboardOsmSpeedLimitWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    activeColor: Color = WidgetActiveColors.Danger,
    inactiveColor: Color = textColor,
    showCameras: Boolean = true,
    showCurrentLimit: Boolean = true,
    showAheadLimit: Boolean = true,
    overageKmh: Int = DEFAULT_SPEED_CAM_OVERAGE_KMH,
    radarHoldDistanceM: Int = vad.dashing.tbox.DEFAULT_MAPS_CAM_RADAR_HOLD_M,
) {
    val anchor by RoadMatchAnchorRepository.state.collectAsStateWithLifecycle()
    val camState by SpeedCamRepository.state.collectAsStateWithLifecycle()
    val geo by GeoDisplayRepository.state.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_osm_speed_limit_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val dashLabel = stringResource(R.string.osm_speed_limit_unknown)
    val packMissing = stringResource(R.string.speed_cam_pack_missing)
    val context = LocalContext.current
    val isRu = LocalConfiguration.current.locales[0]?.language.equals("ru", ignoreCase = true) ||
        Locale.getDefault().language.equals("ru", ignoreCase = true)
    val tileOverage = normalizeSpeedCamOverageKmh(overageKmh)
    val display = MapsCamerasRadarsDisplay.from(
        anchor = anchor,
        camAlert = camState.alert,
        camInstalled = camState.installed,
        lastRadar = SpeedCamRepository.lastRadarLimit(),
        vehicleLat = geo.latitude,
        vehicleLon = geo.longitude,
        vehicleSpeedKmh = geo.speedKmh,
        overageKmh = tileOverage,
        holdDistanceM = radarHoldDistanceM,
        showCameras = showCameras,
        showCurrentLimit = showCurrentLimit,
        showAheadLimit = showAheadLimit,
    )
    val cameraAccent = if (display.cameraOverLimit) activeColor else inactiveColor
    val currentAccent = if (display.currentOverLimit) activeColor else inactiveColor

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            val mainTextStyle = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = TextType.VALUE,
            )
            val sideTextStyle = mainTextStyle.scaledWidgetText(0.55f)
            val distStyle = mainTextStyle.scaledWidgetText(0.4f)
            val columns = mutableListOf<MapsCamColumn>()
            if (display.showCamerasColumn) columns.add(MapsCamColumn.Cameras)
            if (display.showCurrentColumn) columns.add(MapsCamColumn.Current)
            if (display.showAheadColumn) columns.add(MapsCamColumn.Ahead)
            if (columns.isEmpty()) {
                Text(
                    text = dashLabel,
                    color = resolvedTextColor.copy(alpha = 0.5f),
                    style = mainTextStyle,
                    modifier = contentModifier.fillMaxSize(),
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(modifier = contentModifier.fillMaxSize()) {
                    for (col in columns) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (col) {
                                MapsCamColumn.Cameras -> MapsCamCameraColumn(
                                    display = display,
                                    dashLabel = dashLabel,
                                    packMissing = packMissing,
                                    isRu = isRu,
                                    emptyTextColor = resolvedTextColor,
                                    distanceColor = cameraAccent,
                                    iconTextStyle = sideTextStyle,
                                    distStyle = distStyle,
                                )
                                MapsCamColumn.Current -> {
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val diameter = min(maxWidth, maxHeight) * 0.9f
                                        OsmSpeedLimitSign(
                                            label = display.currentLabel ?: dashLabel,
                                            textStyle = mainTextStyle,
                                            digitColor = if (display.currentLabel != null) {
                                                currentAccent
                                            } else {
                                                resolvedTextColor
                                            },
                                            alpha = if (display.currentLabel != null) {
                                                1f
                                            } else {
                                                OsmInactiveAlpha
                                            },
                                            modifier = Modifier.size(diameter),
                                        )
                                    }
                                }
                                MapsCamColumn.Ahead -> MapsCamAheadColumn(
                                    display = display,
                                    dashLabel = dashLabel,
                                    context = context,
                                    emptyTextColor = resolvedTextColor,
                                    accentColor = cameraAccent,
                                    signTextStyle = sideTextStyle,
                                    distStyle = distStyle,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MapsCamColumn { Cameras, Current, Ahead }

@Composable
private fun MapsCamCameraColumn(
    display: MapsCamerasRadarsDisplay,
    dashLabel: String,
    packMissing: String,
    isRu: Boolean,
    emptyTextColor: Color,
    distanceColor: Color,
    iconTextStyle: TextStyle,
    distStyle: TextStyle,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (display.cameraAlert == null) {
                val emptyLabel = if (!display.camInstalled) packMissing else dashLabel
                Text(
                    text = emptyLabel,
                    color = emptyTextColor.copy(alpha = 0.5f),
                    style = iconTextStyle.scaledWidgetText(if (!display.camInstalled) 0.85f else 1f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(0.85f),
                    contentAlignment = Alignment.Center,
                ) {
                    val iconSize = min(maxWidth, maxHeight)
                    SpeedCamTypeIcon(
                        category = display.cameraAlert.point.category,
                        relative = display.cameraAlert.relative,
                        speedKmh = 0,
                        color = distanceColor,
                        speedTextStyle = iconTextStyle.scaledWidgetText(0.7f),
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val dist = display.cameraDistanceM
            val text = when {
                dist == null -> dashLabel
                isRu -> SpeedCamLookahead.formatDistanceM(dist)
                else -> SpeedCamLookahead.formatDistanceMEn(dist)
            }
            Text(
                text = text,
                color = if (dist != null) distanceColor else emptyTextColor.copy(alpha = 0.5f),
                style = distStyle,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MapsCamAheadColumn(
    display: MapsCamerasRadarsDisplay,
    dashLabel: String,
    context: android.content.Context,
    emptyTextColor: Color,
    accentColor: Color,
    signTextStyle: TextStyle,
    distStyle: TextStyle,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(0.85f),
                contentAlignment = Alignment.Center,
            ) {
                val diameter = min(maxWidth, maxHeight)
                OsmSpeedLimitSign(
                    label = display.aheadLabel ?: dashLabel,
                    textStyle = signTextStyle,
                    digitColor = if (display.aheadLabel != null) accentColor else emptyTextColor,
                    alpha = if (display.aheadLabel != null) 1f else OsmInactiveAlpha,
                    modifier = Modifier.size(diameter),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val distText = display.aheadDistanceM?.let {
                OsmSpeedLimitDisplay.formatDistanceAhead(context, it)
            } ?: dashLabel
            Text(
                text = distText,
                color = if (display.aheadDistanceM != null) {
                    accentColor
                } else {
                    emptyTextColor.copy(alpha = 0.5f)
                },
                style = distStyle,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OsmSpeedLimitSign(
    label: String,
    textStyle: TextStyle,
    alpha: Float,
    digitColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(
                width = OsmSignRingWidth,
                color = OsmSignRingColor.copy(alpha = alpha),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = digitColor.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = textStyle,
            maxLines = 1,
        )
    }
}

/** Pure display model for the unified maps/cameras/radars tile (unit-testable). */
data class MapsCamerasRadarsDisplay(
    val showCamerasColumn: Boolean,
    val showCurrentColumn: Boolean,
    val showAheadColumn: Boolean,
    val camInstalled: Boolean,
    val cameraAlert: vad.dashing.tbox.speedcam.SpeedCamAlert?,
    val cameraDistanceM: Double?,
    val cameraOverLimit: Boolean,
    val currentOverLimit: Boolean,
    val currentLabel: String?,
    val aheadLabel: String?,
    val aheadDistanceM: Double?,
) {
    companion object {
        fun from(
            anchor: RoadMatchAnchorState,
            camAlert: vad.dashing.tbox.speedcam.SpeedCamAlert?,
            camInstalled: Boolean,
            lastRadar: vad.dashing.tbox.LastRadarLimit?,
            vehicleLat: Double,
            vehicleLon: Double,
            vehicleSpeedKmh: Float,
            overageKmh: Int,
            holdDistanceM: Int,
            showCameras: Boolean,
            showCurrentLimit: Boolean,
            showAheadLimit: Boolean,
        ): MapsCamerasRadarsDisplay {
            val currentKmh = MapsCamerasRadarsLogic.resolveCurrentLimitKmh(
                osmCurrentKmh = anchor.currentLimitKmh,
                lastRadar = lastRadar,
                vehicleLat = vehicleLat,
                vehicleLon = vehicleLon,
                holdDistanceM = holdDistanceM,
            )
            val osmAhead = if (!anchor.nextLimitHidden &&
                anchor.nextLimitKmh != null &&
                anchor.nextLimitKmh > 0 &&
                anchor.nextLimitDistanceM != null
            ) {
                MapsCamerasRadarsLogic.AheadCandidate(
                    limitKmh = anchor.nextLimitKmh,
                    distanceM = anchor.nextLimitDistanceM,
                )
            } else {
                null
            }
            val camAhead = camAlert?.takeIf { it.point.hasSpeedLimit }?.let {
                MapsCamerasRadarsLogic.AheadCandidate(
                    limitKmh = it.point.speedKmh,
                    distanceM = it.distanceM,
                )
            }
            val ahead = MapsCamerasRadarsLogic.pickAhead(osmAhead, camAhead)
            val cameraOverLimit = camAlert != null &&
                camAlert.point.hasSpeedLimit &&
                vehicleSpeedKmh.isFinite() &&
                vehicleSpeedKmh > camAlert.point.speedKmh + overageKmh
            val currentOverLimit = currentKmh != null &&
                currentKmh > 0 &&
                vehicleSpeedKmh.isFinite() &&
                vehicleSpeedKmh > currentKmh + overageKmh
            return MapsCamerasRadarsDisplay(
                showCamerasColumn = showCameras,
                showCurrentColumn = showCurrentLimit,
                showAheadColumn = showAheadLimit,
                camInstalled = camInstalled,
                cameraAlert = camAlert,
                cameraDistanceM = camAlert?.distanceM,
                cameraOverLimit = cameraOverLimit,
                currentOverLimit = currentOverLimit,
                currentLabel = currentKmh?.toString(),
                aheadLabel = ahead.limitKmh?.toString(),
                aheadDistanceM = ahead.distanceM,
            )
        }
    }
}

/** Pure display helpers retained for distance formatting tests. */
data class OsmSpeedLimitDisplay(
    val currentLabel: String?,
    val nextLabel: String?,
    val nextDistanceM: Double?,
    val showNext: Boolean,
) {
    fun nextDistanceLabel(context: android.content.Context): String? {
        val meters = nextDistanceM ?: return null
        return formatDistanceAhead(context, meters)
    }

    companion object {
        fun from(anchor: RoadMatchAnchorState): OsmSpeedLimitDisplay {
            val current = anchor.currentLimitKmh?.takeIf { it > 0 }?.toString()
            val nextHidden = anchor.nextLimitHidden
            val next = anchor.nextLimitKmh?.takeIf { it > 0 }?.toString()
            val dist = anchor.nextLimitDistanceM?.takeIf { it.isFinite() && it >= 0.0 }
            val showNext = !nextHidden && next != null
            return OsmSpeedLimitDisplay(
                currentLabel = current,
                nextLabel = next,
                nextDistanceM = dist.takeIf { showNext },
                showNext = showNext,
            )
        }

        fun formatDistanceAhead(context: android.content.Context, meters: Double): String {
            if (!meters.isFinite() || meters < 0.0) {
                return context.getString(R.string.osm_speed_limit_distance_unknown)
            }
            return if (meters >= 1000.0) {
                val km = meters / 1000.0
                val text = if (km >= 10.0) {
                    km.toInt().toString()
                } else {
                    String.format(Locale.US, "%.1f", km)
                }
                context.getString(R.string.osm_speed_limit_distance_km, text)
            } else {
                context.getString(R.string.osm_speed_limit_distance_m, meters.toInt())
            }
        }
    }
}
