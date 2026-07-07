package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.TextStyle
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
import vad.dashing.tbox.ui.theme.LocalTboxTextStyles
import vad.dashing.tbox.ui.theme.TboxWidgetTextRole
import vad.dashing.tbox.ui.theme.TboxWidgetTypography
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
    titleOverride: String = "",
    units: Boolean = true,
    dateTimeFormat: String = "",
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val onlyText: Boolean

    val widgetHistory by dashboardManager.getWidgetHistoryFlow(widget.id).collectAsState()

    val valueFlow = remember(widget.dataKey, widget.valueAccuracy, dateTimeFormat) {
        dataProvider.getValueFlow(
            key = widget.dataKey,
            accuracy = widget.valueAccuracy,
            dateTimeFormat = dateTimeFormat,
        )
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

    val displayTitle = titleOverride.trim().ifBlank { widget.title }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        if (!widgetHistory.checkValues() && dashboardChart && !onlyText) {
            HistoryLineChart(
                values = widgetHistory,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.3f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title && !onlyText) {
                val titleStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE,
                )
                Text(
                    text = displayTitle,
                    style = titleStyle,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }

            val valueTextType = if (widget.dataKey == "restartTbox") {
                TextType.TITLE
            } else {
                TextType.VALUE
            }
            val valueStyle = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = valueTextType,
            )
            Text(
                text = "$valueString\u2009${if (units && !onlyText) widget.unit.replace("/", "\u2060/\u2060") else ""}",
                style = valueStyle,
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
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

/** Title row aligned with dual-metric widgets (e.g. [DashboardFuelLevelWidgetItem]) when [showTitle] is true. */
@Composable
fun ColumnScope.DashboardWidgetTitleRowIfVisible(
    showTitle: Boolean,
    titleText: String,
    availableHeight: Dp,
    resolvedTextColor: Color,
    layoutWeight: Float = 1f,
) {
    if (!showTitle) return
    val titleStyle = calculateResponsiveTextStyle(
        containerHeight = availableHeight * layoutWeight.coerceIn(0f, 1f),
        textType = TextType.TITLE,
    )
    Text(
        text = titleText,
        modifier = Modifier
            .weight(layoutWeight)
            .fillMaxWidth()
            .wrapContentHeight(Alignment.CenterVertically),
        style = titleStyle,
        color = resolvedTextColor,
        textAlign = TextAlign.Center,
        maxLines = 2,
        softWrap = true,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun calculateResponsiveTextStyle(
    containerHeight: Dp,
    textType: TextType = TextType.VALUE,
): TextStyle {
    val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)
    val role = textType.toWidgetRole()
    val styles = LocalTboxTextStyles.current
    val baseStyle = when (role) {
        TboxWidgetTextRole.TITLE -> styles.WidgetTitle
        TboxWidgetTextRole.VALUE -> styles.WidgetValue
        TboxWidgetTextRole.UNIT -> styles.WidgetUnit
    }
    return TboxWidgetTypography.textStyleForHeight(
        containerHeightDp = containerHeight.value,
        role = role,
        baseStyle = baseStyle,
        textScale = textScale,
    )
}

@Composable
fun calculateResponsiveFontSize(
    containerHeight: Dp,
    textType: TextType = TextType.VALUE,
): TextUnit = calculateResponsiveTextStyle(containerHeight, textType).fontSize

enum class TextType {
    TITLE, VALUE, UNIT,
}

fun TextType.toWidgetRole(): TboxWidgetTextRole = when (this) {
    TextType.TITLE -> TboxWidgetTextRole.TITLE
    TextType.VALUE -> TboxWidgetTextRole.VALUE
    TextType.UNIT -> TboxWidgetTextRole.UNIT
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