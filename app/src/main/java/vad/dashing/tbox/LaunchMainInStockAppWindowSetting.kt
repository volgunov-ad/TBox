package vad.dashing.tbox

import vad.dashing.tbox.dcl.DclCapabilities
import vad.dashing.tbox.dcl.DclLauncherApiContract
import vad.dashing.tbox.dcl.dclCanLaunchAppArea
import vad.dashing.tbox.dcl.dclCanLaunchFullWidth
import vad.dashing.tbox.dcl.dclCanLaunchFullscreen

/**
 * Cached value of «launch MainActivity in app window» (Adayo stock and/or DCL app_area)
 * used when DCL environment mode is **OFF**.
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
 * Main-only DCL launch mode when «Работа в среде DCL» is ON.
 * Storage keys match DCL [DclLauncherApiContract] mode strings.
 *
 * Migration from legacy [LaunchMainInStockAppWindowSetting]:
 * `true` → [APP_AREA], `false` → [FULLSCREEN].
 */
enum class MainDclLaunchMode(val storageKey: String) {
    FULLSCREEN(DclLauncherApiContract.MODE_FULLSCREEN),
    FULL_WIDTH(DclLauncherApiContract.MODE_FULL_WIDTH),
    APP_AREA(DclLauncherApiContract.MODE_APP_AREA),
    ;

    companion object {
        val DEFAULT: MainDclLaunchMode = APP_AREA

        fun fromStored(raw: String?): MainDclLaunchMode {
            val key = raw?.trim().orEmpty()
            return entries.firstOrNull { it.storageKey == key } ?: DEFAULT
        }

        /** Legacy boolean `launch_main_in_stock_app_window`: true → app_area, false → fullscreen. */
        fun migrateFromLegacyBoolean(enabled: Boolean): MainDclLaunchMode =
            if (enabled) APP_AREA else FULLSCREEN

        /**
         * Resolve stored mode string, or migrate from the legacy boolean when the mode key
         * is absent. Default boolean is true (matches DataStore).
         */
        fun resolveStored(
            storedMode: String?,
            legacyBoolean: Boolean?,
            modeKeyPresent: Boolean,
        ): MainDclLaunchMode {
            if (modeKeyPresent && !storedMode.isNullOrBlank()) {
                return fromStored(storedMode)
            }
            return migrateFromLegacyBoolean(legacyBoolean ?: true)
        }
    }
}

/** Cached [MainDclLaunchMode] for cold paths / IntentHelper. */
object MainDclLaunchModeSetting {
    @Volatile
    var mode: MainDclLaunchMode = MainDclLaunchMode.DEFAULT
        private set

    fun update(mode: MainDclLaunchMode) {
        this.mode = mode
    }
}

/** Backend chosen when opening Main via window / DCL. */
enum class MainAppWindowBackend {
    /** DCL `launchApp(package=TBox, mode=app_area)`. */
    DCL_APP_AREA,
    /** DCL `launchApp(..., mode=full_width)`. */
    DCL_FULL_WIDTH,
    /** DCL `launchApp(..., mode=fullscreen)`. */
    DCL_FULLSCREEN,
    /** Adayo `LAUNCH_APP` ActivityView path. */
    ADAYO_STOCK,
    /** Setting off / fullscreen preference — raw Main (no external window host). */
    NONE,
}

/**
 * Pure decision: open TBox [MainActivity] via DCL mode, Adayo stock window, or fullscreen.
 *
 * When DCL environment is ON: use [MainDclLaunchMode] (fullscreen / full_width / app_area).
 * When OFF: legacy boolean — DCL app_area → Adayo stock (A10) → fullscreen.
 */
internal object LaunchMainInStockAppWindowDecision {
    fun resolveBackend(
        dclEnvironmentEnabled: Boolean,
        mainDclMode: MainDclLaunchMode,
        legacyStockWindowEnabled: Boolean,
        dclApiAvailable: Boolean,
        dclCapabilities: DclCapabilities?,
        headUnitIsAndroid10: Boolean,
        adayoLauncherAvailable: Boolean,
    ): MainAppWindowBackend {
        if (dclEnvironmentEnabled) {
            return resolveDclEnvBackend(
                mode = mainDclMode,
                dclApiAvailable = dclApiAvailable,
                dclCapabilities = dclCapabilities,
                headUnitIsAndroid10 = headUnitIsAndroid10,
                adayoLauncherAvailable = adayoLauncherAvailable,
            )
        }
        // Legacy path (DCL env OFF): boolean ON → DCL app_area → Adayo → NONE.
        if (!legacyStockWindowEnabled) return MainAppWindowBackend.NONE
        if (dclCanLaunchAppArea(dclApiAvailable, dclCapabilities)) {
            return MainAppWindowBackend.DCL_APP_AREA
        }
        if (headUnitIsAndroid10 && adayoLauncherAvailable) {
            return MainAppWindowBackend.ADAYO_STOCK
        }
        return MainAppWindowBackend.NONE
    }

    /**
     * Backward-compatible overload used by older call sites / tests that only knew the
     * boolean + app_area capability flag.
     */
    fun resolveBackend(
        settingEnabled: Boolean,
        dclApiAvailable: Boolean,
        dclLaunchAppAreaCapable: Boolean,
        headUnitIsAndroid10: Boolean,
        adayoLauncherAvailable: Boolean,
    ): MainAppWindowBackend {
        val caps = if (dclLaunchAppAreaCapable) {
            DclCapabilities(
                apiVersion = 1,
                launchFullscreen = false,
                launchAppArea = true,
                launchFullWidth = null,
                uiStateQuery = false,
                uiStateEvents = false,
                adasPanel = false,
                screenModes = emptyList(),
                splitSupported = null,
            )
        } else {
            null
        }
        return resolveBackend(
            dclEnvironmentEnabled = false,
            mainDclMode = MainDclLaunchMode.DEFAULT,
            legacyStockWindowEnabled = settingEnabled,
            dclApiAvailable = dclApiAvailable,
            dclCapabilities = caps,
            headUnitIsAndroid10 = headUnitIsAndroid10,
            adayoLauncherAvailable = adayoLauncherAvailable,
        )
    }

    private fun resolveDclEnvBackend(
        mode: MainDclLaunchMode,
        dclApiAvailable: Boolean,
        dclCapabilities: DclCapabilities?,
        headUnitIsAndroid10: Boolean,
        adayoLauncherAvailable: Boolean,
    ): MainAppWindowBackend =
        when (mode) {
            MainDclLaunchMode.FULLSCREEN -> {
                if (dclCanLaunchFullscreen(dclApiAvailable, dclCapabilities)) {
                    MainAppWindowBackend.DCL_FULLSCREEN
                } else {
                    MainAppWindowBackend.NONE
                }
            }
            MainDclLaunchMode.FULL_WIDTH -> {
                if (dclCanLaunchFullWidth(dclApiAvailable, dclCapabilities)) {
                    MainAppWindowBackend.DCL_FULL_WIDTH
                } else if (dclCanLaunchAppArea(dclApiAvailable, dclCapabilities)) {
                    // Prefer a windowed DCL host over raw fullscreen when full_width is absent.
                    MainAppWindowBackend.DCL_APP_AREA
                } else if (headUnitIsAndroid10 && adayoLauncherAvailable) {
                    MainAppWindowBackend.ADAYO_STOCK
                } else {
                    MainAppWindowBackend.NONE
                }
            }
            MainDclLaunchMode.APP_AREA -> {
                if (dclCanLaunchAppArea(dclApiAvailable, dclCapabilities)) {
                    MainAppWindowBackend.DCL_APP_AREA
                } else if (headUnitIsAndroid10 && adayoLauncherAvailable) {
                    MainAppWindowBackend.ADAYO_STOCK
                } else {
                    MainAppWindowBackend.NONE
                }
            }
        }

    fun dclModeForBackend(backend: MainAppWindowBackend): String? =
        when (backend) {
            MainAppWindowBackend.DCL_APP_AREA -> DclLauncherApiContract.MODE_APP_AREA
            MainAppWindowBackend.DCL_FULL_WIDTH -> DclLauncherApiContract.MODE_FULL_WIDTH
            MainAppWindowBackend.DCL_FULLSCREEN -> DclLauncherApiContract.MODE_FULLSCREEN
            MainAppWindowBackend.ADAYO_STOCK,
            MainAppWindowBackend.NONE,
            -> null
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

    fun shouldAttempt(backend: MainAppWindowBackend): Boolean =
        backend != MainAppWindowBackend.NONE

    /**
     * A fresh [MainActivity] on the default display was started from outside (ADB, shortcut,
     * another app). The launcher ActivityView hosts it on a virtual display (id ≠ 0), so the
     * display check also prevents a redirect loop; [lastRedirectAtMs] guards a launcher that
     * falls back to the default display.
     *
     * On A9, DCL hosts on physical display 0, so displayId alone cannot break the
     * finish→relaunch loop. Use [alreadyRedirectedViaDclThisProcess],
     * [intentSkipsRedirect], or [dclAlreadyHostingPackage] as anti-loop.
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
        alreadyRedirectedViaDclThisProcess: Boolean = false,
        intentSkipsRedirect: Boolean = false,
        dclAlreadyHostingPackage: Boolean = false,
    ): Boolean {
        if (!freshInstance || displayId != DEFAULT_DISPLAY_ID) return false
        if (intentSkipsRedirect) return false
        if (alreadyRedirectedViaDclThisProcess || dclAlreadyHostingPackage) return false
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

    fun shouldRedirectExternalLaunch(
        freshInstance: Boolean,
        displayId: Int,
        nowMs: Long,
        lastRedirectAtMs: Long,
        backend: MainAppWindowBackend,
        alreadyRedirectedViaDclThisProcess: Boolean = false,
        intentSkipsRedirect: Boolean = false,
        dclAlreadyHostingPackage: Boolean = false,
    ): Boolean {
        if (!freshInstance || displayId != DEFAULT_DISPLAY_ID) return false
        if (intentSkipsRedirect) return false
        if (alreadyRedirectedViaDclThisProcess || dclAlreadyHostingPackage) return false
        if (lastRedirectAtMs > 0L && nowMs - lastRedirectAtMs < EXTERNAL_REDIRECT_COOLDOWN_MS) {
            return false
        }
        return shouldAttempt(backend)
    }

    /**
     * Pure A9/single-display anti-loop for DCL hosting on display 0.
     * Sticky process flag, intent skip, or DCL uiState already showing [selfPackage].
     */
    fun shouldSkipDclRedirectAntiLoop(
        alreadyRedirectedViaDclThisProcess: Boolean,
        intentSkipsRedirect: Boolean,
        dclForegroundPackage: String?,
        dclScreenMode: String?,
        selfPackageName: String,
    ): Boolean {
        if (intentSkipsRedirect || alreadyRedirectedViaDclThisProcess) return true
        if (selfPackageName.isBlank()) return false
        val fg = dclForegroundPackage?.trim().orEmpty()
        if (fg != selfPackageName) return false
        val mode = dclScreenMode?.trim().orEmpty()
        return mode.isNotEmpty() && mode != "home"
    }

    /**
     * Whether an already-alive Main ([ComponentActivity.onNewIntent] / programmatic reopen)
     * should ask DCL to re-host with [desiredBackend]'s mode.
     *
     * Used after the post-update first-open skip left Main in raw fullscreen (`singleTask`):
     * the second open must land in the chosen DCL mode without [android.app.Activity.finish].
     * Skip when already hosted in a matching DCL screen mode, or when the intent opts out.
     */
    fun shouldReapplyDclLaunchModeOnAliveMain(
        backend: MainAppWindowBackend,
        intentSkipsRedirect: Boolean,
        dclForegroundPackage: String?,
        dclScreenMode: String?,
        selfPackageName: String,
        nowMs: Long,
        lastReapplyAtMs: Long,
    ): Boolean {
        if (intentSkipsRedirect) return false
        val dclMode = dclModeForBackend(backend) ?: return false
        if (lastReapplyAtMs > 0L && nowMs - lastReapplyAtMs < EXTERNAL_REDIRECT_COOLDOWN_MS) {
            return false
        }
        if (selfPackageName.isBlank()) return true
        val fg = dclForegroundPackage?.trim().orEmpty()
        val screen = dclScreenMode?.trim().orEmpty()
        if (fg == selfPackageName && screenModesMatchDesired(screen, dclMode)) {
            return false
        }
        return true
    }

    private fun screenModesMatchDesired(screenMode: String, desiredDclMode: String): Boolean =
        when (desiredDclMode) {
            DclLauncherApiContract.MODE_APP_AREA ->
                screenMode == DclLauncherApiContract.SCREEN_MODE_SINGLE ||
                    screenMode == DclLauncherApiContract.MODE_APP_AREA
            DclLauncherApiContract.MODE_FULL_WIDTH ->
                screenMode == DclLauncherApiContract.SCREEN_MODE_FULL_WIDTH
            DclLauncherApiContract.MODE_FULLSCREEN ->
                screenMode == DclLauncherApiContract.SCREEN_MODE_FULLSCREEN
            else -> false
        }

    /**
     * Show the Main «app window» switch (legacy boolean) on A9 when DCL can host Main,
     * and always on A10. Hidden when DCL environment is ON (replaced by the 3-way picker).
     */
    fun shouldShowMainAppWindowSwitch(
        headUnitIsAndroid10: Boolean,
        dclApiAvailable: Boolean,
        dclEnvironmentEnabled: Boolean,
    ): Boolean {
        if (dclEnvironmentEnabled) return false
        return headUnitIsAndroid10 || dclApiAvailable
    }

    /** 3-way Main DCL mode picker replaces the boolean when DCL environment is ON. */
    fun shouldShowMainDclLaunchModePicker(dclEnvironmentEnabled: Boolean): Boolean =
        dclEnvironmentEnabled

    /**
     * First Main cold open after install/update may skip external DCL/Adayo redirect
     * (fullscreen is acceptable). Armed by [PostUpdateMainLaunchGate] via version stamp
     * and/or [android.content.Intent.ACTION_MY_PACKAGE_REPLACED].
     */
    fun shouldSkipExternalRedirectForFirstOpenAfterUpdate(
        lastSeenVersionCode: Long,
        currentVersionCode: Long,
        packageReplacedArmed: Boolean,
    ): Boolean = packageReplacedArmed || lastSeenVersionCode != currentVersionCode

    /**
     * After a successful DCL launch from an external Main redirect: keep this
     * activity alive (`false` = do not [android.app.Activity.finish]).
     *
     * Main is `singleTask`. DCL on A9 typically **reuses** the already-started task
     * (`moveTaskToFront` + `resizeTask` into freeform/app_area bounds) instead of
     * starting a second instance. Finishing the redirector would destroy that only
     * Main → empty freeform task and HOME-only UI. Adayo `LAUNCH_APP` still needs
     * finish (new host on a virtual display).
     */
    fun shouldFinishSelfAfterSuccessfulDclRedirect(): Boolean = false

    @Deprecated(
        message = "Use shouldFinishSelfAfterSuccessfulDclRedirect()",
        replaceWith = ReplaceWith("shouldFinishSelfAfterSuccessfulDclRedirect()"),
    )
    fun shouldFinishSelfAfterSuccessfulDclAppAreaRedirect(): Boolean =
        shouldFinishSelfAfterSuccessfulDclRedirect()

    const val EXTERNAL_REDIRECT_COOLDOWN_MS = 3_000L
    private const val DEFAULT_DISPLAY_ID = 0
}
