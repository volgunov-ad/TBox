package vad.dashing.tbox.mbcan

/**
 * EPS cfg **25** follows drive-mode cfg **145**.
 *
 * Journals `tbox_app_log_20261010_115553` and `162750`: each drive-mode change was
 * followed in the same second by EPS. The user said the steering mode changes
 * automatically with the drive mode. Observed pairs: drive **2** → EPS **2**,
 * drive **0** → EPS **1**. The names of those two modes were not identified.
 * 6DCT-wet cfg **149** stayed at **2**, and BCM `nDriverMode` stayed at **0**.
 */
object DriveModeFollowDomain {
    fun epsRawFollowingDriveMode(driveMode: Int): Int? = when (driveMode) {
        0 -> 1
        2 -> 2
        else -> null
    }
}
