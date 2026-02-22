package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DEFAULT_WIDGET_SCALE
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.normalizeWidgetScale
import kotlinx.coroutines.delay
import vad.dashing.tbox.DashboardManager
import kotlin.math.abs

val LocalWidgetTextScale = staticCompositionLocalOf { DEFAULT_WIDGET_SCALE }

@Composable
fun DashboardWidgetItem(
    widget: DashboardWidget,
    dataProvider: DataProvider,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    dashboardManager: DashboardManager,
    dashboardChart: Boolean,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    title: Boolean = true,
    units: Boolean = true,
    backgroundTransparent: Boolean = false,
    textColor: Color? = null
) {
    val onlyText: Boolean

    val widgetHistory by dashboardManager.getWidgetHistoryFlow(widget.id).collectAsState()

    val valueFlow = remember(widget.dataKey) {
        dataProvider.getValueFlow(widget.dataKey)
    }
    val valueString by valueFlow.collectAsStateWithLifecycle()

    if (widget.dataKey == "restartTbox") {
        onlyText = true
    } else {
        val currentValue by rememberUpdatedState(valueString.replace(",", ".").toFloatOrNull())

        LaunchedEffect(widget.id) {
            while (true) {
                delay(1000L)
                if (dashboardChart) {
                    currentValue?.let {
                        dashboardManager.updateWidgetHistory(widget.id, it)
                    }
                }
            }
        }
        onlyText = false
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick
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
            if (!widgetHistory.checkValues() && dashboardChart && !onlyText) {
                HistoryLineChart(
                    values = widgetHistory,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.3f)
                )
            }

            val availableHeight = maxHeight
            val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title && !onlyText) {
                    Text(
                        text = widget.title,
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ),
                        fontWeight = FontWeight.Medium,
                        color = resolvedTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ) * 1.3f,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }

                Text(
                    text = "$valueString\u2009${if (units && !onlyText) widget.unit.replace("/", "\u2060/\u2060") else ""}",
                    fontSize = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = if (widget.dataKey == "restartTbox") {
                            TextType.TITLE
                        } else {
                            TextType.VALUE
                        }
                    ),
                    fontWeight = FontWeight.Medium,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = TextType.VALUE
                    ) * 1.3f,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(if (title) 2f else 3f)
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                )

                /*if (units && !onlyText) {
                    Text(
                        text = widget.unit,
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.UNIT
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }*/
            }
        }
    }
}

@Composable
fun calculateResponsiveFontSize(
    containerHeight: Dp,
    textType: TextType = TextType.VALUE
): TextUnit {
    val heightInDp = containerHeight.value
    val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)

    val baseSize = when (textType) {
        TextType.TITLE -> {
            when {
                heightInDp < 20 -> 8.sp
                heightInDp < 40 -> 10.sp
                heightInDp < 60 -> 12.sp
                heightInDp < 80 -> 16.sp
                heightInDp < 100 -> 20.sp
                heightInDp < 120 -> 24.sp
                heightInDp < 150 -> 28.sp
                else -> 32.sp
            }
        }
        TextType.VALUE -> {
            when {
                heightInDp < 20 -> 10.sp
                heightInDp < 40 -> 14.sp
                heightInDp < 60 -> 18.sp
                heightInDp < 80 -> 24.sp
                heightInDp < 100 -> 30.sp
                heightInDp < 120 -> 36.sp
                heightInDp < 150 -> 42.sp
                else -> 48.sp
            }
        }
        TextType.UNIT -> {
            when {
                heightInDp < 20 -> 6.sp
                heightInDp < 40 -> 8.sp
                heightInDp < 60 -> 10.sp
                heightInDp < 80 -> 14.sp
                heightInDp < 100 -> 18.sp
                heightInDp < 120 -> 22.sp
                heightInDp < 150 -> 26.sp
                else -> 30.sp
            }
        }
    }
    return baseSize * textScale
}

enum class TextType {
    TITLE, VALUE, UNIT
}

@Composable
private fun HistoryLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Canvas(modifier = modifier.padding(2.dp)) {
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