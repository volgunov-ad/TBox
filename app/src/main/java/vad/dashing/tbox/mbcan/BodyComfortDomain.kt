package vad.dashing.tbox.mbcan

/**
 * Live shade / sunroof / window position for automation STATE triggers.
 *
 * Write scales differ (shade/roof 1…11 + 12 tilt, A10 windows 1/2/3). Live status
 * is percent: shade/roof read 0…100 % with step 10 (write N ↔ read (N−1)×10 %,
 * roof tilt reads **102** or 10 %), windows 0…100 % from A9 BCM `stWindowSts` /
 * A10 `*_WIN_Position`.
 */
enum class ShadeRoofPosition {
    Closed,
    Open,
    Tilt,
}

enum class WindowPanePosition {
    Closed,
    Open,
    Vent,
}

object BodyComfortDomain {
    const val STATE_TILT = "tilt"
    /** Stock A9 vent (щель) write, same band as [decodeWindow] 1…30. */
    const val WINDOW_A9_VENT_PERCENT = 20
    /** A9 comfort-open stop; the car also holds 0 / 20 / 100. */
    const val WINDOW_A9_COMFORT_OPEN_PERCENT = 80
    /** Live roof tilt on A9 cfg/canGet 45 (write tilt is still [MbCanKnownVehiclePropertyId.SUNROOF_TILT] = 12). */
    const val SUNROOF_STATUS_TILT = 102
    /** Roof tilt lives at 10 %: the car reports 10 or 102 for the tilted roof. */
    const val ROOF_TILT_PERCENT = 10

    fun sanitizeStatusRaw(raw: Int?): Int? =
        raw?.takeIf { it >= 0 && it <= SUNROOF_STATUS_TILT }

    /** Roof tilt read: command echo 12 or live 102. */
    fun shadeRoofTilted(raw: Int?): Boolean =
        raw == MbCanKnownVehiclePropertyId.SUNROOF_TILT || raw == SUNROOF_STATUS_TILT

    /** Live shade/roof percent 0…100; tilt reads (12/102) are not a percent. */
    fun shadeRoofPercent(raw: Int?): Int? =
        sanitizeStatusRaw(raw)?.takeIf { it in 0..100 && !shadeRoofTilted(it) }

    /** Write scale 1…11 maps to read percent 0…100: write = percent / 10 + 1. */
    fun percentToWrite(percent: Int): Int = percent.coerceIn(0, 100) / 10 + 1

    /** Automation STATE values: percent steps 0…100 % (+ tilt for the roof). */
    val SHADE_STATE_OPTIONS: List<String> = (0..100 step 10).map { "$it%" }
    val ROOF_STATE_OPTIONS: List<String> = SHADE_STATE_OPTIONS + STATE_TILT
    val WINDOW_STATE_OPTIONS: List<String> =
        BodyComfortWrite.WINDOW_A9_PERCENT_STEPS.map { "$it%" }

    /**
     * Shade/roof live status. Write scale is 1…11 (+12 tilt). Read scale on A9 roof
     * is percent: **0** closed, **10…100** open, **102** tilt. Command echo 12 still
     * counts as tilt. **−1** is invalid (BCM `getSunRoof`).
     */
    fun decodeShadeRoof(raw: Int?, allowTilt: Boolean): ShadeRoofPosition? {
        val value = sanitizeStatusRaw(raw) ?: return null
        return when (value) {
            0, 1 -> ShadeRoofPosition.Closed
            MbCanKnownVehiclePropertyId.SUNROOF_TILT, SUNROOF_STATUS_TILT ->
                if (allowTilt) ShadeRoofPosition.Tilt else null
            in 2..11, in 13..100 -> ShadeRoofPosition.Open
            else -> null
        }
    }

    /**
     * Window position: 0…100 % (A9 BCM / A10 `*_WIN_Position`).
     * 0 closed, 1…30 vent (stock A9 щель is 20), 31…100 open.
     */
    fun decodeWindow(raw: Int?): WindowPanePosition? {
        if (raw == null) return null
        return when (raw) {
            0 -> WindowPanePosition.Closed
            in 1..30 -> WindowPanePosition.Vent
            in 31..100 -> WindowPanePosition.Open
            else -> null
        }
    }

    /**
     * Buttons to highlight from live raw: write = percent / 10 + 1. Roof tilt
     * (read 12/102 or 10 %) lights both the «10%» and «Откинуть» buttons.
     */
    fun selectedShadeRoofWriteValues(
        raw: Int?,
        lastCommand: Int?,
        allowTilt: Boolean,
    ): Set<Int> {
        val percent = shadeRoofPercent(raw)
        if (percent == null) {
            return if (allowTilt && shadeRoofTilted(raw)) {
                roofTiltWriteValues
            } else {
                setOfNotNull(lastCommand)
            }
        }
        val write = percentToWrite(percent)
        return if (allowTilt && percent == ROOF_TILT_PERCENT) {
            roofTiltWriteValues + write
        } else {
            setOf(write)
        }
    }

    /** Roof tilt at 10 %: «10%» (write 2) and «Откинуть» (write 12) together. */
    val roofTiltWriteValues: Set<Int> =
        setOf(percentToWrite(ROOF_TILT_PERCENT), MbCanKnownVehiclePropertyId.SUNROOF_TILT)

    /**
     * A9: live percent snaps to the nearest allowed stop 0/20/80/100.
     * A10: 1 close / 2 open / 3 vent derived from the percent bands.
     */
    fun selectedWindowWriteValues(
        raw: Int?,
        lastCommand: Int?,
        android10: Boolean,
    ): Set<Int> {
        val percent = sanitizeStatusRaw(raw)?.takeIf { it in 0..100 }
            ?: return setOfNotNull(lastCommand)
        if (android10) {
            return setOf(
                when {
                    percent == 0 -> MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE
                    percent <= WINDOW_A9_VENT_PERCENT + 10 -> MbCanKnownVehiclePropertyId.WINDOW_A10_VENT
                    else -> MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN
                },
            )
        }
        return setOf(nearestWindowA9Step(percent))
    }

    fun nearestWindowA9Step(percent: Int): Int =
        BodyComfortWrite.WINDOW_A9_PERCENT_STEPS.minBy { step -> kotlin.math.abs(step - percent) }
}

data class BodyComfortBcmRaw(
    val sunRoof: Int?,
    val windowFl: Int?,
    val windowFr: Int?,
    val windowRl: Int?,
    val windowRr: Int?,
)

/** Last bus reads for Car Settings (percent 0…100, roof tilt 102). */
data class BodyComfortRawRead(
    val sunshade: Int? = null,
    val sunroof: Int? = null,
    val windowFl: Int? = null,
    val windowFr: Int? = null,
    val windowRl: Int? = null,
    val windowRr: Int? = null,
)
