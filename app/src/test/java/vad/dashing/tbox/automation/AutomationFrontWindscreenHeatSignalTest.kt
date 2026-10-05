package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanSignal

class AutomationFrontWindscreenHeatSignalTest {
    @Test
    fun frontWindscreenHeat_interestIsElectricHeatNotBlowMode() {
        assertSame(
            MbCanSignal.FrontWindscreenHeat,
            huInterestForSignal(AutomationSignalId.FRONT_WINDSCREEN_HEAT),
        )
        assertEquals(
            MbCanSignal.HvacBlowMode,
            huInterestForSignal(AutomationSignalId.HVAC_FAN_DIRECTION),
        )
    }
}
