package vad.dashing.tbox.mbcan

/**
 * Auto Hold cfg **142** while the function stays enabled.
 *
 * The user left Auto Hold on for the whole 2026-10-10 afternoon session
 * (`tbox_app_log_20261010_162750`). Raw **2** is the value while moving and while
 * already parked at the start of the log. Raw **1** appeared about a second after a
 * stop into P (16:29:49, 16:31:59) and cleared to **2** just before driving off
 * (16:30:40). That is the hold engaging until movement starts.
 *
 * Both values stay "feature on" in [MbCanSignalStateEngine.decodeAvhHdcStatusRaw].
 * The write command is still **2** on / **1** off and is a different use of the same id.
 */
enum class AvhHoldPhase {
    Holding,
    Standby,
}

object AvhDomain {
    /** **1** holding the car / **2** function on, not holding. */
    fun decodeHoldPhase(raw: Int): AvhHoldPhase? = when (raw) {
        1 -> AvhHoldPhase.Holding
        2 -> AvhHoldPhase.Standby
        else -> null
    }
}
