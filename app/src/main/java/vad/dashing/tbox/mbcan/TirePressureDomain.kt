package vad.dashing.tbox.mbcan

import vad.dashing.tbox.Wheels

/**
 * TPMS decode for HU backends (tire pressure / temperature).
 *
 * - **mbCAN (A9):** `MBCanTireInfo.fPressure` already in bar (**-1** = invalid);
 *   `nTemperature` already °C (**-100** = invalid). Order: LF/RF/LR/RR.
 * - **VHAL (A10):** stock `R_0300_CEM_5_*TyrePressure` / `*TyreTemperature` INT32;
 *   **P_bar = raw × 0.0275**; **T_°C = raw − 60**.
 *   Invalid (stock UI): P ≤ 0 or > 3.5 → null; T raw ≤ 0 or ≥ 150 → null.
 *
 * TBox CAN `0x51B` uses **raw/36** for pressure — different from VHAL; keep formulas separate by source.
 *
 * Null-debounce / disk restore for pressure mirrors the TBox path in [vad.dashing.tbox.utils.CanFramesProcess]
 * and [vad.dashing.tbox.BackgroundService] (`wheelPressurePersistAcrossStops`), but HU values use
 * separate DataStore keys (`wheel*_pressure_last_hu`) and are never mixed with TBox.
 */
object TirePressureDomain {
    const val VHAL_PRESSURE_SCALE = 0.0275f
    const val VHAL_TEMPERATURE_OFFSET = 60
    const val VHAL_PRESSURE_MAX_BAR = 3.5f
    const val MB_CAN_PRESSURE_INVALID = -1f
    const val MB_CAN_TEMPERATURE_INVALID = -100
    const val DEFAULT_PRESSURE_NULL_DEBOUNCE_MS = 2_000L
    const val PERSIST_PRESSURE_NULL_DEBOUNCE_MS = 300_000L

    /** A10 VHAL tire pressure raw → bar, or null if stock UI would show ---. */
    fun decodeVhalPressureBar(raw: Int): Float? {
        val bar = raw * VHAL_PRESSURE_SCALE
        if (bar <= 0f || bar > VHAL_PRESSURE_MAX_BAR) return null
        return bar
    }

    /** A10 VHAL tire temperature raw → °C, or null if stock UI would show ---. */
    fun decodeVhalTemperatureC(raw: Int): Float? {
        if (raw <= 0 || raw >= 150) return null
        return (raw - VHAL_TEMPERATURE_OFFSET).toFloat()
    }

    /** A9 mbCAN float bar; -1 (and non-positive) = invalid. */
    fun decodeMbCanPressureBar(fPressure: Float): Float? {
        if (!fPressure.isFinite()) return null
        if (fPressure == MB_CAN_PRESSURE_INVALID || fPressure <= 0f) return null
        return fPressure
    }

    /** A9 mbCAN temperature byte (°C); -100 = invalid. */
    fun decodeMbCanTemperatureC(nTemperature: Int): Float? {
        if (nTemperature == MB_CAN_TEMPERATURE_INVALID) return null
        return nTemperature.toFloat()
    }

    /**
     * Keep last valid pressure while [incoming] is null/invalid, until [debounceMs] since last valid.
     * Same semantics as TBox `0x51B` handling in CanFramesProcess.
     */
    fun resolvePressureAfterSample(
        current: Float?,
        lastTimeNotNull: Long?,
        incoming: Float?,
        now: Long,
        debounceMs: Long,
    ): Pair<Float?, Long?> {
        if (incoming != null && incoming > 0f && incoming.isFinite()) {
            return incoming to now
        }
        return if (now - (lastTimeNotNull ?: 0L) > debounceMs) {
            null to lastTimeNotNull
        } else {
            current to lastTimeNotNull
        }
    }

    /** Merge a full LF/RF/LR/RR pressure snapshot (nulls = invalid samples). */
    fun mergeWheelsPressure(
        current: Wheels,
        incoming: Wheels,
        now: Long,
        debounceMs: Long,
    ): Wheels {
        val (w1, t1) = resolvePressureAfterSample(
            current.wheel1, current.wheel1LastTimeNotNull, incoming.wheel1, now, debounceMs,
        )
        val (w2, t2) = resolvePressureAfterSample(
            current.wheel2, current.wheel2LastTimeNotNull, incoming.wheel2, now, debounceMs,
        )
        val (w3, t3) = resolvePressureAfterSample(
            current.wheel3, current.wheel3LastTimeNotNull, incoming.wheel3, now, debounceMs,
        )
        val (w4, t4) = resolvePressureAfterSample(
            current.wheel4, current.wheel4LastTimeNotNull, incoming.wheel4, now, debounceMs,
        )
        return Wheels(w1, w2, w3, w4, t1, t2, t3, t4)
    }

    /** Merge one corner (0=LF … 3=RR) into [current]. */
    fun mergeWheelsPressureCorner(
        current: Wheels,
        corner: Int,
        incoming: Float?,
        now: Long,
        debounceMs: Long,
    ): Wheels {
        val (value, last) = when (corner) {
            0 -> resolvePressureAfterSample(
                current.wheel1, current.wheel1LastTimeNotNull, incoming, now, debounceMs,
            )
            1 -> resolvePressureAfterSample(
                current.wheel2, current.wheel2LastTimeNotNull, incoming, now, debounceMs,
            )
            2 -> resolvePressureAfterSample(
                current.wheel3, current.wheel3LastTimeNotNull, incoming, now, debounceMs,
            )
            3 -> resolvePressureAfterSample(
                current.wheel4, current.wheel4LastTimeNotNull, incoming, now, debounceMs,
            )
            else -> return current
        }
        return when (corner) {
            0 -> current.copy(wheel1 = value, wheel1LastTimeNotNull = last)
            1 -> current.copy(wheel2 = value, wheel2LastTimeNotNull = last)
            2 -> current.copy(wheel3 = value, wheel3LastTimeNotNull = last)
            else -> current.copy(wheel4 = value, wheel4LastTimeNotNull = last)
        }
    }

    /**
     * Fill null corners from [saved] (disk restore). Sets lastTimeNotNull to [now] for filled corners
     * so null-debounce does not immediately clear them.
     */
    fun restoreMissingPressures(current: Wheels, saved: Wheels, now: Long): Wheels {
        fun pick(cur: Float?, savedV: Float?): Float? = cur ?: savedV?.takeIf { it > 0f && it.isFinite() }
        val w1 = pick(current.wheel1, saved.wheel1)
        val w2 = pick(current.wheel2, saved.wheel2)
        val w3 = pick(current.wheel3, saved.wheel3)
        val w4 = pick(current.wheel4, saved.wheel4)
        return Wheels(
            wheel1 = w1,
            wheel2 = w2,
            wheel3 = w3,
            wheel4 = w4,
            wheel1LastTimeNotNull = if (current.wheel1 == null && w1 != null) now else current.wheel1LastTimeNotNull,
            wheel2LastTimeNotNull = if (current.wheel2 == null && w2 != null) now else current.wheel2LastTimeNotNull,
            wheel3LastTimeNotNull = if (current.wheel3 == null && w3 != null) now else current.wheel3LastTimeNotNull,
            wheel4LastTimeNotNull = if (current.wheel4 == null && w4 != null) now else current.wheel4LastTimeNotNull,
        )
    }

    fun pressureNullDebounceMs(persistAcrossStops: Boolean): Long =
        if (persistAcrossStops) PERSIST_PRESSURE_NULL_DEBOUNCE_MS else DEFAULT_PRESSURE_NULL_DEBOUNCE_MS
}
