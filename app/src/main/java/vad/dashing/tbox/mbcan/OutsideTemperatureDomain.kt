package vad.dashing.tbox.mbcan

import vad.dashing.tbox.utils.InOutTemperatureNullDebounce

/**
 * Outside ambient temperature decode for HU backends.
 *
 * - **mbCAN (A9):** `getExternalTemperatureRaw` is already °C (signed byte); **87** = invalid.
 * - **VHAL (A10):** `R_0400_CEM_IPM_3_ExternalTemperatureRaw` is the same raw encoding as TBox CAN
 *   `0x535`: unsigned byte → `°C = raw × 0.5 − 40` (same as [vad.dashing.tbox.utils.CanFramesProcess]).
 */
object OutsideTemperatureDomain {
    private const val SCALE = 0.5f
    private const val OFFSET = -40f
    private const val MB_CAN_INVALID_SENTINEL = 87

    /** A9 mbCAN: raw byte is already °C; sentinel 87 = invalid. */
    fun decodeMbCanCelsiusRaw(raw: Int): Float? {
        val asByte = raw.toByte().toInt()
        if (asByte == MB_CAN_INVALID_SENTINEL || raw == MB_CAN_INVALID_SENTINEL) return null
        return asByte.toFloat()
    }

    /**
     * A10 VHAL: treat [raw] as unsigned byte (handles signed delivery, e.g. 138 → −118).
     * Formula matches TBox CAN outside temp. Out-of-range → null.
     */
    fun decodeVhalRaw(raw: Int): Float? {
        val unsigned = raw and 0xFF
        val celsius = unsigned * SCALE + OFFSET
        return celsius.takeIf(InOutTemperatureNullDebounce::isValidCelsius)
    }
}
