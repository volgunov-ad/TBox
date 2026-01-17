package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.valueToString

@Composable
fun DashboardWheelsPressureTemperatureWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    canViewModel: CanDataViewModel,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    backgroundTransparent: Boolean = false
) {
    val wheelsPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()
    val wheelsTemperature by canViewModel.wheelsTemperature.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (backgroundTransparent) Color.Transparent else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(shape)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(shape)
                )
        ) {
            val availableHeight = maxHeight
            // Основной контент
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
                            TextAlign.Left)
                    }
                    Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                        PressureText(
                            wheelsPressure.wheel2,
                            availableHeight,
                            TextAlign.Right)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentHeight(Alignment.Top),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TemperatureText(
                            wheelsTemperature.wheel1,
                            availableHeight,
                            TextAlign.Left)
                    }
                    Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                        TemperatureText(
                            wheelsTemperature.wheel2,
                            availableHeight,
                            TextAlign.Right)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentHeight(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "бар / °C",
                            fontSize = calculateResponsiveFontSize(
                                containerHeight = availableHeight,
                                textType = TextType.UNIT
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
                            TextAlign.Left)
                    }
                    Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                        PressureText(
                            wheelsPressure.wheel4,
                            availableHeight,
                            TextAlign.Right)
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
                        TemperatureText(
                            wheelsTemperature.wheel3,
                            availableHeight,
                            TextAlign.Left)
                    }
                    Box(modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                        TemperatureText(
                            wheelsTemperature.wheel4,
                            availableHeight,
                            TextAlign.Right)
                    }
                }
            }
        }
    }
}

// Функция для отрисовки одного значения давления
@Composable
private fun PressureText(
    value: Float?,
    availableHeight: Dp,
    align: TextAlign
) {
    Text(
        text = valueToString(value, 1, default = "-"),
        fontSize = calculateResponsiveFontSize(
            containerHeight = availableHeight,
            textType = TextType.TITLE
        ),
        fontWeight = FontWeight.Medium,
        color = if ((value ?: 0f) >= 1.92f) MaterialTheme.colorScheme.onSurface else Color(0xD9FF0000),
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TemperatureText(
    value: Float?,
    availableHeight: Dp,
    align: TextAlign
) {
    Text(
        text = valueToString(value, 0, default = "-"),
        fontSize = calculateResponsiveFontSize(
            containerHeight = availableHeight,
            textType = TextType.TITLE
        ),
        fontWeight = FontWeight.Medium,
        color = if ((value ?: 0f) >= 85f) MaterialTheme.colorScheme.onSurface else Color(0xD9FF0000),
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}