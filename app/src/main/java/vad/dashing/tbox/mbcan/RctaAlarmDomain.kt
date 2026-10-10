package vad.dashing.tbox.mbcan

/**
 * `MBCanRctaAlarm` side status (`nLeftSts` / `nRightSts`).
 *
 * Afternoon journal `tbox_app_log_20261010_162750`: both sides rest at **1**.
 * In reverse at 16:28:31 `nRightSts` became **2**. The user confirmed a rear-right
 * obstacle warning at that maneuver. `nLeftSts` also took **2** for about two seconds
 * in the same reverse. `nRCWWarning` stayed **0** through the warning.
 */
object RctaAlarmDomain {
    /** **1** no warning / **2** warning. */
    fun decodeWarning(raw: Int): Boolean? = when (raw) {
        1 -> false
        2 -> true
        else -> null
    }
}
