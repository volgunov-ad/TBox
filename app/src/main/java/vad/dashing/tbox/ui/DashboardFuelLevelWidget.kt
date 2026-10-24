package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FUEL_LITERS_DISPLAY_TO_FULL
import vad.dashing.tbox.R
import vad.dashing.tbox.normalizeFuelLitersDisplayMode

@Composable
fun DashboardFuelLevelWidgetItem(
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
    fuelLitersDisplayMode: Int = 0,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val toFull = normalizeFuelLitersDisplayMode(fuelLitersDisplayMode) == FUEL_LITERS_DISPLAY_TO_FULL
    val litersDataKey = if (toFull) {
        FUEL_LEVEL_LITERS_TO_FULL_FLOW_KEY
    } else {
        "fuelLevelLiters"
    }
    val pctFlow = remember(valueAccuracy) {
        dataProvider.getValueFlow("fuelLevelPercentageFiltered", valueAccuracy)
    }
    val litersFlow = remember(valueAccuracy, litersDataKey) {
        dataProvider.getValueFlow(litersDataKey, valueAccuracy)
    }
    val pctStr by pctFlow.collectAsStateWithLifecycle()
    val litersStr by litersFlow.collectAsStateWithLifecycle()
    val percentUnit = stringResource(R.string.unit_percent)
    val literUnit = stringResource(R.string.unit_liter)
    val firstLine = "$pctStr${if (units) "\u2009$percentUnit" else ""}"
    val secondLine = "$litersStr${if (units) "\u2009$literUnit" else ""}"
    val defaultTitle = stringResource(R.string.widget_title_fuel_level)
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
