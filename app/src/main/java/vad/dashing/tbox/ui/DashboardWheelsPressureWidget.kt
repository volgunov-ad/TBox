package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardWidget

@Composable
fun DashboardWheelsPressureWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    canViewModel: CanDataViewModel,
    dataProvider: DataProvider,
    valueAccuracy: Int? = null,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    units: Boolean = true,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val wheelsPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()
    val w1Flow = remember(valueAccuracy) {
        dataProvider.getValueFlow(DashboardCompositeTileFlowKeys.WHEEL1_PRESSURE_WHEELS_TILE, valueAccuracy)
    }
    val w2Flow = remember(valueAccuracy) {
        dataProvider.getValueFlow(DashboardCompositeTileFlowKeys.WHEEL2_PRESSURE_WHEELS_TILE, valueAccuracy)
    }
    val w3Flow = remember(valueAccuracy) {
        dataProvider.getValueFlow(DashboardCompositeTileFlowKeys.WHEEL3_PRESSURE_WHEELS_TILE, valueAccuracy)
    }
    val w4Flow = remember(valueAccuracy) {
        dataProvider.getValueFlow(DashboardCompositeTileFlowKeys.WHEEL4_PRESSURE_WHEELS_TILE, valueAccuracy)
    }
    val w1Str by w1Flow.collectAsStateWithLifecycle()
    val w2Str by w2Flow.collectAsStateWithLifecycle()
    val w3Str by w3Flow.collectAsStateWithLifecycle()
    val w4Str by w4Flow.collectAsStateWithLifecycle()

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentHeight(Alignment.Top),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PressureText(
                        value = wheelsPressure.wheel1,
                        displayText = w1Str,
                        availableHeight = availableHeight,
                        align = TextAlign.Left,
                        textType = TextType.VALUE,
                        textColor = resolvedTextColor
                    )
                }
                Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                    PressureText(
                        value = wheelsPressure.wheel2,
                        displayText = w2Str,
                        availableHeight = availableHeight,
                        align = TextAlign.Right,
                        textType = TextType.VALUE,
                        textColor = resolvedTextColor
                    )
                }
            }
            if (units) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentHeight(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = widget.unit,
                        fontSize = calculateResponsiveFontSize(
                            availableHeight,
                            TextType.UNIT
                        ),
                        color = resolvedTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentHeight(Alignment.Bottom),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PressureText(
                        value = wheelsPressure.wheel3,
                        displayText = w3Str,
                        availableHeight = availableHeight,
                        align = TextAlign.Left,
                        textType = TextType.VALUE,
                        textColor = resolvedTextColor
                    )
                }
                Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                    PressureText(
                        value = wheelsPressure.wheel4,
                        displayText = w4Str,
                        availableHeight = availableHeight,
                        align = TextAlign.Right,
                        textType = TextType.VALUE,
                        textColor = resolvedTextColor
                    )
                }
            }
        }
    }
}
