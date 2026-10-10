package vad.dashing.tbox.mbcan

/**
 * `MBCanRadarSensor` (A9 type 7 `eMBCAN_RADARSENSOR`) as seen in deep-diag push.
 *
 * Journals `tbox_app_log_20261010_115553` and `tbox_app_log_20261010_162750`:
 * work status follows cfg **218** about a second later (switch 2 → work 1, switch 1 → work 0).
 * Detect **1** (afternoon, front) and **2** (morning, rear) both meant an object.
 * Beep **0** was silent; **1…4** sounded, and the rank was not monotonic with distance
 * (60 at beep 2, 35 at beep 1).
 *
 * No-target raw sits at **175** on the front sensors (LHF, RHF, LHMF, RHMF) and **335**
 * on the rear and rear-mid sensors (LHR, RHR, LHMR, RHMR). Side sensors (LHSF, LHSR,
 * RHSF, RHSR) stayed on a small scale (1, 2, 20…24) and do not use those sentinels.
 * Distance units are the BCM raw; the numbers match the centimetre-like drops
 * (60, 50, 35, 145, 110, 65) but are not labelled as centimetres here.
 *
 * The PAS on/off switch itself stays cfg **218** (`1` off / `2` on). This object is not a widget.
 */
object RadarSensorDomain {
    const val FRONT_NO_TARGET_RAW = 175
    const val REAR_NO_TARGET_RAW = 335

    enum class Sensor {
        LHF,
        RHF,
        LHMF,
        RHMF,
        LHR,
        RHR,
        LHMR,
        RHMR,
        LHSF,
        LHSR,
        RHSF,
        RHSR,
    }

    /** `nRadarWorkSts`: **0** off / **1** on. */
    fun decodeWorking(raw: Int): Boolean? = when (raw) {
        0 -> false
        1 -> true
        else -> null
    }

    /**
     * `nRadarDetectSts`: **0** no object / **1** and **2** object.
     * The two object codes are kept as one meaning.
     */
    fun decodeObjectPresent(raw: Int): Boolean? = when (raw) {
        0 -> false
        1, 2 -> true
        else -> null
    }

    /**
     * `nAudibleBeepRate`: **0** silent / any positive rate sounding.
     * Rates 1…4 are not ordered by distance.
     */
    fun decodeBeepSounding(raw: Int): Boolean? = when {
        raw == 0 -> false
        raw > 0 -> true
        else -> null
    }

    /** No-target raw for front and rear sensors. Side sensors have no confirmed sentinel. */
    fun noTargetRaw(sensor: Sensor): Int? = when (sensor) {
        Sensor.LHF, Sensor.RHF, Sensor.LHMF, Sensor.RHMF -> FRONT_NO_TARGET_RAW
        Sensor.LHR, Sensor.RHR, Sensor.LHMR, Sensor.RHMR -> REAR_NO_TARGET_RAW
        Sensor.LHSF, Sensor.LHSR, Sensor.RHSF, Sensor.RHSR -> null
    }

    /**
     * Measured distance raw, or null when the sensor is sitting on its no-target value.
     * Side sensors return the raw as-is (their scale is not the 175/335 sentinel).
     */
    fun decodeDistanceRaw(sensor: Sensor, raw: Int): Int? {
        if (raw < 0) return null
        val sentinel = noTargetRaw(sensor)
        if (sentinel != null && raw == sentinel) return null
        return raw
    }
}
