package vad.dashing.tbox

/**
 * Cached value of «launch MainActivity in Adayo stock app window» (A10).
 * Default [true] matches DataStore when the key is absent.
 * Updated from [TboxApplication] / settings saves.
 */
object LaunchMainInStockAppWindowSetting {
    @Volatile
    var enabled: Boolean = true
        private set

    fun update(enabled: Boolean) {
        this.enabled = enabled
    }
}

/**
 * Pure decision: attempt Adayo [AdayoStockAppWindow] for TBox MainActivity opens.
 */
internal object LaunchMainInStockAppWindowDecision {
    fun shouldAttempt(
        settingEnabled: Boolean,
        headUnitIsAndroid10: Boolean,
        adayoLauncherAvailable: Boolean,
    ): Boolean = settingEnabled && headUnitIsAndroid10 && adayoLauncherAvailable
}
