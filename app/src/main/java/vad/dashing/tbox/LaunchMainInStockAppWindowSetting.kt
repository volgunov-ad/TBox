package vad.dashing.tbox

/**
 * Cached value of «launch MainActivity in app window» (Adayo stock and/or DCL app_area).
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

/** Backend chosen when [LaunchMainInStockAppWindowSetting] is ON. */
enum class MainAppWindowBackend {
    /** DCL `launchApp(package=TBox, mode=app_area)`. */
    DCL_APP_AREA,
    /** Adayo `LAUNCH_APP` ActivityView path. */
    ADAYO_STOCK,
    /** Setting off, or no usable window backend — fullscreen Main. */
    NONE,
}

/**
 * Pure decision: open TBox [MainActivity] via DCL app_area, Adayo stock window, or fullscreen.
 *
 * Priority when setting is ON: DCL app_area (if API available) → Adayo stock (A10) → fullscreen.
 */
internal object LaunchMainInStockAppWindowDecision {
    fun resolveBackend(
        settingEnabled: Boolean,
        dclApiAvailable: Boolean,
        dclLaunchAppAreaCapable: Boolean,
        headUnitIsAndroid10: Boolean,
        adayoLauncherAvailable: Boolean,
    ): MainAppWindowBackend {
        if (!settingEnabled) return MainAppWindowBackend.NONE
        if (dclApiAvailable && dclLaunchAppAreaCapable) {
            return MainAppWindowBackend.DCL_APP_AREA
        }
        if (headUnitIsAndroid10 && adayoLauncherAvailable) {
            return MainAppWindowBackend.ADAYO_STOCK
        }
        return MainAppWindowBackend.NONE
    }

    fun shouldAttempt(
        settingEnabled: Boolean,
        headUnitIsAndroid10: Boolean,
        adayoLauncherAvailable: Boolean,
        dclApiAvailable: Boolean = false,
        dclLaunchAppAreaCapable: Boolean = false,
    ): Boolean =
        resolveBackend(
            settingEnabled = settingEnabled,
            dclApiAvailable = dclApiAvailable,
            dclLaunchAppAreaCapable = dclLaunchAppAreaCapable,
            headUnitIsAndroid10 = headUnitIsAndroid10,
            adayoLauncherAvailable = adayoLauncherAvailable,
        ) != MainAppWindowBackend.NONE

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
        dclApiAvailable: Boolean = false,
        dclLaunchAppAreaCapable: Boolean = false,
    ): Boolean {
        if (!freshInstance || displayId != DEFAULT_DISPLAY_ID) return false
        if (lastRedirectAtMs > 0L && nowMs - lastRedirectAtMs < EXTERNAL_REDIRECT_COOLDOWN_MS) {
            return false
        }
        return shouldAttempt(
            settingEnabled = settingEnabled,
            headUnitIsAndroid10 = headUnitIsAndroid10,
            adayoLauncherAvailable = adayoLauncherAvailable,
            dclApiAvailable = dclApiAvailable,
            dclLaunchAppAreaCapable = dclLaunchAppAreaCapable,
        )
    }

    /** Show the Main «app window» switch on A9 when DCL can host Main in app_area. */
    fun shouldShowMainAppWindowSwitch(
        headUnitIsAndroid10: Boolean,
        dclApiAvailable: Boolean,
        dclEnvironmentEnabled: Boolean,
    ): Boolean = headUnitIsAndroid10 || dclApiAvailable || dclEnvironmentEnabled

    const val EXTERNAL_REDIRECT_COOLDOWN_MS = 3_000L
    private const val DEFAULT_DISPLAY_ID = 0
}
