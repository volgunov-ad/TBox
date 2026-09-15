package vad.dashing.tbox.mbcan

/**
 * Raw gearbox gear number (GSM / EMS target gear).
 *
 * Pass-through of non-negative ints as-is; negative → unknown.
 */
object GearNumberDomain {
    fun decode(raw: Int?): Int? {
        if (raw == null || raw < 0) return null
        return raw
    }
}
