package vad.dashing.tbox.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TripMetricFormatterTest {

    private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun trip(
        distanceKm: Float = 100f,
        movingTimeMs: Long = 3_600_000L,
        idleTimeMs: Long = 1_800_000L,
        fuelConsumedLiters: Float = 8f,
    ) = TripRecord(
        startTimeEpochMs = 1_700_000_000_000L,
        endTimeEpochMs = null,
        distanceKm = distanceKm,
        movingTimeMs = movingTimeMs,
        idleTimeMs = idleTimeMs,
        fuelConsumedLiters = fuelConsumedLiters,
        maxSpeed = 120f,
    )

    @Test
    fun formatsDistanceAndAvgSpeeds() {
        val t = trip()
        val distance = TripMetricFormatter.format(
            field = ActiveTripCustomWidgetField.DISTANCE,
            trip = t,
            dateFmt = dateFmt,
            formatDurationMs = { it.toString() },
            noData = "—",
        )
        assertEquals("100.0", distance.value.replace(',', '.'))
        assertEquals(vad.dashing.tbox.R.string.unit_km, distance.unitRes)

        val avgMoving = TripMetricFormatter.format(
            field = ActiveTripCustomWidgetField.AVG_SPEED_MOVING,
            trip = t,
            dateFmt = dateFmt,
            formatDurationMs = { it.toString() },
            noData = "—",
        )
        assertEquals("100.0", avgMoving.value.replace(',', '.'))
        assertEquals(vad.dashing.tbox.R.string.unit_kmh, avgMoving.unitRes)

        val avgTrip = TripMetricFormatter.format(
            field = ActiveTripCustomWidgetField.AVG_SPEED_TRIP,
            trip = t,
            dateFmt = dateFmt,
            formatDurationMs = { it.toString() },
            noData = "—",
        )
        // 100 km / 1.5 h = 66.666… → 66.7 with 1 decimal
        assertEquals("66.7", avgTrip.value.replace(',', '.'))
    }

    @Test
    fun formatsDurationFieldsWithoutUnit() {
        val t = trip(movingTimeMs = 90_000L)
        val moving = TripMetricFormatter.format(
            field = ActiveTripCustomWidgetField.MOVING_TIME,
            trip = t,
            dateFmt = dateFmt,
            formatDurationMs = { ms -> "dur:$ms" },
            noData = "—",
        )
        assertEquals("dur:90000", moving.value)
        assertNull(moving.unitRes)
    }

    @Test
    fun endTimeAbsentShowsNoData() {
        val formatted = TripMetricFormatter.format(
            field = ActiveTripCustomWidgetField.END_TIME,
            trip = trip(),
            dateFmt = dateFmt,
            formatDurationMs = { it.toString() },
            noData = "n/a",
        )
        assertEquals("n/a", formatted.value)
        assertNull(formatted.unitRes)
    }

    @Test
    fun respectsValueAccuracyOverride() {
        val formatted = TripMetricFormatter.format(
            field = ActiveTripCustomWidgetField.DISTANCE,
            trip = trip(distanceKm = 12.34f),
            dateFmt = dateFmt,
            formatDurationMs = { it.toString() },
            noData = "—",
            valueAccuracy = 0,
        )
        assertEquals("12", formatted.value)
    }
}
