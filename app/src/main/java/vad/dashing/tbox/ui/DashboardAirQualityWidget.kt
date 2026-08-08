package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R

@Composable
fun DashboardAirQualityWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    dataProvider: DataProvider,
    valueAccuracy: Int? = null,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    showTitle: Boolean = false,
    titleOverride: String = "",
    singleLineDualMetrics: Boolean = false,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    useMbCan: Boolean = false,
) {
    val outsideKey = if (useMbCan) OUTSIDE_AIR_QUALITY_CAN_FLOW_KEY else "outsideAirQuality"
    val insideKey = if (useMbCan) INSIDE_AIR_QUALITY_CAN_FLOW_KEY else "insideAirQuality"
    val outsideFlow = remember(valueAccuracy, useMbCan) {
        dataProvider.getValueFlow(outsideKey, valueAccuracy)
    }
    val insideFlow = remember(valueAccuracy, useMbCan) {
        dataProvider.getValueFlow(insideKey, valueAccuracy)
    }
    val firstLine by outsideFlow.collectAsStateWithLifecycle()
    val secondLine by insideFlow.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.widget_title_air_quality_outside_inside)
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
            DashboardDualMetricRows(
                firstLine = firstLine,
                secondLine = secondLine,
                singleLineDualMetrics = singleLineDualMetrics,
                availableHeight = availableHeight,
                resolvedTextColor = resolvedTextColor,
                modifier = contentModifier,
            )
        }
    }
}
