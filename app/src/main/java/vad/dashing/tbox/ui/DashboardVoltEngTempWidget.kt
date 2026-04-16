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
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.valueToString

@Composable
fun DashboardVoltEngTempWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    canViewModel: CanDataViewModel,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    units: Boolean = true,
    showTitle: Boolean = false,
    titleOverride: String = "",
    singleLineDualMetrics: Boolean = false,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val voltage by canViewModel.voltage.collectAsStateWithLifecycle()
    val engineTemperature by canViewModel.engineTemperature.collectAsStateWithLifecycle()
    val voltUnit = stringResource(R.string.unit_volt)
    val celsiusUnit = stringResource(R.string.unit_celsius)
    val firstLine = "${valueToString(voltage, 1)}${if (units) "\u2009$voltUnit" else ""}"
    val secondLine =
        "${valueToString(engineTemperature, 0)}${if (units) "\u2009$celsiusUnit" else ""}"
    val defaultTitle = stringResource(R.string.widget_title_voltage_engine_temp)
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
