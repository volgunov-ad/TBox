package vad.dashing.tbox

/** Single-button seat heat/vent tile (swipe switches heat vs vent control). */
const val FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY = "frontLeftSeatHeatVentSingleWidget"
const val FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY = "frontRightSeatHeatVentSingleWidget"

/** Rear seats: heating only (no ventilation), one tile per side. */
const val REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY = "rearLeftSeatHeatWidget"
const val REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY = "rearRightSeatHeatWidget"

/** Values for [FloatingDashboardWidgetConfig.selectedVariant] on single seat heat/vent widgets. */
const val SEAT_HEAT_VENT_VARIANT_HEAT = 0
const val SEAT_HEAT_VENT_VARIANT_VENT = 1

fun isSeatHeatVentSingleWidgetDataKey(dataKey: String): Boolean =
    dataKey == FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY ||
        dataKey == FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
