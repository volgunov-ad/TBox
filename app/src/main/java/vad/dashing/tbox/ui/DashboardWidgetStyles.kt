package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.normalizeWidgetScale
import kotlin.math.min

const val DISPLAY_STYLE_GAUGE = "gauge"
const val DISPLAY_STYLE_BAR = "bar"
const val DISPLAY_STYLE_MINIMAL = "minimal"

@Composable
fun DashboardGaugeWidgetItem(
    widget: DashboardWidget,
    dataProvider: DataProvider,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    showTitle: Boolean = false,
    titleOverride: String = "",
    showUnit: Boolean = true
) {
    val valueFlow = remember(widget.dataKey) { dataProvider.getValueFlow(widget.dataKey) }
    val valueString by valueFlow.collectAsStateWithLifecycle()
    val numericValue = valueString.replace(",", ".").toFloatOrNull() ?: 0f
    val maxVal = widget.maxValue ?: 100f
    val fraction = (numericValue / maxVal).coerceIn(0f, 1f)
    val displayTitle = titleOverride.trim().ifBlank { widget.title }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, resolvedTextColor ->
        val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val strokeWidth = 8.dp.toPx()
                val radius = min(size.width, size.height) / 2f - strokeWidth
                val startAngle = 135f
                val sweepAngle = 270f

                drawArc(
                    color = resolvedTextColor.copy(alpha = 0.2f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(
                        size.width / 2f - radius,
                        size.height / 2f - radius
                    ),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = resolvedTextColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * fraction,
                    useCenter = false,
                    topLeft = Offset(
                        size.width / 2f - radius,
                        size.height / 2f - radius
                    ),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueString.ifBlank { "–" },
                    color = resolvedTextColor,
                    fontSize = (24 * textScale).sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (showUnit && widget.unit.isNotBlank()) {
                    Text(
                        text = widget.unit,
                        color = resolvedTextColor.copy(alpha = 0.6f),
                        fontSize = (12 * textScale).sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardBarWidgetItem(
    widget: DashboardWidget,
    dataProvider: DataProvider,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    showTitle: Boolean = false,
    titleOverride: String = "",
    showUnit: Boolean = true
) {
    val valueFlow = remember(widget.dataKey) { dataProvider.getValueFlow(widget.dataKey) }
    val valueString by valueFlow.collectAsStateWithLifecycle()
    val numericValue = valueString.replace(",", ".").toFloatOrNull() ?: 0f
    val maxVal = widget.maxValue ?: 100f
    val fraction = (numericValue / maxVal).coerceIn(0f, 1f)
    val displayTitle = titleOverride.trim().ifBlank { widget.title }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, resolvedTextColor ->
        val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showTitle && displayTitle.isNotBlank()) {
                Text(
                    text = displayTitle,
                    color = resolvedTextColor.copy(alpha = 0.6f),
                    fontSize = (10 * textScale).sp,
                    maxLines = 1
                )
            }
            Text(
                text = if (showUnit && widget.unit.isNotBlank()) {
                    "${valueString.ifBlank { "–" }} ${widget.unit}"
                } else {
                    valueString.ifBlank { "–" }
                },
                color = resolvedTextColor,
                fontSize = (18 * textScale).sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 2.dp)
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            ) {
                val barRadius = CornerRadius(size.height / 2f)
                drawRoundRect(
                    color = resolvedTextColor.copy(alpha = 0.15f),
                    cornerRadius = barRadius
                )
                drawRoundRect(
                    color = resolvedTextColor,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = barRadius
                )
            }
        }
    }
}

@Composable
fun DashboardMinimalWidgetItem(
    widget: DashboardWidget,
    dataProvider: DataProvider,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    showUnit: Boolean = true
) {
    val valueFlow = remember(widget.dataKey) { dataProvider.getValueFlow(widget.dataKey) }
    val valueString by valueFlow.collectAsStateWithLifecycle()

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, resolvedTextColor ->
        val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (showUnit && widget.unit.isNotBlank()) {
                    "${valueString.ifBlank { "–" }} ${widget.unit}"
                } else {
                    valueString.ifBlank { "–" }
                },
                color = resolvedTextColor,
                fontSize = (28 * textScale).sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
