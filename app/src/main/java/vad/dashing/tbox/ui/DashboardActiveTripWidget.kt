package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY
import vad.dashing.tbox.ACTIVE_TRIP_WIDGET_MINI_DATA_KEY
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.formatTripDurationHuman
import vad.dashing.tbox.R
import vad.dashing.tbox.TripRepository
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardActiveTripWidgetItem(
    widget: DashboardWidget,
    appDataViewModel: AppDataViewModel,
    showTitle: Boolean = false,
    titleOverride: String = "",
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val context = LocalContext.current
    val activeTrip by appDataViewModel.activeTrip.collectAsStateWithLifecycle()
    val trips by appDataViewModel.trips.collectAsStateWithLifecycle()
    val displayTrip = activeTrip ?: TripRepository.latestFinishedTrip(trips)
    val showingLastFinishedTrip = displayTrip != null && !displayTrip.isActive
    val dateFmt = rememberTripDateFormat()
    val defaultActiveTitle = stringResource(R.string.trips_active_trip)
    val defaultLastTitle = stringResource(R.string.trips_last_trip_widget_title)
    val titleText = titleOverride.trim().ifBlank {
        if (showingLastFinishedTrip) defaultLastTitle else defaultActiveTitle
    }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        val titleFont = calculateResponsiveFontSize(
            containerHeight = availableHeight,
            textType = TextType.TITLE
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (showTitle) {
                Text(
                    text = titleText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = titleFont,
                    lineHeight = titleFont * 1.3f,
                    fontWeight = FontWeight.Medium,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (displayTrip == null) {
                Text(
                    text = stringResource(R.string.trips_no_active),
                    fontSize = titleFont,
                    lineHeight = titleFont * 1.3f,
                    fontWeight = FontWeight.Medium,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Start,
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val t = displayTrip ?: return@DashboardWidgetScaffold
                val rowFont = titleFont
                val simplified = widget.dataKey == ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY
                val mini = widget.dataKey == ACTIVE_TRIP_WIDGET_MINI_DATA_KEY
                val avgT = TripRepository.averageSpeedTripKmH(t)
                val avgFuel = TripRepository.averageFuelConsumptionLitersPer100Km(t)
                if (mini) {
                    StatusRow(
                        label = stringResource(R.string.trips_distance),
                        value = valueToString(t.distanceKm, 0),
                        unit = stringResource(R.string.unit_km),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_active_trip_mini_enroute_label),
                        value = formatTripDurationHuman(
                            context,
                            t.movingTimeMs + t.idleTimeMs,
                        ),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_consumption_l_100km),
                        value = avgFuel?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgFuel != null) stringResource(R.string.unit_l_100km) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_used),
                        value = valueToString(t.fuelConsumedLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                } else if (simplified) {
                    StatusRow(
                        label = stringResource(R.string.trips_start_time),
                        value = dateFmt.format(Date(t.startTimeEpochMs)),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    t.endTimeEpochMs?.let { endMs ->
                        StatusRow(
                            label = stringResource(R.string.trips_end_time),
                            value = dateFmt.format(Date(endMs)),
                            unit = "",
                            fontSize = rowFont,
                            color = resolvedTextColor
                        )
                    }
                    StatusRow(
                        label = stringResource(R.string.trips_odometer_start),
                        value = t.odometerStartKm?.let { valueToString(it, 0) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.odometerStartKm != null) stringResource(R.string.unit_km) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_distance),
                        value = valueToString(t.distanceKm, 0),
                        unit = stringResource(R.string.unit_km),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_moving_time),
                        value = formatTripDurationHuman(context, t.movingTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_idle_time),
                        value = formatTripDurationHuman(context, t.idleTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_total_time),
                        value = formatTripDurationHuman(
                            context,
                            t.movingTimeMs + t.idleTimeMs + t.parkingTimeMs
                        ),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    val avgM = TripRepository.averageSpeedMovingKmH(t)
                    StatusRow(
                        label = stringResource(R.string.trips_avg_speed_moving),
                        value = avgM?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgM != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_avg_speed_trip),
                        value = avgT?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgT != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_used),
                        value = valueToString(t.fuelConsumedLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_consumption_l_100km),
                        value = avgFuel?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgFuel != null) stringResource(R.string.unit_l_100km) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_refueled),
                        value = valueToString(t.fuelRefueledLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                } else {
                    StatusRow(
                        label = stringResource(R.string.trips_start_time),
                        value = dateFmt.format(Date(t.startTimeEpochMs)),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    t.endTimeEpochMs?.let { endMs ->
                        StatusRow(
                            label = stringResource(R.string.trips_end_time),
                            value = dateFmt.format(Date(endMs)),
                            unit = "",
                            fontSize = rowFont,
                            color = resolvedTextColor
                        )
                    }
                    StatusRow(
                        label = stringResource(R.string.trips_odometer_start),
                        value = t.odometerStartKm?.let { valueToString(it, 0) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.odometerStartKm != null) stringResource(R.string.unit_km) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_distance),
                        value = valueToString(t.distanceKm, 0),
                        unit = stringResource(R.string.unit_km),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_moving_time),
                        value = formatTripDurationHuman(context, t.movingTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_idle_time),
                        value = formatTripDurationHuman(context, t.idleTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_parking_time),
                        value = formatTripDurationHuman(context, t.parkingTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_total_time),
                        value = formatTripDurationHuman(
                            context,
                            t.movingTimeMs + t.idleTimeMs + t.parkingTimeMs
                        ),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_engine_start_count),
                        value = valueToString(t.engineStartCount),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_max_speed),
                        value = valueToString(t.maxSpeed, 1),
                        unit = stringResource(R.string.unit_kmh),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_max_engine_temp),
                        value = t.maxEngineTemp?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (t.maxEngineTemp != null) stringResource(R.string.unit_celsius) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    t.maxGearboxOilTemp?.let { gb ->
                        StatusRow(
                            label = stringResource(R.string.trips_max_gearbox_temp),
                            value = valueToString(gb),
                            unit = stringResource(R.string.unit_celsius),
                            fontSize = rowFont,
                            color = resolvedTextColor
                        )
                    }
                    StatusRow(
                        label = stringResource(R.string.trips_min_outside_temp),
                        value = t.minOutsideTemp?.let { valueToString(it, 1) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.minOutsideTemp != null) stringResource(R.string.unit_celsius) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_max_outside_temp),
                        value = t.maxOutsideTemp?.let { valueToString(it, 1) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.maxOutsideTemp != null) stringResource(R.string.unit_celsius) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    val avgM = TripRepository.averageSpeedMovingKmH(t)
                    StatusRow(
                        label = stringResource(R.string.trips_avg_speed_moving),
                        value = avgM?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgM != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_avg_speed_trip),
                        value = avgT?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgT != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_used),
                        value = valueToString(t.fuelConsumedLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_consumption_l_100km),
                        value = avgFuel?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgFuel != null) stringResource(R.string.unit_l_100km) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_fuel_refueled),
                        value = valueToString(t.fuelRefueledLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    StatusRow(
                        label = stringResource(R.string.trips_refuel_count),
                        value = valueToString(t.refuelCount),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTripDateFormat() = androidx.compose.runtime.remember {
    SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
}

