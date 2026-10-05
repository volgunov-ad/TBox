package vad.dashing.tbox.obd

/**
 * One OBD readiness / monitor bit pair from Mode 01 PID `01` or `41`.
 */
data class ObdMonitorItem(
    /** Stable id for strings / export, e.g. `misfire`, `catalyst`. */
    val id: String,
    val available: Boolean,
    /** Meaningful when [available]; true = test complete (or not required this cycle). */
    val complete: Boolean,
) {
    val incomplete: Boolean get() = available && !complete
}

/**
 * Decoded Mode 01 PID `01` (since DTCs cleared) or `41` (this drive cycle).
 */
data class ObdMonitorStatus(
    val milOn: Boolean,
    val confirmedDtcCount: Int,
    /** `true` = spark ignition layout; `false` = compression (diesel) layout. */
    val sparkIgnition: Boolean,
    val monitors: List<ObdMonitorItem>,
) {
    companion object {
        const val SPARK_MISFIRE = "misfire"
        const val SPARK_FUEL = "fuel_system"
        const val SPARK_COMPONENTS = "components"
        const val SPARK_CATALYST = "catalyst"
        const val SPARK_HEATED_CATALYST = "heated_catalyst"
        const val SPARK_EVAP = "evap"
        const val SPARK_SECONDARY_AIR = "secondary_air"
        const val SPARK_AC_REFRIGERANT = "ac_refrigerant"
        const val SPARK_O2 = "oxygen_sensor"
        const val SPARK_O2_HEATER = "oxygen_sensor_heater"
        const val SPARK_EGR = "egr"

        const val COMP_NMHC = "nmhc_catalyst"
        const val COMP_NOX = "nox_scr"
        const val COMP_BOOST = "boost_pressure"
        const val COMP_EXHAUST_SENSOR = "exhaust_gas_sensor"
        const val COMP_PM_FILTER = "pm_filter"
        const val COMP_EGR_VVT = "egr_vvt"
    }
}
