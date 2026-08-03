package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.location.LocIndicatorState
import vad.dashing.tbox.location.MockLocationJob
import vad.dashing.tbox.valueToString

@Composable
fun DashboardLocWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    valueAccuracy: Int? = null,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    showTitle: Boolean = false,
    titleOverride: String = "",
    scale: Float = 1f
) {
    val geo by GeoDisplayRepository.state.collectAsStateWithLifecycle()
    val speedDecimals = if (valueAccuracy != null && valueAccuracy >= 0) valueAccuracy else 1
    val speedText = remember(geo.speedKmh, speedDecimals) {
        valueToString(geo.speedKmh, speedDecimals)
    }
    val satsText = remember(geo.visibleSatellites, geo.usingSatellites) {
        MockLocationJob.formatSatellites(geo.visibleSatellites, geo.usingSatellites)
    }
    val bearing = geo.bearingDeg ?: 0f

    val locIndicatorDrawable = remember(geo.indicator) {
        when (geo.indicator) {
            LocIndicatorState.NONE -> R.drawable.loc_0_err
            LocIndicatorState.LOST -> R.drawable.loc_0_warn
            LocIndicatorState.RETAINING -> R.drawable.loc_0_retain
            LocIndicatorState.LIVE -> R.drawable.loc_0_ok
        }
    }

    val defaultTitle = stringResource(R.string.data_title_loc_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            Column(
                modifier = contentModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = satsText,
                    style = calculateResponsiveTextStyle(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    color = resolvedTextColor,
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
                Image(
                    painter = painterResource(id = locIndicatorDrawable),
                    contentDescription = stringResource(
                        R.string.dashboard_loc_content_desc,
                        geo.locateStatus,
                        geo.isTruthful,
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .rotate(degrees = -bearing)
                        .weight(2f)
                        .padding(4.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .scale(scale)
                )
                Text(
                    text = "$speedText\u2009${stringResource(R.string.unit_kmh)}",
                    style = calculateResponsiveTextStyle(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    color = resolvedTextColor,
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
    }
}
