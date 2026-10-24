package vad.dashing.tbox.mbcan

import kotlin.math.abs

/**
 * Decodes YAS acceleration to m/s² from:
 * - Android 9 mbCAN: `MBCanVehicleSpeed.getfLateraAccaleleration()` (`eMBCAN_VEHICLE_SPEED`)
 * - Android 10 VHAL: `R_0900_YAS_1_LateraAccaleleration` (`289414960`)
 * - TBox cycle frame (same YAS signal, `UINT16_LE / 1000 − 2`)
 *
 * The bus value is in **g** (CAN range −2..+2 g): on 10.10.2026 drive logs
 * `fYAS_2_LongitudinalAcceleration` tracked `d(fSpeed)/dt` with slope ≈ 0.075 per m/s²
 * (r ≈ 0.9), i.e. ~1/9.81, and `fLateraAccaleleration` stayed within ±0.35 in normal turns.
 *
 * Sign: opposite to the mbCAN steering angle sign in the same logs (r ≈ −0.8).
 */
object LateralAccelerationDomain {
    const val STANDARD_GRAVITY_MPS2 = 9.80665f

    /** CAN signal range of the YAS frame (g). */
    const val MAX_ABS_G = 2f

    private const val RAW_CAN_MIN_INT = 100
    private const val RAW_CAN_MAX_INT = 4000
    private const val RAW_CAN_SENTINEL = 0xFFFF

    fun gToMps2(g: Float?): Float? {
        val v = g ?: return null
        if (!v.isFinite() || abs(v) > MAX_ABS_G) return null
        return v * STANDARD_GRAVITY_MPS2
    }

    fun decodeMbCanFloat(raw: Float?): Float? {
        val v = raw ?: return null
        if (!v.isFinite()) return null
        if (abs(v) <= MAX_ABS_G) return gToMps2(v)
        return decodeRawCanInteger(v.toInt())
    }

    fun decodeVhalRaw(raw: Any?): Float? {
        val num = (raw as? Number) ?: return null
        val f = num.toFloat()
        if (!f.isFinite()) return null
        if (num is Float || num is Double) {
            if (abs(f) <= MAX_ABS_G) return gToMps2(f)
        }
        val i = num.toInt()
        if (i == RAW_CAN_SENTINEL) return null
        if (i in RAW_CAN_MIN_INT..RAW_CAN_MAX_INT) {
            return decodeRawCanInteger(i)
        }
        return null
    }

    private fun decodeRawCanInteger(raw: Int): Float? {
        if (raw !in RAW_CAN_MIN_INT..RAW_CAN_MAX_INT) return null
        return gToMps2(raw / 1000f - 2f)
    }
}
