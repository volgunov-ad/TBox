package vad.dashing.tbox

/** Head-unit accelerator percent; text turns red when the brake pedal is pressed. */
const val GAS_BRAKE_WIDGET_DATA_KEY = "gasBrakeWidget"

fun isGasBrakeWidgetDataKey(dataKey: String): Boolean = dataKey == GAS_BRAKE_WIDGET_DATA_KEY
