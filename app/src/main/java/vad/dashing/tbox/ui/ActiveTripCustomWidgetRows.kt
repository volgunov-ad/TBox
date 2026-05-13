package vad.dashing.tbox.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import vad.dashing.tbox.R
import vad.dashing.tbox.trip.ActiveTripCustomWidgetField
import vad.dashing.tbox.trip.ActiveTripCustomWidgetLayout
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.trip.formatTripDurationHuman
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ActiveTripCustomWidgetRows(
    trip: TripRecord,
    layout: ActiveTripCustomWidgetLayout,
    rowFont: TextUnit,
    resolvedTextColor: Color,
    dateFmt: SimpleDateFormat,
    context: Context,
) {
    val avgT = TripRepository.averageSpeedTripKmH(trip)
    val avgFuel = TripRepository.averageFuelConsumptionLitersPer100Km(trip)
    val avgM = TripRepository.averageSpeedMovingKmH(trip)
    val noData = stringResource(R.string.value_no_data)

    for (row in layout.rows) {
        if (!row.enabled) continue
        when (row.field) {
            ActiveTripCustomWidgetField.START_TIME -> {
                StatusRow(
                    label = stringResource(R.string.trips_start_time),
                    value = dateFmt.format(Date(trip.startTimeEpochMs)),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.END_TIME -> {
                val end = trip.endTimeEpochMs ?: continue
                StatusRow(
                    label = stringResource(R.string.trips_end_time),
                    value = dateFmt.format(Date(end)),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.ODOMETER_START -> {
                StatusRow(
                    label = stringResource(R.string.trips_odometer_start),
                    value = trip.odometerStartKm?.let { valueToString(it, 0) } ?: noData,
                    unit = if (trip.odometerStartKm != null) stringResource(R.string.unit_km) else "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.DISTANCE -> {
                StatusRow(
                    label = stringResource(R.string.trips_distance),
                    value = valueToString(trip.distanceKm, 0),
                    unit = stringResource(R.string.unit_km),
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.MOVING_TIME -> {
                StatusRow(
                    label = stringResource(R.string.trips_moving_time),
                    value = formatTripDurationHuman(context, trip.movingTimeMs),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.IDLE_TIME -> {
                StatusRow(
                    label = stringResource(R.string.trips_idle_time),
                    value = formatTripDurationHuman(context, trip.idleTimeMs),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.ENGINE_RUNNING_TIME -> {
                StatusRow(
                    label = stringResource(R.string.trips_engine_running_time),
                    value = formatTripDurationHuman(
                        context,
                        trip.movingTimeMs + trip.idleTimeMs
                    ),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.PARKING_TIME -> {
                StatusRow(
                    label = stringResource(R.string.trips_parking_time),
                    value = formatTripDurationHuman(context, trip.parkingTimeMs),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.TOTAL_TIME -> {
                StatusRow(
                    label = stringResource(R.string.trips_total_time),
                    value = formatTripDurationHuman(
                        context,
                        trip.movingTimeMs + trip.idleTimeMs + trip.parkingTimeMs
                    ),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.ENGINE_START_COUNT -> {
                StatusRow(
                    label = stringResource(R.string.trips_engine_start_count),
                    value = valueToString(trip.engineStartCount),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.MAX_SPEED -> {
                StatusRow(
                    label = stringResource(R.string.trips_max_speed),
                    value = valueToString(trip.maxSpeed, 1),
                    unit = stringResource(R.string.unit_kmh),
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.MAX_ENGINE_TEMP -> {
                StatusRow(
                    label = stringResource(R.string.trips_max_engine_temp),
                    value = trip.maxEngineTemp?.let { valueToString(it, 1) } ?: noData,
                    unit = if (trip.maxEngineTemp != null) stringResource(R.string.unit_celsius) else "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.MAX_GEARBOX_TEMP -> {
                val gb = trip.maxGearboxOilTemp ?: continue
                StatusRow(
                    label = stringResource(R.string.trips_max_gearbox_temp),
                    value = valueToString(gb),
                    unit = stringResource(R.string.unit_celsius),
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.MIN_OUTSIDE_TEMP -> {
                StatusRow(
                    label = stringResource(R.string.trips_min_outside_temp),
                    value = trip.minOutsideTemp?.let { valueToString(it, 1) } ?: noData,
                    unit = if (trip.minOutsideTemp != null) stringResource(R.string.unit_celsius) else "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.MAX_OUTSIDE_TEMP -> {
                StatusRow(
                    label = stringResource(R.string.trips_max_outside_temp),
                    value = trip.maxOutsideTemp?.let { valueToString(it, 1) } ?: noData,
                    unit = if (trip.maxOutsideTemp != null) stringResource(R.string.unit_celsius) else "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.AVG_SPEED_MOVING -> {
                StatusRow(
                    label = stringResource(R.string.trips_avg_speed_moving),
                    value = avgM?.let { valueToString(it, 1) } ?: noData,
                    unit = if (avgM != null) stringResource(R.string.unit_kmh) else "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.AVG_SPEED_TRIP -> {
                StatusRow(
                    label = stringResource(R.string.trips_avg_speed_trip),
                    value = avgT?.let { valueToString(it, 1) } ?: noData,
                    unit = if (avgT != null) stringResource(R.string.unit_kmh) else "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.FUEL_USED -> {
                StatusRow(
                    label = stringResource(R.string.trips_fuel_used),
                    value = valueToString(trip.fuelConsumedLiters, 1),
                    unit = stringResource(R.string.unit_liter),
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.FUEL_CONSUMPTION -> {
                StatusRow(
                    label = stringResource(R.string.trips_fuel_consumption_l_100km),
                    value = avgFuel?.let { valueToString(it, 1) } ?: noData,
                    unit = if (avgFuel != null) stringResource(R.string.unit_l_100km) else "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.FUEL_REFUELED -> {
                StatusRow(
                    label = stringResource(R.string.trips_fuel_refueled),
                    value = valueToString(trip.fuelRefueledLiters, 1),
                    unit = stringResource(R.string.unit_liter),
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.FUEL_REFUELED_COST -> {
                StatusRow(
                    label = stringResource(R.string.trips_fuel_refueled_cost),
                    value = valueToString(trip.fuelRefueledCostRub, 2),
                    unit = stringResource(R.string.unit_ruble),
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
            ActiveTripCustomWidgetField.REFUEL_COUNT -> {
                StatusRow(
                    label = stringResource(R.string.trips_refuel_count),
                    value = valueToString(trip.refuelCount),
                    unit = "",
                    fontSize = rowFont,
                    color = resolvedTextColor
                )
            }
        }
    }
}
