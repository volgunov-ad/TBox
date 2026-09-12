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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_CURRENT
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_PERSISTENT
import vad.dashing.tbox.WIDGET_TITLE_POSITION_BOTTOM
import vad.dashing.tbox.normalizeTripWidgetSource
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.trip.TripMetricFormatter
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.trip.formatTripDurationHuman
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Single-metric trip tile: one big value (like speed), sourced from current or daily trip.
 */
@Composable
fun DashboardTripMetricWidgetItem(
    appDataViewModel: AppDataViewModel,
    tripWidgetSource: Int = TRIP_WIDGET_SOURCE_CURRENT,
    tripMetricFieldId: String = TripMetricFormatter.normalizeFieldId(""),
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
    val context = LocalContext.current
    val activeTrip by appDataViewModel.activeTrip.collectAsStateWithLifecycle()
    val trips by appDataViewModel.trips.collectAsStateWithLifecycle()
    val source = normalizeTripWidgetSource(tripWidgetSource)
    val field = TripMetricFormatter.fieldFromIdOrDefault(tripMetricFieldId)
    val persistentTrip = remember(trips) { trips.firstOrNull { it.isPersistent } }
    val displayTrip = if (source == TRIP_WIDGET_SOURCE_PERSISTENT) {
        persistentTrip
    } else {
        activeTrip ?: TripRepository.latestFinishedTrip(trips)
    }
    val dateFmt = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    }
    val noData = stringResource(R.string.value_no_data)
    val fieldLabel = stringResource(field.labelRes)
    val defaultTitle = stringResource(R.string.data_title_trip_metric_widget)
    val titleText = titleOverride.trim().ifBlank { fieldLabel.ifBlank { defaultTitle } }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        val formatted = displayTrip?.let { trip ->
            TripMetricFormatter.format(
                field = field,
                trip = trip,
                dateFmt = dateFmt,
                formatDurationMs = { ms -> formatTripDurationHuman(context, ms) },
                noData = noData,
                valueAccuracy = valueAccuracy,
            )
        }
        val valueText = formatted?.value ?: noData
        val unitText = if (showUnit && formatted?.unitRes != null) {
            stringResource(formatted.unitRes)
        } else {
            ""
        }

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
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
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
