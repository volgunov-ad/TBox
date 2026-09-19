package vad.dashing.tbox.ui

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
import androidx.compose.ui.graphics.Color
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
import vad.dashing.tbox.speedcam.SpeedCamUiIcons
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
                            SpeedCamTypeIcon(
                                category = alert.point.category,
                                relative = alert.relative,
                                speedKmh = alert.point.speedKmh,
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
private fun SpeedCamTypeIcon(
    category: SpeedCamCategory,
    relative: SpeedCamRelativeDirection,
    speedKmh: Int,
    color: Color,
    speedTextStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val typeIcon = SpeedCamUiIcons.forCategory(category)
    val arrowIcon = SpeedCamUiIcons.forRelative(relative)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CustomizableUiIcon(
            iconKey = typeIcon.key,
            drawableRes = typeIcon.drawableRes,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.92f),
            tint = color,
        )
        CustomizableUiIcon(
            iconKey = arrowIcon.key,
            drawableRes = arrowIcon.drawableRes,
            contentDescription = null,
            modifier = Modifier
                .align(
                    if (relative == SpeedCamRelativeDirection.SAME) {
                        Alignment.TopCenter
                    } else {
                        Alignment.BottomCenter
                    },
                )
                .fillMaxSize(0.28f),
            tint = color,
        )
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
