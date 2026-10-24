package vad.dashing.tbox.mbcan

import kotlin.math.abs

/**
 * Decodes lateral acceleration (m/s²) from:
 * - Android 9 mbCAN: `MBCanVehicleSpeed.getfLateraAccaleleration()` (`eMBCAN_VEHICLE_SPEED`)
 * - Android 10 VHAL: `R_0900_YAS_1_LateraAccaleleration` (`289414960`)
 *
 * Sign convention: left turn + / right turn − (or raw signed m/s²).
 */
object LateralAccelerationDomain {
    /** Physical plausibility bound for passenger SUV lateral acceleration (m/s²). */
    const val MAX_ABS_MPS2 = 25f

    /**
     * Raw CAN integer offset/scale used in Chery/Jetour YAS_1 frames (`raw / 1000 - 2` g/m/s²
     * or `(raw - 2048) * 0.01`) when VHAL passes raw unsigned integer instead of signed float.
     */
    private const val RAW_CAN_MIN_INT = 100
    private const val RAW_CAN_MAX_INT = 4000
    private const val RAW_CAN_SENTINEL = 0xFFFF

    fun decodeMbCanFloat(raw: Float?): Float? {
        val v = raw ?: return null
        if (!v.isFinite()) return null
        if (abs(v) <= MAX_ABS_MPS2) return v
        return decodeRawCanInteger(v.toInt())
    }

    fun decodeVhalRaw(raw: Any?): Float? {
        val num = (raw as? Number) ?: return null
        val f = num.toFloat()
        if (!f.isFinite()) return null
        if (num is Float || num is Double) {
            if (abs(f) <= MAX_ABS_MPS2) return f
        }
        val i = num.toInt()
        if (i == RAW_CAN_SENTINEL) return null
        if (i in RAW_CAN_MIN_INT..RAW_CAN_MAX_INT) {
            return decodeRawCanInteger(i)
        }
        if (abs(f) <= MAX_ABS_MPS2) return f
        return null
    }

    private fun decodeRawCanInteger(raw: Int): Float? {
        if (raw !in RAW_CAN_MIN_INT..RAW_CAN_MAX_INT) return null
        val mps2 = raw / 1000f - 2f
        return mps2.takeIf { it.isFinite() && abs(it) <= MAX_ABS_MPS2 }
    }
}
