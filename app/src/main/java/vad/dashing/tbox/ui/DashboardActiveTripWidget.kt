package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val context = LocalContext.current
    val trip by appDataViewModel.activeTrip.collectAsStateWithLifecycle()
    val dateFmt = rememberTripDateFormat()

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (trip == null) {
                Text(
                    text = stringResource(R.string.trips_no_active),
                    fontSize = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = TextType.VALUE
                    ),
                    lineHeight = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = TextType.VALUE
                    ) * 1.3f,
                    fontWeight = FontWeight.Medium,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Start,
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val t = trip ?: return@DashboardWidgetScaffold
                val rowFont = calculateResponsiveFontSize(
                    containerHeight = availableHeight,
                    textType = TextType.VALUE
                )
                val simplified = widget.dataKey == "activeTripWidgetSimple"
                if (simplified) {
                    ActiveTripRow(
                        label = stringResource(R.string.trips_odometer_start),
                        value = t.odometerStartKm?.let { valueToString(it, 0) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.odometerStartKm != null) stringResource(R.string.unit_km) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_distance),
                        value = valueToString(t.distanceKm, 0),
                        unit = stringResource(R.string.unit_km),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_moving_time),
                        value = formatTripDurationHuman(context, t.movingTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_idle_time),
                        value = formatTripDurationHuman(context, t.idleTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_total_time),
                        value = formatTripDurationHuman(
                            context,
                            t.movingTimeMs + t.idleTimeMs
                        ),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    val avgM = TripRepository.averageSpeedMovingKmH(t)
                    ActiveTripRow(
                        label = stringResource(R.string.trips_avg_speed_moving),
                        value = avgM?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgM != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    val avgT = TripRepository.averageSpeedTripKmH(t)
                    ActiveTripRow(
                        label = stringResource(R.string.trips_avg_speed_trip),
                        value = avgT?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgT != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_fuel_used),
                        value = valueToString(t.fuelConsumedLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_fuel_refueled),
                        value = valueToString(t.fuelRefueledLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                } else {
                    ActiveTripRow(
                        label = stringResource(R.string.trips_start_time),
                        value = dateFmt.format(Date(t.startTimeEpochMs)),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_odometer_start),
                        value = t.odometerStartKm?.let { valueToString(it, 0) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.odometerStartKm != null) stringResource(R.string.unit_km) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_distance),
                        value = valueToString(t.distanceKm, 0),
                        unit = stringResource(R.string.unit_km),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_moving_time),
                        value = formatTripDurationHuman(context, t.movingTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_idle_time),
                        value = formatTripDurationHuman(context, t.idleTimeMs),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_total_time),
                        value = formatTripDurationHuman(
                            context,
                            t.movingTimeMs + t.idleTimeMs
                        ),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_engine_start_count),
                        value = valueToString(t.engineStartCount),
                        unit = "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_max_speed),
                        value = valueToString(t.maxSpeed, 1),
                        unit = stringResource(R.string.unit_kmh),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_max_engine_temp),
                        value = t.maxEngineTemp?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (t.maxEngineTemp != null) stringResource(R.string.unit_celsius) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    t.maxGearboxOilTemp?.let { gb ->
                        ActiveTripRow(
                            label = stringResource(R.string.trips_max_gearbox_temp),
                            value = valueToString(gb),
                            unit = stringResource(R.string.unit_celsius),
                            fontSize = rowFont,
                            color = resolvedTextColor
                        )
                    }
                    ActiveTripRow(
                        label = stringResource(R.string.trips_min_outside_temp),
                        value = t.minOutsideTemp?.let { valueToString(it, 1) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.minOutsideTemp != null) stringResource(R.string.unit_celsius) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_max_outside_temp),
                        value = t.maxOutsideTemp?.let { valueToString(it, 1) }
                            ?: stringResource(R.string.value_no_data),
                        unit = if (t.maxOutsideTemp != null) stringResource(R.string.unit_celsius) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    val avgM = TripRepository.averageSpeedMovingKmH(t)
                    ActiveTripRow(
                        label = stringResource(R.string.trips_avg_speed_moving),
                        value = avgM?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgM != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    val avgT = TripRepository.averageSpeedTripKmH(t)
                    ActiveTripRow(
                        label = stringResource(R.string.trips_avg_speed_trip),
                        value = avgT?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data),
                        unit = if (avgT != null) stringResource(R.string.unit_kmh) else "",
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_fuel_used),
                        value = valueToString(t.fuelConsumedLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
                        label = stringResource(R.string.trips_fuel_refueled),
                        value = valueToString(t.fuelRefueledLiters, 1),
                        unit = stringResource(R.string.unit_liter),
                        fontSize = rowFont,
                        color = resolvedTextColor
                    )
                    ActiveTripRow(
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

@Composable
private fun ActiveTripRow(
    label: String,
    value: String,
    unit: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color
) {
    val valueWithUnit = if (unit.isNotEmpty()) "$value\u2009$unit" else value
    Text(
        text = "$label: $valueWithUnit",
        fontSize = fontSize,
        lineHeight = fontSize * 1.3f,
        fontWeight = FontWeight.Normal,
        color = color,
        maxLines = 4,
        softWrap = true,
        overflow = TextOverflow.Ellipsis
    )
}

