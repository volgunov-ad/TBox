package vad.dashing.tbox.trip

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.R

/**
 * Identifiers for rows on the configurable trip dashboard tile.
 * [id] is persisted in settings JSON.
 */
enum class ActiveTripCustomWidgetField(
    val id: String,
    @StringRes val labelRes: Int,
) {
    START_TIME("start_time", R.string.trips_start_time),
    END_TIME("end_time", R.string.trips_end_time),
    ODOMETER_START("odometer_start", R.string.trips_odometer_start),
    DISTANCE("distance", R.string.trips_distance),
    MOVING_TIME("moving_time", R.string.trips_moving_time),
    IDLE_TIME("idle_time", R.string.trips_idle_time),
    ENGINE_RUNNING_TIME("engine_running_time", R.string.trips_engine_running_time),
    PARKING_TIME("parking_time", R.string.trips_parking_time),
    TOTAL_TIME("total_time", R.string.trips_total_time),
    ENGINE_START_COUNT("engine_start_count", R.string.trips_engine_start_count),
    MAX_SPEED("max_speed", R.string.trips_max_speed),
    MAX_ENGINE_TEMP("max_engine_temp", R.string.trips_max_engine_temp),
    MAX_GEARBOX_TEMP("max_gearbox_temp", R.string.trips_max_gearbox_temp),
    MIN_OUTSIDE_TEMP("min_outside_temp", R.string.trips_min_outside_temp),
    MAX_OUTSIDE_TEMP("max_outside_temp", R.string.trips_max_outside_temp),
    AVG_SPEED_MOVING("avg_speed_moving", R.string.trips_avg_speed_moving),
    AVG_SPEED_TRIP("avg_speed_trip", R.string.trips_avg_speed_trip),
    FUEL_USED("fuel_used", R.string.trips_fuel_used),
    FUEL_CONSUMPTION("fuel_consumption", R.string.trips_fuel_consumption_l_100km),
    FUEL_REFUELED("fuel_refueled", R.string.trips_fuel_refueled),
    FUEL_REFUELED_COST("fuel_refueled_cost", R.string.trips_fuel_refueled_cost),
    REFUEL_COUNT("refuel_count", R.string.trips_refuel_count),
    ;

    companion object {
        private val byId: Map<String, ActiveTripCustomWidgetField> =
            entries.associateBy { it.id }

        fun fromId(id: String): ActiveTripCustomWidgetField? = byId[id]
    }
}

data class ActiveTripCustomWidgetLayout(
    val rows: List<Row>,
) {
    data class Row(
        val field: ActiveTripCustomWidgetField,
        val enabled: Boolean,
    )

    companion object {
        fun default(): ActiveTripCustomWidgetLayout =
            ActiveTripCustomWidgetLayout(
                ActiveTripCustomWidgetField.entries.map { Row(it, enabled = true) }
            )

        /**
         * Default layout for the simplified trip tile: same fields and order as the historical
         * hard-coded [vad.dashing.tbox.ui.DashboardActiveTripWidgetItem] simplified branch.
         */
        fun defaultSimplified(): ActiveTripCustomWidgetLayout {
            val enabledInOrder = listOf(
                ActiveTripCustomWidgetField.START_TIME,
                ActiveTripCustomWidgetField.END_TIME,
                ActiveTripCustomWidgetField.ODOMETER_START,
                ActiveTripCustomWidgetField.DISTANCE,
                ActiveTripCustomWidgetField.MOVING_TIME,
                ActiveTripCustomWidgetField.IDLE_TIME,
                ActiveTripCustomWidgetField.TOTAL_TIME,
                ActiveTripCustomWidgetField.AVG_SPEED_MOVING,
                ActiveTripCustomWidgetField.AVG_SPEED_TRIP,
                ActiveTripCustomWidgetField.FUEL_USED,
                ActiveTripCustomWidgetField.FUEL_CONSUMPTION,
                ActiveTripCustomWidgetField.FUEL_REFUELED,
            )
            val enabledSet = enabledInOrder.toSet()
            val disabled =
                ActiveTripCustomWidgetField.entries.filter { it !in enabledSet }
            return ActiveTripCustomWidgetLayout(
                enabledInOrder.map { Row(it, enabled = true) } +
                    disabled.map { Row(it, enabled = false) },
            )
        }

        fun parse(raw: String): ActiveTripCustomWidgetLayout =
            parseWithDefaults(raw, blankDefault = default(), missingFieldEnabled = true)

        fun parseSimple(raw: String): ActiveTripCustomWidgetLayout =
            parseWithDefaults(
                raw,
                blankDefault = defaultSimplified(),
                missingFieldEnabled = false,
            )

        private fun parseWithDefaults(
            raw: String,
            blankDefault: ActiveTripCustomWidgetLayout,
            missingFieldEnabled: Boolean,
        ): ActiveTripCustomWidgetLayout {
            if (raw.isBlank()) return blankDefault
            return try {
                val root = JSONObject(raw)
                val arr = root.getJSONArray("rows")
                val seen = mutableSetOf<String>()
                val parsed = mutableListOf<Row>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val field = ActiveTripCustomWidgetField.fromId(id) ?: continue
                    if (id in seen) continue
                    seen.add(id)
                    parsed.add(Row(field, o.optBoolean("enabled", true)))
                }
                for (f in ActiveTripCustomWidgetField.entries) {
                    if (f.id !in seen) {
                        parsed.add(Row(f, enabled = missingFieldEnabled))
                    }
                }
                ActiveTripCustomWidgetLayout(parsed)
            } catch (_: Exception) {
                blankDefault
            }
        }

        fun serialize(layout: ActiveTripCustomWidgetLayout): String {
            val arr = JSONArray()
            for (r in layout.rows) {
                arr.put(
                    JSONObject()
                        .put("id", r.field.id)
                        .put("enabled", r.enabled)
                )
            }
            return JSONObject().put("rows", arr).toString()
        }

        fun enabledFieldsInOrder(layout: ActiveTripCustomWidgetLayout): List<ActiveTripCustomWidgetField> =
            layout.rows.filter { it.enabled }.map { it.field }
    }
}
