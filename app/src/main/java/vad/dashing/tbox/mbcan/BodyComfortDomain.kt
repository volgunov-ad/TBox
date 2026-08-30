package vad.dashing.tbox.mbcan

/**
 * Live shade / sunroof / window position for automation STATE triggers.
 *
 * Write scales differ (shade/roof 1…11, A10 windows 1/2/3). Status is decoded to
 * closed / open / tilt / vent from the stock read paths:
 * A9 `canGet` 45/46 and BCM `nSunRoof` / `stWindowSts`; A10 `Abat_VentCMDSts`,
 * `PSRFCMDSts`, `*_WIN_Position`.
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
    const val STATE_CLOSED = "closed"
    const val STATE_OPEN = "open"
    const val STATE_TILT = "tilt"
    const val STATE_VENT = "vent"
    /** Stock A9 vent (щель) write, same band as [decodeWindow] 1…30. */
    const val WINDOW_A9_VENT_PERCENT = 20

    val SHADE_STATE_OPTIONS: List<String> = listOf(STATE_CLOSED, STATE_OPEN)
    val ROOF_STATE_OPTIONS: List<String> = listOf(STATE_CLOSED, STATE_OPEN, STATE_TILT)
    val WINDOW_STATE_OPTIONS: List<String> = listOf(STATE_CLOSED, STATE_OPEN, STATE_VENT)

    /** Shade/roof status: 0/1 closed, 12 tilt, 2…11 or 100 open. */
    fun decodeShadeRoof(raw: Int?, allowTilt: Boolean): ShadeRoofPosition? {
        if (raw == null) return null
        return when (raw) {
            0, 1 -> ShadeRoofPosition.Closed
            MbCanKnownVehiclePropertyId.SUNROOF_TILT ->
                if (allowTilt) ShadeRoofPosition.Tilt else null
            in 2..11, 100 -> ShadeRoofPosition.Open
            in 13..99 -> ShadeRoofPosition.Open
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

    fun toAutomationState(position: ShadeRoofPosition): String = when (position) {
        ShadeRoofPosition.Closed -> STATE_CLOSED
        ShadeRoofPosition.Open -> STATE_OPEN
        ShadeRoofPosition.Tilt -> STATE_TILT
    }

    fun toAutomationState(position: WindowPanePosition): String = when (position) {
        WindowPanePosition.Closed -> STATE_CLOSED
        WindowPanePosition.Open -> STATE_OPEN
        WindowPanePosition.Vent -> STATE_VENT
    }

    /**
     * Highlight the last write when it still matches live status; otherwise map
     * Closed/Open/Tilt to the command values the buttons send (1 / 11 / 12).
     */
    fun selectedShadeRoofWriteValue(
        position: ShadeRoofPosition?,
        lastCommand: Int?,
        allowTilt: Boolean,
    ): Int? = when (position) {
        ShadeRoofPosition.Closed -> BodyComfortWrite.SHADE_VALUES.first
        ShadeRoofPosition.Open ->
            lastCommand?.takeIf { it in 2..11 } ?: BodyComfortWrite.SHADE_VALUES.last
        ShadeRoofPosition.Tilt ->
            if (allowTilt) MbCanKnownVehiclePropertyId.SUNROOF_TILT else lastCommand
        null -> lastCommand
    }

    /**
     * A10: 1 close / 2 open / 3 vent. A9: 0 / last-or-20 vent / last-or-100 open.
     */
    fun selectedWindowWriteValue(
        position: WindowPanePosition?,
        lastCommand: Int?,
        android10: Boolean,
    ): Int? {
        if (android10) {
            return when (position) {
                WindowPanePosition.Closed -> MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE
                WindowPanePosition.Open -> MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN
                WindowPanePosition.Vent -> MbCanKnownVehiclePropertyId.WINDOW_A10_VENT
                null -> lastCommand
            }
        }
        return when (position) {
            WindowPanePosition.Closed -> 0
            WindowPanePosition.Vent ->
                lastCommand?.takeIf { it in 1..30 } ?: WINDOW_A9_VENT_PERCENT
            WindowPanePosition.Open ->
                lastCommand?.takeIf { it in 31..100 } ?: 100
            null -> lastCommand
        }
    }
}

data class BodyComfortBcmRaw(
    val sunRoof: Int?,
    val windowFl: Int?,
    val windowFr: Int?,
    val windowRl: Int?,
    val windowRr: Int?,
)

/** Last bus reads for Car Settings debug (not decoded Closed/Open). */
data class BodyComfortRawRead(
    val sunshade: Int? = null,
    val sunroof: Int? = null,
    val windowFl: Int? = null,
    val windowFr: Int? = null,
    val windowRl: Int? = null,
    val windowRr: Int? = null,
) {
    fun format(value: Int?): String = value?.toString() ?: "—"
}
