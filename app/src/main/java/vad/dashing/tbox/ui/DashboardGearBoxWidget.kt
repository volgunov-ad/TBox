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
import vad.dashing.tbox.utils.GEARBOX_MODE_CURRENT_GEAR_DATA_KEY

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
    val modeGearFlow = remember(valueAccuracy) {
        dataProvider.getValueFlow(GEARBOX_MODE_CURRENT_GEAR_DATA_KEY, valueAccuracy)
    }
    val oilFlow = remember(valueAccuracy) {
        dataProvider.getValueFlow(DashboardCompositeTileFlowKeys.GEARBOX_OIL_TEMP_GEAR_TILE, valueAccuracy)
    }
    val modeGearLine by modeGearFlow.collectAsStateWithLifecycle()
    val oilStr by oilFlow.collectAsStateWithLifecycle()
    val celsiusUnit = stringResource(R.string.unit_celsius)
    val firstLine = modeGearLine
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
