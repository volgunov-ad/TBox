package vad.dashing.tbox.trip

import androidx.annotation.StringRes
import vad.dashing.tbox.R
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Formats a single [ActiveTripCustomWidgetField] for trip tiles (multi-row and single-metric).
 */
object TripMetricFormatter {

    data class Formatted(
        val value: String,
        @StringRes val unitRes: Int?,
    )

    fun normalizeFieldId(raw: String): String =
        ActiveTripCustomWidgetField.fromId(raw)?.id
            ?: ActiveTripCustomWidgetField.DISTANCE.id

    fun fieldFromIdOrDefault(raw: String): ActiveTripCustomWidgetField =
        ActiveTripCustomWidgetField.fromId(raw) ?: ActiveTripCustomWidgetField.DISTANCE

    fun format(
        field: ActiveTripCustomWidgetField,
        trip: TripRecord,
        dateFmt: SimpleDateFormat,
        formatDurationMs: (Long) -> String,
        noData: String,
        valueAccuracy: Int? = null,
    ): Formatted {
        val avgT = TripRepository.averageSpeedTripKmH(trip)
        val avgFuel = TripRepository.averageFuelConsumptionLitersPer100Km(trip)
        val avgM = TripRepository.averageSpeedMovingKmH(trip)
        return when (field) {
            ActiveTripCustomWidgetField.START_TIME ->
                Formatted(dateFmt.format(Date(trip.startTimeEpochMs)), null)
            ActiveTripCustomWidgetField.END_TIME -> {
                val end = trip.endTimeEpochMs
                if (end == null) Formatted(noData, null)
                else Formatted(dateFmt.format(Date(end)), null)
            }
            ActiveTripCustomWidgetField.ODOMETER_START -> {
                val odo = trip.odometerStartKm
                if (odo == null) Formatted(noData, null)
                else Formatted(valueToString(odo, valueAccuracy ?: 0), R.string.unit_km)
            }
            ActiveTripCustomWidgetField.DISTANCE ->
                Formatted(
                    valueToString(trip.distanceKm, valueAccuracy ?: TripDistanceFormat.DECIMALS),
                    R.string.unit_km,
                )
            ActiveTripCustomWidgetField.MOVING_TIME ->
                Formatted(formatDurationMs(trip.movingTimeMs), null)
            ActiveTripCustomWidgetField.IDLE_TIME ->
                Formatted(formatDurationMs(trip.idleTimeMs), null)
            ActiveTripCustomWidgetField.ENGINE_RUNNING_TIME ->
                Formatted(formatDurationMs(trip.movingTimeMs + trip.idleTimeMs), null)
            ActiveTripCustomWidgetField.PARKING_TIME ->
                Formatted(formatDurationMs(trip.parkingTimeMs), null)
            ActiveTripCustomWidgetField.TOTAL_TIME ->
                Formatted(
                    formatDurationMs(trip.movingTimeMs + trip.idleTimeMs + trip.parkingTimeMs),
                    null,
                )
            ActiveTripCustomWidgetField.ENGINE_START_COUNT ->
                Formatted(valueToString(trip.engineStartCount), null)
            ActiveTripCustomWidgetField.MAX_SPEED ->
                Formatted(
                    valueToString(trip.maxSpeed, valueAccuracy ?: 1),
                    R.string.unit_kmh,
                )
            ActiveTripCustomWidgetField.MAX_ENGINE_TEMP -> {
                val t = trip.maxEngineTemp
                if (t == null) Formatted(noData, null)
                else Formatted(valueToString(t, valueAccuracy ?: 1), R.string.unit_celsius)
            }
            ActiveTripCustomWidgetField.MAX_GEARBOX_TEMP -> {
                val gb = trip.maxGearboxOilTemp
                if (gb == null) Formatted(noData, null)
                else Formatted(valueToString(gb), R.string.unit_celsius)
            }
            ActiveTripCustomWidgetField.MIN_OUTSIDE_TEMP -> {
                val t = trip.minOutsideTemp
                if (t == null) Formatted(noData, null)
                else Formatted(valueToString(t, valueAccuracy ?: 1), R.string.unit_celsius)
            }
            ActiveTripCustomWidgetField.MAX_OUTSIDE_TEMP -> {
                val t = trip.maxOutsideTemp
                if (t == null) Formatted(noData, null)
                else Formatted(valueToString(t, valueAccuracy ?: 1), R.string.unit_celsius)
            }
            ActiveTripCustomWidgetField.AVG_SPEED_MOVING -> {
                if (avgM == null) Formatted(noData, null)
                else Formatted(valueToString(avgM, valueAccuracy ?: 1), R.string.unit_kmh)
            }
            ActiveTripCustomWidgetField.AVG_SPEED_TRIP -> {
                if (avgT == null) Formatted(noData, null)
                else Formatted(valueToString(avgT, valueAccuracy ?: 1), R.string.unit_kmh)
            }
            ActiveTripCustomWidgetField.FUEL_USED ->
                Formatted(
                    valueToString(trip.fuelConsumedLiters, valueAccuracy ?: 1),
                    R.string.unit_liter,
                )
            ActiveTripCustomWidgetField.FUEL_CONSUMPTION -> {
                if (avgFuel == null) Formatted(noData, null)
                else Formatted(valueToString(avgFuel, valueAccuracy ?: 1), R.string.unit_l_100km)
            }
            ActiveTripCustomWidgetField.FUEL_REFUELED ->
                Formatted(
                    valueToString(trip.fuelRefueledLiters, valueAccuracy ?: 1),
                    R.string.unit_liter,
                )
            ActiveTripCustomWidgetField.FUEL_REFUELED_COST ->
                Formatted(
                    valueToString(trip.fuelRefueledCostRub, valueAccuracy ?: 2),
                    R.string.unit_ruble,
                )
            ActiveTripCustomWidgetField.REFUEL_COUNT ->
                Formatted(valueToString(trip.refuelCount), null)
        }
    }
}
