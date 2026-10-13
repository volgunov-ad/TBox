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

    /**
     * A fresh [MainActivity] on the default display was started from outside (ADB, shortcut,
     * another app). The launcher ActivityView hosts it on a virtual display (id ≠ 0), so the
     * display check also prevents a redirect loop; [lastRedirectAtMs] guards a launcher that
     * falls back to the default display.
     */
    fun shouldRedirectExternalLaunch(
        freshInstance: Boolean,
        displayId: Int,
        nowMs: Long,
        lastRedirectAtMs: Long,
        settingEnabled: Boolean,
        headUnitIsAndroid10: Boolean,
        adayoLauncherAvailable: Boolean,
    ): Boolean {
        if (!freshInstance || displayId != DEFAULT_DISPLAY_ID) return false
        if (lastRedirectAtMs > 0L && nowMs - lastRedirectAtMs < EXTERNAL_REDIRECT_COOLDOWN_MS) {
            return false
        }
        return shouldAttempt(settingEnabled, headUnitIsAndroid10, adayoLauncherAvailable)
    }

    const val EXTERNAL_REDIRECT_COOLDOWN_MS = 3_000L
    private const val DEFAULT_DISPLAY_ID = 0
}
