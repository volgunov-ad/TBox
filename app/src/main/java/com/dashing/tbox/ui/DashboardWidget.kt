package com.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashing.tbox.DashboardWidget
import com.dashing.tbox.SettingsViewModel
import com.dashing.tbox.WidgetViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun DashboardWidgetItem(
    widget: DashboardWidget,
    value: String,
    onEditClick: () -> Unit,
    widgetViewModel: WidgetViewModel,
    settingsViewModel: SettingsViewModel
) {
    val widgetHistory by widgetViewModel.getWidgetHistoryFlow(widget.id).collectAsState()

    val dashboardChart by settingsViewModel.dashboardChart.collectAsStateWithLifecycle()

    if (dashboardChart) {
        val currentValue by rememberUpdatedState(value.replace(",", ".").toFloatOrNull())

        LaunchedEffect(widget.id) {
            while (true) {
                delay(1000L)

                // Обновляем историю каждую секунду
                currentValue?.let {
                    widgetViewModel.updateWidgetHistory(widget.id, it)
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onEditClick() },
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // График истории в фоне (только если есть хотя бы 2 точки и не все значения одинаковые)
            if (!widgetHistory.checkValues() && dashboardChart) {
                HistoryLineChart(
                    values = widgetHistory,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.3f)
                )
            }

            // Основной контент
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Заголовок виджета
                Text(
                    text = widget.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Значение
                Text(
                    text = value,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Единицы измерения
                if (widget.unit.isNotEmpty()) {
                    Text(
                        text = widget.unit,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Canvas(modifier = modifier) {
        // Добавляем проверку, чтобы избежать деления на ноль
        if (values.size < 2) return@Canvas

        val maxValue = values.max()
        val minValue = values.min()
        val valueRange = maxValue - minValue

        // Создаем путь для графика
        val path = Path()
        val width = size.width
        val height = size.height

        // Добавляем точки в путь
        values.forEachIndexed { index, value ->
            val x = (width * index) / (values.size - 1)
            // Более безопасное вычисление Y
            val y = if (valueRange > 0.001f) { // Добавляем небольшую эпсилон для стабильности
                height - ((value - minValue) / valueRange * height)
            } else {
                height / 2
            }

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Рисуем линию графика
        drawPath(
            path = path,
            color = colorScheme.primary,
            style = Stroke(
                width = 4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun List<Float>.checkValues(epsilon: Float = 0.001f): Boolean {
    if (isEmpty()) return true
    if (size < 2) return true

    val first = this[0]
    return all { abs(it - first) < epsilon }
}