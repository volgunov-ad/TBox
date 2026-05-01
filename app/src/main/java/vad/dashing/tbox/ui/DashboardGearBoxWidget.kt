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
fun DashboardGearBoxWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    dataProvider: DataProvider,
    valueAccuracy: Int? = null,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    units: Boolean = true,
    showTitle: Boolean = false,
    titleOverride: String = "",
    singleLineDualMetrics: Boolean = false,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val modeFlow = remember(valueAccuracy) {
        dataProvider.getValueFlow("gearBoxMode", valueAccuracy)
    }
    val gearFlow = remember(valueAccuracy) {
        dataProvider.getValueFlow("gearBoxCurrentGear", valueAccuracy)
    }
    val oilFlow = remember(valueAccuracy) {
        dataProvider.getValueFlow("gearBoxOilTemperature", valueAccuracy)
    }
    val gearBoxMode by modeFlow.collectAsStateWithLifecycle()
    val gearStr by gearFlow.collectAsStateWithLifecycle()
    val oilStr by oilFlow.collectAsStateWithLifecycle()
    val celsiusUnit = stringResource(R.string.unit_celsius)
    val firstLine = "$gearBoxMode$gearStr"
    val secondLine = "$oilStr${if (units) "\u2009$celsiusUnit" else ""}"
    val defaultTitle = stringResource(R.string.widget_title_gear_box_mode_temp)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showTitle) {
                val titleFont = calculateResponsiveFontSize(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE
                )
                Text(
                    text = titleText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically),
                    fontSize = titleFont,
                    lineHeight = titleFont * 1.3f,
                    fontWeight = FontWeight.Medium,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DashboardDualMetricRows(
                firstLine = firstLine,
                secondLine = secondLine,
                singleLineDualMetrics = singleLineDualMetrics,
                availableHeight = availableHeight,
                resolvedTextColor = resolvedTextColor,
                modifier = Modifier.weight(
                    if (showTitle && !singleLineDualMetrics) 2f else 2f
                )
            )
        }
    }
}
