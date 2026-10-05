package vad.dashing.tbox.obd

import androidx.annotation.StringRes
import vad.dashing.tbox.R

/**
 * Mode 01 live PIDs plus adapter voltage ([ADAPTER_VOLTAGE] via ATRV).
 * [id] is persisted in widget JSON as `obdPidId`.
 */
enum class ObdPid(
    val id: String,
    /** Mode 01 PID byte, or null for adapter-only commands (ATRV). */
    val mode01Pid: Int?,
    @StringRes val labelRes: Int,
    @StringRes val unitRes: Int?,
    val defaultAccuracy: Int,
) {
    ENGINE_LOAD(
        id = "engine_load",
        mode01Pid = 0x04,
        labelRes = R.string.obd_pid_engine_load,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    COOLANT_TEMP(
        id = "coolant_temp",
        mode01Pid = 0x05,
        labelRes = R.string.obd_pid_coolant_temp,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    SHORT_FUEL_TRIM_B1(
        id = "stft_b1",
        mode01Pid = 0x06,
        labelRes = R.string.obd_pid_stft_b1,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 1,
    ),
    LONG_FUEL_TRIM_B1(
        id = "ltft_b1",
        mode01Pid = 0x07,
        labelRes = R.string.obd_pid_ltft_b1,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 1,
    ),
    SHORT_FUEL_TRIM_B2(
        id = "stft_b2",
        mode01Pid = 0x08,
        labelRes = R.string.obd_pid_stft_b2,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 1,
    ),
    LONG_FUEL_TRIM_B2(
        id = "ltft_b2",
        mode01Pid = 0x09,
        labelRes = R.string.obd_pid_ltft_b2,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 1,
    ),
    FUEL_PRESSURE(
        id = "fuel_pressure",
        mode01Pid = 0x0A,
        labelRes = R.string.obd_pid_fuel_pressure,
        unitRes = R.string.unit_kpa,
        defaultAccuracy = 0,
    ),
    MAP(
        id = "map",
        mode01Pid = 0x0B,
        labelRes = R.string.obd_pid_map,
        unitRes = R.string.unit_kpa,
        defaultAccuracy = 0,
    ),
    RPM(
        id = "rpm",
        mode01Pid = 0x0C,
        labelRes = R.string.obd_pid_rpm,
        unitRes = R.string.unit_rpm,
        defaultAccuracy = 0,
    ),
    SPEED(
        id = "speed",
        mode01Pid = 0x0D,
        labelRes = R.string.obd_pid_speed,
        unitRes = R.string.unit_kmh,
        defaultAccuracy = 0,
    ),
    TIMING_ADVANCE(
        id = "timing_advance",
        mode01Pid = 0x0E,
        labelRes = R.string.obd_pid_timing_advance,
        unitRes = R.string.unit_degree,
        defaultAccuracy = 1,
    ),
    IAT(
        id = "iat",
        mode01Pid = 0x0F,
        labelRes = R.string.obd_pid_iat,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    MAF(
        id = "maf",
        mode01Pid = 0x10,
        labelRes = R.string.obd_pid_maf,
        unitRes = R.string.unit_gs,
        defaultAccuracy = 2,
    ),
    THROTTLE(
        id = "throttle",
        mode01Pid = 0x11,
        labelRes = R.string.obd_pid_throttle,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    O2_B1S1_VOLTAGE(
        id = "o2_b1s1",
        mode01Pid = 0x14,
        labelRes = R.string.obd_pid_o2_b1s1,
        unitRes = R.string.unit_volt,
        defaultAccuracy = 2,
    ),
    RUNTIME(
        id = "runtime",
        mode01Pid = 0x1F,
        labelRes = R.string.obd_pid_runtime,
        unitRes = R.string.unit_sec,
        defaultAccuracy = 0,
    ),
    DISTANCE_WITH_MIL(
        id = "distance_with_mil",
        mode01Pid = 0x21,
        labelRes = R.string.obd_pid_distance_with_mil,
        unitRes = R.string.unit_km,
        defaultAccuracy = 0,
    ),
    FUEL_RAIL_REL(
        id = "fuel_rail_rel",
        mode01Pid = 0x22,
        labelRes = R.string.obd_pid_fuel_rail_rel,
        unitRes = R.string.unit_kpa,
        defaultAccuracy = 1,
    ),
    FUEL_RAIL_GAUGE(
        id = "fuel_rail_gauge",
        mode01Pid = 0x23,
        labelRes = R.string.obd_pid_fuel_rail_gauge,
        unitRes = R.string.unit_kpa,
        defaultAccuracy = 0,
    ),
    COMMANDED_EGR(
        id = "commanded_egr",
        mode01Pid = 0x2C,
        labelRes = R.string.obd_pid_commanded_egr,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    EGR_ERROR(
        id = "egr_error",
        mode01Pid = 0x2D,
        labelRes = R.string.obd_pid_egr_error,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 1,
    ),
    EVAP_PURGE(
        id = "evap_purge",
        mode01Pid = 0x2E,
        labelRes = R.string.obd_pid_evap_purge,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    FUEL_LEVEL(
        id = "fuel_level",
        mode01Pid = 0x2F,
        labelRes = R.string.obd_pid_fuel_level,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    DISTANCE_SINCE_CODES_CLEARED(
        id = "distance_since_cleared",
        mode01Pid = 0x31,
        labelRes = R.string.obd_pid_distance_since_cleared,
        unitRes = R.string.unit_km,
        defaultAccuracy = 0,
    ),
    BARO(
        id = "baro",
        mode01Pid = 0x33,
        labelRes = R.string.obd_pid_baro,
        unitRes = R.string.unit_kpa,
        defaultAccuracy = 0,
    ),
    CATALYST_TEMP_B1S1(
        id = "cat_b1s1",
        mode01Pid = 0x3C,
        labelRes = R.string.obd_pid_cat_b1s1,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    CATALYST_TEMP_B1S2(
        id = "cat_b1s2",
        mode01Pid = 0x3D,
        labelRes = R.string.obd_pid_cat_b1s2,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    CATALYST_TEMP_B2S1(
        id = "cat_b2s1",
        mode01Pid = 0x3E,
        labelRes = R.string.obd_pid_cat_b2s1,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    CATALYST_TEMP_B2S2(
        id = "cat_b2s2",
        mode01Pid = 0x3F,
        labelRes = R.string.obd_pid_cat_b2s2,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    CONTROL_MODULE_VOLTAGE(
        id = "cm_voltage",
        mode01Pid = 0x42,
        labelRes = R.string.obd_pid_cm_voltage,
        unitRes = R.string.unit_volt,
        defaultAccuracy = 2,
    ),
    ABSOLUTE_LOAD(
        id = "absolute_load",
        mode01Pid = 0x43,
        labelRes = R.string.obd_pid_absolute_load,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    COMMANDED_EQ_RATIO(
        id = "eq_ratio",
        mode01Pid = 0x44,
        labelRes = R.string.obd_pid_eq_ratio,
        unitRes = R.string.unit_lambda,
        defaultAccuracy = 3,
    ),
    RELATIVE_THROTTLE(
        id = "relative_throttle",
        mode01Pid = 0x45,
        labelRes = R.string.obd_pid_relative_throttle,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    AMBIENT_AIR_TEMP(
        id = "ambient_air_temp",
        mode01Pid = 0x46,
        labelRes = R.string.obd_pid_ambient_air_temp,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    ABS_THROTTLE_B(
        id = "abs_throttle_b",
        mode01Pid = 0x47,
        labelRes = R.string.obd_pid_abs_throttle_b,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    ABS_THROTTLE_C(
        id = "abs_throttle_c",
        mode01Pid = 0x48,
        labelRes = R.string.obd_pid_abs_throttle_c,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    ACCEL_PEDAL(
        id = "accel_pedal",
        mode01Pid = 0x49,
        labelRes = R.string.obd_pid_accel_pedal,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    ACCEL_PEDAL_D(
        id = "accel_pedal_d",
        mode01Pid = 0x4A,
        labelRes = R.string.obd_pid_accel_pedal_d,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    ACCEL_PEDAL_E(
        id = "accel_pedal_e",
        mode01Pid = 0x4B,
        labelRes = R.string.obd_pid_accel_pedal_e,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    COMMANDED_THROTTLE(
        id = "commanded_throttle",
        mode01Pid = 0x4C,
        labelRes = R.string.obd_pid_commanded_throttle,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    ETHANOL_PCT(
        id = "ethanol_pct",
        mode01Pid = 0x52,
        labelRes = R.string.obd_pid_ethanol_pct,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    FUEL_RAIL_ABS(
        id = "fuel_rail_abs",
        mode01Pid = 0x59,
        labelRes = R.string.obd_pid_fuel_rail_abs,
        unitRes = R.string.unit_kpa,
        defaultAccuracy = 0,
    ),
    REL_ACCEL_PEDAL(
        id = "rel_accel_pedal",
        mode01Pid = 0x5A,
        labelRes = R.string.obd_pid_rel_accel_pedal,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    HYBRID_BATTERY(
        id = "hybrid_battery",
        mode01Pid = 0x5B,
        labelRes = R.string.obd_pid_hybrid_battery,
        unitRes = R.string.unit_percent,
        defaultAccuracy = 0,
    ),
    OIL_TEMP(
        id = "oil_temp",
        mode01Pid = 0x5C,
        labelRes = R.string.obd_pid_oil_temp,
        unitRes = R.string.unit_celsius,
        defaultAccuracy = 0,
    ),
    FUEL_RATE(
        id = "fuel_rate",
        mode01Pid = 0x5E,
        labelRes = R.string.obd_pid_fuel_rate,
        unitRes = R.string.unit_l_h,
        defaultAccuracy = 2,
    ),
    ADAPTER_VOLTAGE(
        id = "adapter_voltage",
        mode01Pid = null,
        labelRes = R.string.obd_pid_adapter_voltage,
        unitRes = R.string.unit_volt,
        defaultAccuracy = 2,
    ),
    ;

    fun decodeMode01(dataBytes: ByteArray): Double? {
        val pid = mode01Pid ?: return null
        return Elm327Protocol.decodeMode01Pid(pid, dataBytes)
    }

    companion object {
        private val byId: Map<String, ObdPid> = entries.associateBy { it.id }

        fun fromId(id: String): ObdPid? = byId[id.trim()]

        fun fromIdOrDefault(id: String): ObdPid = fromId(id) ?: RPM

        fun normalizeId(id: String): String = fromIdOrDefault(id).id
    }
}
