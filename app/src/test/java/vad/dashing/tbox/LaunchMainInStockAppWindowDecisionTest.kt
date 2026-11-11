package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.dcl.DclCapabilities
import vad.dashing.tbox.dcl.DclLauncherApiContract

class LaunchMainInStockAppWindowDecisionTest {

    private fun caps(
        appArea: Boolean = true,
        fullscreen: Boolean = true,
        fullWidth: Boolean? = true,
    ) = DclCapabilities(
        apiVersion = 1,
        launchFullscreen = fullscreen,
        launchAppArea = appArea,
        launchFullWidth = fullWidth,
        uiStateQuery = true,
        uiStateEvents = false,
        adasPanel = true,
        screenModes = listOf("home", "single", "full_width", "fullscreen"),
        splitSupported = null,
    )

    @Test
    fun migrateFromLegacyBoolean_trueToAppArea_falseToFullscreen() {
        assertEquals(
            MainDclLaunchMode.APP_AREA,
            MainDclLaunchMode.migrateFromLegacyBoolean(true),
        )
        assertEquals(
            MainDclLaunchMode.FULLSCREEN,
            MainDclLaunchMode.migrateFromLegacyBoolean(false),
        )
    }

    @Test
    fun resolveStored_prefersModeKey_thenMigratesBoolean() {
        assertEquals(
            MainDclLaunchMode.FULL_WIDTH,
            MainDclLaunchMode.resolveStored(
                storedMode = "full_width",
                legacyBoolean = false,
                modeKeyPresent = true,
            ),
        )
        assertEquals(
            MainDclLaunchMode.APP_AREA,
            MainDclLaunchMode.resolveStored(
                storedMode = null,
                legacyBoolean = true,
                modeKeyPresent = false,
            ),
        )
        assertEquals(
            MainDclLaunchMode.FULLSCREEN,
            MainDclLaunchMode.resolveStored(
                storedMode = null,
                legacyBoolean = false,
                modeKeyPresent = false,
            ),
        )
        // Absent boolean defaults to true → app_area.
        assertEquals(
            MainDclLaunchMode.APP_AREA,
            MainDclLaunchMode.resolveStored(
                storedMode = null,
                legacyBoolean = null,
                modeKeyPresent = false,
            ),
        )
    }

    @Test
    fun settingOff_legacy_resolvesNone() {
        assertEquals(
            MainAppWindowBackend.NONE,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                settingEnabled = false,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
    }

    @Test
    fun dclPreferredOverAdayo_legacy() {
        assertEquals(
            MainAppWindowBackend.DCL_APP_AREA,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                settingEnabled = true,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
    }

    @Test
    fun adayoWhenDclUnavailableOnA10_legacy() {
        assertEquals(
            MainAppWindowBackend.ADAYO_STOCK,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                settingEnabled = true,
                dclApiAvailable = false,
                dclLaunchAppAreaCapable = false,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
    }

    @Test
    fun a9WithoutDcl_resolvesNone_legacy() {
        assertEquals(
            MainAppWindowBackend.NONE,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                settingEnabled = true,
                dclApiAvailable = false,
                dclLaunchAppAreaCapable = false,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
            ),
        )
    }

    @Test
    fun a9WithDcl_usesDcl_legacy() {
        assertEquals(
            MainAppWindowBackend.DCL_APP_AREA,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                settingEnabled = true,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
            ),
        )
    }

    @Test
    fun dclEnvOn_routesByMainMode() {
        val c = caps()
        assertEquals(
            MainAppWindowBackend.DCL_FULLSCREEN,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                dclEnvironmentEnabled = true,
                mainDclMode = MainDclLaunchMode.FULLSCREEN,
                legacyStockWindowEnabled = true,
                dclApiAvailable = true,
                dclCapabilities = c,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
            ),
        )
        assertEquals(
            MainAppWindowBackend.DCL_FULL_WIDTH,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                dclEnvironmentEnabled = true,
                mainDclMode = MainDclLaunchMode.FULL_WIDTH,
                legacyStockWindowEnabled = false,
                dclApiAvailable = true,
                dclCapabilities = c,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
            ),
        )
        assertEquals(
            MainAppWindowBackend.DCL_APP_AREA,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                dclEnvironmentEnabled = true,
                mainDclMode = MainDclLaunchMode.APP_AREA,
                legacyStockWindowEnabled = false,
                dclApiAvailable = true,
                dclCapabilities = c,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
            ),
        )
    }

    @Test
    fun dclEnvOn_fullWidthFallsBackToAppAreaThenAdayo() {
        assertEquals(
            MainAppWindowBackend.DCL_APP_AREA,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                dclEnvironmentEnabled = true,
                mainDclMode = MainDclLaunchMode.FULL_WIDTH,
                legacyStockWindowEnabled = true,
                dclApiAvailable = true,
                dclCapabilities = caps(fullWidth = false, appArea = true),
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
        assertEquals(
            MainAppWindowBackend.ADAYO_STOCK,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                dclEnvironmentEnabled = true,
                mainDclMode = MainDclLaunchMode.FULL_WIDTH,
                legacyStockWindowEnabled = true,
                dclApiAvailable = false,
                dclCapabilities = null,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
    }

    @Test
    fun dclEnvOff_ignoresMainMode_usesLegacyBoolean() {
        assertEquals(
            MainAppWindowBackend.NONE,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                dclEnvironmentEnabled = false,
                mainDclMode = MainDclLaunchMode.APP_AREA,
                legacyStockWindowEnabled = false,
                dclApiAvailable = true,
                dclCapabilities = caps(),
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
        assertEquals(
            MainAppWindowBackend.DCL_APP_AREA,
            LaunchMainInStockAppWindowDecision.resolveBackend(
                dclEnvironmentEnabled = false,
                mainDclMode = MainDclLaunchMode.FULLSCREEN,
                legacyStockWindowEnabled = true,
                dclApiAvailable = true,
                dclCapabilities = caps(),
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
            ),
        )
    }

    @Test
    fun dclModeForBackend_mapsStorageKeys() {
        assertEquals(
            DclLauncherApiContract.MODE_APP_AREA,
            LaunchMainInStockAppWindowDecision.dclModeForBackend(MainAppWindowBackend.DCL_APP_AREA),
        )
        assertEquals(
            DclLauncherApiContract.MODE_FULL_WIDTH,
            LaunchMainInStockAppWindowDecision.dclModeForBackend(MainAppWindowBackend.DCL_FULL_WIDTH),
        )
        assertEquals(
            DclLauncherApiContract.MODE_FULLSCREEN,
            LaunchMainInStockAppWindowDecision.dclModeForBackend(MainAppWindowBackend.DCL_FULLSCREEN),
        )
        assertEquals(
            null,
            LaunchMainInStockAppWindowDecision.dclModeForBackend(MainAppWindowBackend.ADAYO_STOCK),
        )
        assertEquals(
            null,
            LaunchMainInStockAppWindowDecision.dclModeForBackend(MainAppWindowBackend.NONE),
        )
    }

    @Test
    fun shouldAttempt_matchesResolve() {
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = true,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = false,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = true,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = true,
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = true,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
            ),
        )
    }

    @Test
    fun showMainSwitch_hiddenWhenDclEnvOn_pickerInstead() {
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldShowMainAppWindowSwitch(
                headUnitIsAndroid10 = true,
                dclApiAvailable = true,
                dclEnvironmentEnabled = true,
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldShowMainDclLaunchModePicker(
                dclEnvironmentEnabled = true,
            ),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldShowMainDclLaunchModePicker(
                dclEnvironmentEnabled = false,
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldShowMainAppWindowSwitch(
                headUnitIsAndroid10 = false,
                dclApiAvailable = true,
                dclEnvironmentEnabled = false,
            ),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldShowMainAppWindowSwitch(
                headUnitIsAndroid10 = false,
                dclApiAvailable = false,
                dclEnvironmentEnabled = false,
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldShowMainAppWindowSwitch(
                headUnitIsAndroid10 = true,
                dclApiAvailable = false,
                dclEnvironmentEnabled = false,
            ),
        )
    }

    private fun redirect(
        freshInstance: Boolean = true,
        displayId: Int = 0,
        nowMs: Long = 100_000L,
        lastRedirectAtMs: Long = 0L,
        settingEnabled: Boolean = true,
        headUnitIsAndroid10: Boolean = true,
        adayoLauncherAvailable: Boolean = true,
        dclApiAvailable: Boolean = false,
        dclLaunchAppAreaCapable: Boolean = false,
        alreadyRedirectedViaDclThisProcess: Boolean = false,
        intentSkipsRedirect: Boolean = false,
        dclAlreadyHostingPackage: Boolean = false,
    ) = LaunchMainInStockAppWindowDecision.shouldRedirectExternalLaunch(
        freshInstance = freshInstance,
        displayId = displayId,
        nowMs = nowMs,
        lastRedirectAtMs = lastRedirectAtMs,
        settingEnabled = settingEnabled,
        headUnitIsAndroid10 = headUnitIsAndroid10,
        adayoLauncherAvailable = adayoLauncherAvailable,
        dclApiAvailable = dclApiAvailable,
        dclLaunchAppAreaCapable = dclLaunchAppAreaCapable,
        alreadyRedirectedViaDclThisProcess = alreadyRedirectedViaDclThisProcess,
        intentSkipsRedirect = intentSkipsRedirect,
        dclAlreadyHostingPackage = dclAlreadyHostingPackage,
    )

    @Test
    fun externalLaunch_onDefaultDisplay_redirects() {
        assertTrue(redirect())
    }

    @Test
    fun externalLaunch_withDclOnA9_redirects() {
        assertTrue(
            redirect(
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
            ),
        )
    }

    @Test
    fun externalLaunch_insideLauncherActivityView_doesNotRedirect() {
        assertFalse(redirect(displayId = 1))
    }

    @Test
    fun externalLaunch_recreatedInstance_doesNotRedirect() {
        assertFalse(redirect(freshInstance = false))
    }

    @Test
    fun externalLaunch_withinCooldown_doesNotRedirect() {
        assertFalse(redirect(nowMs = 101_000L, lastRedirectAtMs = 100_000L))
        assertTrue(
            redirect(
                nowMs = 100_000L + LaunchMainInStockAppWindowDecision.EXTERNAL_REDIRECT_COOLDOWN_MS,
                lastRedirectAtMs = 100_000L,
            ),
        )
    }

    @Test
    fun externalLaunch_settingOffOrAndroid9WithoutDcl_doesNotRedirect() {
        assertFalse(redirect(settingEnabled = false))
        assertFalse(
            redirect(
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
                dclApiAvailable = false,
            ),
        )
    }

    @Test
    fun externalLaunch_dclStickyAntiLoop_doesNotRedirect() {
        assertFalse(
            redirect(
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
                alreadyRedirectedViaDclThisProcess = true,
            ),
        )
        assertFalse(
            redirect(
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
                intentSkipsRedirect = true,
            ),
        )
        assertFalse(
            redirect(
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
                dclAlreadyHostingPackage = true,
            ),
        )
        // After cooldown, sticky still blocks A9 display-0 re-entry.
        assertFalse(
            redirect(
                nowMs = 100_000L + LaunchMainInStockAppWindowDecision.EXTERNAL_REDIRECT_COOLDOWN_MS,
                lastRedirectAtMs = 100_000L,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = false,
                dclApiAvailable = true,
                dclLaunchAppAreaCapable = true,
                alreadyRedirectedViaDclThisProcess = true,
            ),
        )
    }

    @Test
    fun shouldSkipExternalRedirectForFirstOpenAfterUpdate_versionOrArmed() {
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldSkipExternalRedirectForFirstOpenAfterUpdate(
                lastSeenVersionCode = 0L,
                currentVersionCode = 10022L,
                packageReplacedArmed = false,
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldSkipExternalRedirectForFirstOpenAfterUpdate(
                lastSeenVersionCode = 10021L,
                currentVersionCode = 10022L,
                packageReplacedArmed = false,
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldSkipExternalRedirectForFirstOpenAfterUpdate(
                lastSeenVersionCode = 10022L,
                currentVersionCode = 10022L,
                packageReplacedArmed = true,
            ),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldSkipExternalRedirectForFirstOpenAfterUpdate(
                lastSeenVersionCode = 10022L,
                currentVersionCode = 10022L,
                packageReplacedArmed = false,
            ),
        )
    }

    @Test
    fun shouldFinishSelfAfterSuccessfulDclRedirect_isFalse() {
        // singleTask + DCL REUSED_TASK: finish would destroy the only Main.
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldFinishSelfAfterSuccessfulDclRedirect(),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldFinishSelfAfterSuccessfulDclAppAreaRedirect(),
        )
    }

    @Test
    fun shouldSkipDclRedirectAntiLoop_matchesHostingAndSticky() {
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldSkipDclRedirectAntiLoop(
                alreadyRedirectedViaDclThisProcess = true,
                intentSkipsRedirect = false,
                dclForegroundPackage = null,
                dclScreenMode = null,
                selfPackageName = "vad.dashing.tbox",
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldSkipDclRedirectAntiLoop(
                alreadyRedirectedViaDclThisProcess = false,
                intentSkipsRedirect = true,
                dclForegroundPackage = null,
                dclScreenMode = null,
                selfPackageName = "vad.dashing.tbox",
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldSkipDclRedirectAntiLoop(
                alreadyRedirectedViaDclThisProcess = false,
                intentSkipsRedirect = false,
                dclForegroundPackage = "vad.dashing.tbox",
                dclScreenMode = "single",
                selfPackageName = "vad.dashing.tbox",
            ),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldSkipDclRedirectAntiLoop(
                alreadyRedirectedViaDclThisProcess = false,
                intentSkipsRedirect = false,
                dclForegroundPackage = "vad.dashing.tbox",
                dclScreenMode = "home",
                selfPackageName = "vad.dashing.tbox",
            ),
        )
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldSkipDclRedirectAntiLoop(
                alreadyRedirectedViaDclThisProcess = false,
                intentSkipsRedirect = false,
                dclForegroundPackage = "other.app",
                dclScreenMode = "single",
                selfPackageName = "vad.dashing.tbox",
            ),
        )
    }

    @Test
    fun shouldReapplyDclLaunchModeOnAliveMain_afterStickyFullscreen() {
        // Post-update first open left Main raw fullscreen; second open must re-host.
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldReapplyDclLaunchModeOnAliveMain(
                backend = MainAppWindowBackend.DCL_APP_AREA,
                intentSkipsRedirect = false,
                dclForegroundPackage = null,
                dclScreenMode = "home",
                selfPackageName = "vad.dashing.tbox",
                nowMs = 100_000L,
                lastReapplyAtMs = 0L,
            ),
        )
        // Already in matching app_area/single — no re-apply.
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldReapplyDclLaunchModeOnAliveMain(
                backend = MainAppWindowBackend.DCL_APP_AREA,
                intentSkipsRedirect = false,
                dclForegroundPackage = "vad.dashing.tbox",
                dclScreenMode = "single",
                selfPackageName = "vad.dashing.tbox",
                nowMs = 100_000L,
                lastReapplyAtMs = 0L,
            ),
        )
        // Wrong mode (fullscreen while wanting full_width) → re-apply.
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldReapplyDclLaunchModeOnAliveMain(
                backend = MainAppWindowBackend.DCL_FULL_WIDTH,
                intentSkipsRedirect = false,
                dclForegroundPackage = "vad.dashing.tbox",
                dclScreenMode = "fullscreen",
                selfPackageName = "vad.dashing.tbox",
                nowMs = 100_000L,
                lastReapplyAtMs = 0L,
            ),
        )
        // NONE / Adayo backends have no DCL mode to re-apply.
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldReapplyDclLaunchModeOnAliveMain(
                backend = MainAppWindowBackend.NONE,
                intentSkipsRedirect = false,
                dclForegroundPackage = null,
                dclScreenMode = null,
                selfPackageName = "vad.dashing.tbox",
                nowMs = 100_000L,
                lastReapplyAtMs = 0L,
            ),
        )
        // Cooldown blocks thrash.
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldReapplyDclLaunchModeOnAliveMain(
                backend = MainAppWindowBackend.DCL_APP_AREA,
                intentSkipsRedirect = false,
                dclForegroundPackage = null,
                dclScreenMode = "home",
                selfPackageName = "vad.dashing.tbox",
                nowMs = 101_000L,
                lastReapplyAtMs = 100_000L,
            ),
        )
    }
}
