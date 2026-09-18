package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.speedcam.DEFAULT_SPEED_CAM_OVERAGE_KMH
import vad.dashing.tbox.speedcam.DEFAULT_SPEED_CAM_RADIUS_M
import vad.dashing.tbox.speedcam.SpeedCamCategory
import vad.dashing.tbox.speedcam.SpeedCamLookahead
import vad.dashing.tbox.speedcam.SpeedCamRelativeDirection
import vad.dashing.tbox.speedcam.SpeedCamRepository
import vad.dashing.tbox.speedcam.normalizeSpeedCamOverageKmh
import vad.dashing.tbox.speedcam.normalizeSpeedCamRadiusM
import vad.dashing.tbox.ui.theme.WidgetActiveColors
import vad.dashing.tbox.ui.theme.scaledWidgetText
import java.util.Locale

@Composable
fun DashboardSpeedCamWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    activeColor: Color = WidgetActiveColors.Danger,
    overageKmh: Int = DEFAULT_SPEED_CAM_OVERAGE_KMH,
    radiusM: Int = DEFAULT_SPEED_CAM_RADIUS_M,
) {
    val state by SpeedCamRepository.state.collectAsStateWithLifecycle()
    val geo by GeoDisplayRepository.state.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_speed_cam_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val dash = stringResource(R.string.speed_cam_no_target)
    val isRu = LocalConfiguration.current.locales[0]?.language.equals("ru", ignoreCase = true)
        || Locale.getDefault().language.equals("ru", ignoreCase = true)
    val tileRadius = normalizeSpeedCamRadiusM(radiusM)
    val tileOverage = normalizeSpeedCamOverageKmh(overageKmh)

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
            val valueStyle = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = TextType.VALUE,
            )
            val distStyle = valueStyle.scaledWidgetText(0.55f)
            val alert = state.alert?.takeIf { it.distanceM <= tileRadius.toDouble() }
            val speed = geo.speedKmh
            val overLimit = alert != null &&
                alert.point.hasSpeedLimit &&
                speed.isFinite() &&
                speed > alert.point.speedKmh + tileOverage
            val accent = if (overLimit) activeColor else resolvedTextColor
            BoxWithConstraints(
                modifier = contentModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val iconSize = (maxHeight * 0.48f).coerceIn(28.dp, 72.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (alert == null) {
                        Text(
                            text = dash,
                            color = resolvedTextColor.copy(alpha = 0.5f),
                            style = valueStyle,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(iconSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpeedCamIcon(
                                category = alert.point.category,
                                speedKmh = alert.point.speedKmh,
                                relative = alert.relative,
                                color = accent,
                                speedTextStyle = valueStyle.scaledWidgetText(0.45f),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        val dist = if (isRu) {
                            SpeedCamLookahead.formatDistanceM(alert.distanceM)
                        } else {
                            SpeedCamLookahead.formatDistanceMEn(alert.distanceM)
                        }
                        Text(
                            text = dist,
                            color = accent,
                            style = distStyle,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedCamIcon(
    category: SpeedCamCategory,
    speedKmh: Int,
    relative: SpeedCamRelativeDirection,
    color: Color,
    speedTextStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.38f
            drawCircle(color = color, radius = r, center = Offset(cx, cy), style = stroke)
            when (category) {
                SpeedCamCategory.TRAFFIC_LIGHT -> {
                    val dotR = r * 0.18f
                    drawCircle(color, radius = dotR, center = Offset(cx, cy - r * 0.35f))
                    drawCircle(color, radius = dotR, center = Offset(cx, cy))
                    drawCircle(color, radius = dotR, center = Offset(cx, cy + r * 0.35f))
                }
                SpeedCamCategory.SECTION -> {
                    drawLine(
                        color = color,
                        start = Offset(cx - r * 0.45f, cy),
                        end = Offset(cx + r * 0.45f, cy),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
                SpeedCamCategory.RAILWAY -> {
                    drawLine(
                        color = color,
                        start = Offset(cx - r * 0.4f, cy - r * 0.35f),
                        end = Offset(cx + r * 0.4f, cy + r * 0.35f),
                        strokeWidth = stroke.width,
                    )
                    drawLine(
                        color = color,
                        start = Offset(cx + r * 0.4f, cy - r * 0.35f),
                        end = Offset(cx - r * 0.4f, cy + r * 0.35f),
                        strokeWidth = stroke.width,
                    )
                }
                else -> Unit
            }
            // Direction arrow: ↑ same travel way, ↓ oncoming
            val arrow = Path()
            val ay = if (relative == SpeedCamRelativeDirection.SAME) {
                cy - r * 0.95f
            } else {
                cy + r * 0.95f
            }
            val tipY = if (relative == SpeedCamRelativeDirection.SAME) {
                cy - r * 1.25f
            } else {
                cy + r * 1.25f
            }
            arrow.moveTo(cx, tipY)
            arrow.lineTo(cx - r * 0.28f, ay)
            arrow.lineTo(cx + r * 0.28f, ay)
            arrow.close()
            drawPath(arrow, color = color)
        }
        if (speedKmh > 0) {
            Text(
                text = speedKmh.toString(),
                color = color,
                style = speedTextStyle,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
