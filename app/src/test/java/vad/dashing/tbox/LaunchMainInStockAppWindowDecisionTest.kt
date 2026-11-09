package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchMainInStockAppWindowDecisionTest {

    @Test
    fun settingOff_resolvesNone() {
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
    fun dclPreferredOverAdayo() {
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
    fun adayoWhenDclUnavailableOnA10() {
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
    fun a9WithoutDcl_resolvesNone() {
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
    fun a9WithDcl_usesDcl() {
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
    fun showMainSwitch_onA9WhenDclPresent() {
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldShowMainAppWindowSwitch(
                headUnitIsAndroid10 = false,
                dclApiAvailable = true,
                dclEnvironmentEnabled = false,
            ),
        )
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldShowMainAppWindowSwitch(
                headUnitIsAndroid10 = false,
                dclApiAvailable = false,
                dclEnvironmentEnabled = true,
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
}
