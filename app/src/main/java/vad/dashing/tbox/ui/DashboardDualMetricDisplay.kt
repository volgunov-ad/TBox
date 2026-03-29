package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/** Separator between two inline metrics (em spaces). */
internal const val DUAL_METRIC_INLINE_SEPARATOR = "\u2003"

/**
 * Two dashboard metrics: either stacked (equal vertical weight) or one line with [DUAL_METRIC_INLINE_SEPARATOR].
 * Parent [Column] should pass [modifier] with [Modifier.weight] so this block shares height with optional title.
 */
@Composable
internal fun DashboardDualMetricRows(
    firstLine: String,
    secondLine: String,
    singleLineDualMetrics: Boolean,
    availableHeight: Dp,
    resolvedTextColor: Color,
    modifier: Modifier = Modifier
) {
    val valueFont = calculateResponsiveFontSize(
        containerHeight = availableHeight,
        textType = TextType.VALUE
    )
    val rowModifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(Alignment.CenterVertically)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (singleLineDualMetrics) {
            Text(
                text = "$firstLine$DUAL_METRIC_INLINE_SEPARATOR$secondLine",
                modifier = rowModifier.weight(1f),
                fontSize = valueFont,
                fontWeight = FontWeight.Medium,
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = firstLine,
                modifier = rowModifier.weight(1f),
                fontSize = valueFont,
                fontWeight = FontWeight.Medium,
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondLine,
                modifier = rowModifier.weight(1f),
                fontSize = valueFont,
                fontWeight = FontWeight.Medium,
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
