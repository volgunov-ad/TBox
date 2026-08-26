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
}
