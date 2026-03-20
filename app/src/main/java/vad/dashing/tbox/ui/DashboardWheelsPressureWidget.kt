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
    elevation: Dp = 0.dp,
    shape: Dp = 12.dp,
    units: Boolean = true,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val wheelsPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()

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
                        wheelsPressure.wheel1,
                        availableHeight,
                        TextAlign.Left,
                        TextType.VALUE,
                        textColor = resolvedTextColor
                    )
                }
                Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                    PressureText(
                        wheelsPressure.wheel2,
                        availableHeight,
                        TextAlign.Right,
                        TextType.VALUE,
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
                        wheelsPressure.wheel3,
                        availableHeight,
                        TextAlign.Left,
                        TextType.VALUE,
                        textColor = resolvedTextColor
                    )
                }
                Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                    PressureText(
                        wheelsPressure.wheel4,
                        availableHeight,
                        TextAlign.Right,
                        TextType.VALUE,
                        textColor = resolvedTextColor
                    )
                }
            }
        }
    }
}