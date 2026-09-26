package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPanelsShowOnServiceStartDelayTest {

    @Test
    fun coerceSeconds_clampsToRange() {
        assertEquals(0, FloatingPanelsShowOnServiceStartDelay.coerceSeconds(-1))
        assertEquals(0, FloatingPanelsShowOnServiceStartDelay.coerceSeconds(0))
        assertEquals(3, FloatingPanelsShowOnServiceStartDelay.coerceSeconds(3))
        assertEquals(60, FloatingPanelsShowOnServiceStartDelay.coerceSeconds(60))
        assertEquals(60, FloatingPanelsShowOnServiceStartDelay.coerceSeconds(99))
    }

    @Test
    fun delayMs_usesCoercedSeconds() {
        assertEquals(0L, FloatingPanelsShowOnServiceStartDelay.delayMs(0))
        assertEquals(3_000L, FloatingPanelsShowOnServiceStartDelay.delayMs(3))
        assertEquals(5_000L, FloatingPanelsShowOnServiceStartDelay.delayMs(5))
        assertEquals(0L, FloatingPanelsShowOnServiceStartDelay.delayMs(-5))
        assertEquals(60_000L, FloatingPanelsShowOnServiceStartDelay.delayMs(100))
    }

    @Test
    fun defaultsMatchSettingsManagerConstants() {
        assertEquals(
            FloatingPanelsShowOnServiceStartDelay.DEFAULT_SECONDS,
            SettingsManager.DEFAULT_FLOATING_PANELS_SHOW_ON_SERVICE_START_DELAY_SECONDS,
        )
        assertEquals(
            FloatingPanelsShowOnServiceStartDelay.MIN_SECONDS,
            SettingsManager.MIN_FLOATING_PANELS_SHOW_ON_SERVICE_START_DELAY_SECONDS,
        )
        assertEquals(
            FloatingPanelsShowOnServiceStartDelay.MAX_SECONDS,
            SettingsManager.MAX_FLOATING_PANELS_SHOW_ON_SERVICE_START_DELAY_SECONDS,
        )
    }
}
