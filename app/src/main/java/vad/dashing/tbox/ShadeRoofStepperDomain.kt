package vad.dashing.tbox

import vad.dashing.tbox.mbcan.BodyComfortDomain

/**
 * Pure helpers for sunshade / sunroof position stepper widgets.
 *
 * Live read is percent 0…100 (step 10). Write scale is 1…11 via
 * [BodyComfortDomain.percentToWrite]. Tilt is never written; a tilted roof is
 * treated as 10 % for ± only so the next step leaves tilt.
 */
object ShadeRoofStepperDomain {
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 100
    const val STEP_PERCENT = 10
    const val DEFAULT_PERCENT = 0

    /** Snap any live percent onto the 0…100 / 10 grid used by shade/roof writes. */
    fun snapPercent(percent: Int): Int {
        val clamped = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val stepped = ((clamped + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT
        return stepped.coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    /**
     * Display / step base from a bus raw. Tilt (12/102) maps to
     * [BodyComfortDomain.ROOF_TILT_PERCENT] only when [allowTilt] is true;
     * otherwise unknown → [DEFAULT_PERCENT].
     */
    fun normalizePercent(raw: Int?, allowTilt: Boolean): Int {
        BodyComfortDomain.shadeRoofPercent(raw)?.let { return snapPercent(it) }
        if (allowTilt && BodyComfortDomain.shadeRoofTilted(raw)) {
            return BodyComfortDomain.ROOF_TILT_PERCENT
        }
        return DEFAULT_PERCENT
    }

    /**
     * True when the center label should show "—" — no live percent, including tilt
     * (widgets are percent-only and never show tilt).
     */
    fun isUnknownDisplay(raw: Int?): Boolean =
        BodyComfortDomain.shadeRoofPercent(raw) == null

    fun stepPercent(currentRaw: Int?, allowTilt: Boolean, increase: Boolean): Int {
        val base = normalizePercent(currentRaw, allowTilt)
        return if (increase) {
            (base + STEP_PERCENT).coerceAtMost(MAX_PERCENT)
        } else {
            (base - STEP_PERCENT).coerceAtLeast(MIN_PERCENT)
        }
    }

    fun writeValue(percent: Int): Int = BodyComfortDomain.percentToWrite(snapPercent(percent))
}
