package vad.dashing.tbox.esp

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.automation.AutomationSignalValue
import vad.dashing.tbox.automation.espGpioInputFlow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EspCompanionGpioSnapshotTest {
    @Before
    fun reset() {
        EspCompanionRepository.updateConnected(false)
        EspCompanionRepository.updateGpioMask(0)
    }

    @Test
    fun inputFlow_staysUnavailableUntilSnapshot_thenFollowsChanges() = runBlocking {
        assertFalse(EspCompanionRepository.gpioInputsReady.value)
        assertEquals(AutomationSignalValue.Unavailable, espGpioInputFlow(0).first())

        EspCompanionRepository.updateConnected(true)
        EspCompanionRepository.updateGpioMask(0x1)
        EspCompanionRepository.applyGpioEvent(0, true)
        assertFalse(EspCompanionRepository.gpioInputsReady.value)
        assertEquals(AutomationSignalValue.Unavailable, espGpioInputFlow(0).first())

        EspCompanionRepository.confirmGpioSnapshot(0x1)
        assertEquals(AutomationSignalValue.State("on"), espGpioInputFlow(0).first())
        assertEquals(AutomationSignalValue.State("off"), espGpioInputFlow(1).first())

        EspCompanionRepository.applyGpioEvent(0, false)
        assertEquals(AutomationSignalValue.State("off"), espGpioInputFlow(0).first())
    }

    @Test
    fun reconnect_hidesStaleMaskUntilNextSnapshot() = runBlocking {
        EspCompanionRepository.updateConnected(true)
        EspCompanionRepository.confirmGpioSnapshot(0x1)
        assertEquals(AutomationSignalValue.State("on"), espGpioInputFlow(0).first())

        EspCompanionRepository.updateConnected(false)
        assertFalse(EspCompanionRepository.gpioInputsReady.value)
        assertEquals(AutomationSignalValue.Unavailable, espGpioInputFlow(0).first())

        EspCompanionRepository.confirmGpioSnapshot(0x1)
        assertFalse(EspCompanionRepository.gpioInputsReady.value)
        assertEquals(AutomationSignalValue.Unavailable, espGpioInputFlow(0).first())

        EspCompanionRepository.updateConnected(true)
        assertEquals(AutomationSignalValue.Unavailable, espGpioInputFlow(0).first())
        EspCompanionRepository.confirmGpioSnapshot(0x1)
        assertEquals(AutomationSignalValue.State("on"), espGpioInputFlow(0).first())
    }
}
