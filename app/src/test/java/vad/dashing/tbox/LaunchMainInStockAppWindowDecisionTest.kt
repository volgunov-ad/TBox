package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchMainInStockAppWindowDecisionTest {

    @Test
    fun a10_enabled_withAdayo_attempts() {
        assertTrue(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = true,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
    }

    @Test
    fun settingOff_neverAttempts() {
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = false,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = true,
            ),
        )
    }

    @Test
    fun android9_neverAttempts() {
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = true,
                headUnitIsAndroid10 = false,
                adayoLauncherAvailable = true,
            ),
        )
    }

    @Test
    fun a10_withoutAdayo_doesNotAttempt() {
        assertFalse(
            LaunchMainInStockAppWindowDecision.shouldAttempt(
                settingEnabled = true,
                headUnitIsAndroid10 = true,
                adayoLauncherAvailable = false,
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
    ) = LaunchMainInStockAppWindowDecision.shouldRedirectExternalLaunch(
        freshInstance = freshInstance,
        displayId = displayId,
        nowMs = nowMs,
        lastRedirectAtMs = lastRedirectAtMs,
        settingEnabled = settingEnabled,
        headUnitIsAndroid10 = headUnitIsAndroid10,
        adayoLauncherAvailable = true,
    )

    @Test
    fun externalLaunch_onDefaultDisplay_redirects() {
        assertTrue(redirect())
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
    fun externalLaunch_settingOffOrAndroid9_doesNotRedirect() {
        assertFalse(redirect(settingEnabled = false))
        assertFalse(redirect(headUnitIsAndroid10 = false))
    }
}
