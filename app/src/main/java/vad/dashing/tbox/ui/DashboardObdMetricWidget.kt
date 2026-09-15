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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.WIDGET_TITLE_POSITION_BOTTOM
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.obd.ObdPid
import vad.dashing.tbox.obd.ObdRepository
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Single OBD metric tile: one large value from ELM327 Mode 01 (or ATRV adapter voltage).
 */
@Composable
fun DashboardObdMetricWidgetItem(
    obdPidId: String = ObdPid.RPM.id,
    valueAccuracy: Int? = null,
    showTitle: Boolean = true,
    titleOverride: String = "",
    showUnit: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
) {
    val pid = remember(obdPidId) { ObdPid.fromIdOrDefault(obdPidId) }
    val values by ObdRepository.values.collectAsStateWithLifecycle()
    val connected by ObdRepository.connected.collectAsStateWithLifecycle()
    val noData = stringResource(R.string.value_no_data)
    val fieldLabel = stringResource(pid.labelRes)
    val defaultTitle = stringResource(R.string.data_title_obd_metric_widget)
    val titleText = titleOverride.trim().ifBlank { fieldLabel.ifBlank { defaultTitle } }
    val raw = values[pid.id]
    val decimals = (valueAccuracy ?: pid.defaultAccuracy).coerceIn(0, 2)
    val valueText = when {
        !connected || raw == null -> noData
        else -> formatObdNumber(raw, decimals)
    }
    val unitText = if (showUnit && pid.unitRes != null && raw != null && connected) {
        stringResource(pid.unitRes)
    } else {
        ""
    }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = widgetColumnHorizontalAlignment(LocalWidgetTextAlign.current),
        ) {
            val titleAtBottom =
                normalizeWidgetTitlePosition(LocalWidgetTitlePosition.current) ==
                    WIDGET_TITLE_POSITION_BOTTOM
            if (showTitle && !titleAtBottom) {
                val titleStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE,
                    forWidgetTitle = true,
                )
                Text(
                    text = titleText,
                    style = titleStyle,
                    color = resolvedTextColor,
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }

            val valueStyle = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = TextType.VALUE,
            )
            Text(
                text = if (unitText.isNotEmpty()) {
                    "$valueText\u2009${unitText.replace("/", "\u2060/\u2060")}"
                } else {
                    valueText
                },
                style = valueStyle,
                color = resolvedTextColor,
                textAlign = LocalWidgetTextAlign.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically),
            )

            if (showTitle && titleAtBottom) {
                val titleStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE,
                    forWidgetTitle = true,
                )
                Text(
                    text = titleText,
                    style = titleStyle,
                    color = resolvedTextColor,
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

private fun formatObdNumber(value: Double, decimals: Int): String {
    if (decimals <= 0) return value.roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToLong() / factor
    return String.format(Locale.US, "%.${decimals}f", rounded)
}
