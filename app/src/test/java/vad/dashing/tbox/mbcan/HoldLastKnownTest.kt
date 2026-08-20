package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.ui.carSettingsTabMbCanSignals

class HoldLastKnownTest {
    @Test
    fun set_ignoresNullAndKeepsPrevious() {
        val flow = MutableStateFlow<Int?>(null)
        HoldLastKnown.set(flow, 2)
        assertEquals(2, flow.value)
        HoldLastKnown.set(flow, null)
        assertEquals(2, flow.value)
        HoldLastKnown.set(flow, 1)
        assertEquals(1, flow.value)
    }

    @Test
    fun decodeLasModeRaw_rejectsSentinelNegOne() {
        assertNull(MbCanSignalStateEngine.decodeLasModeRaw(-1))
        assertNull(MbCanSignalStateEngine.decodeLasModeRaw(0))
        assertEquals(MbCanKnownVehiclePropertyId.LAS_MODE_LDW, MbCanSignalStateEngine.decodeLasModeRaw(1))
    }

    @Test
    fun carSettingsTabMbCanSignals_coversAudioAndAdasCfgTypes() {
        val signals = carSettingsTabMbCanSignals()
        assertTrue(signals.contains(MbCanSignal.AudioVolume))
        assertTrue(signals.contains(MbCanSignal.TjaIca))
        assertTrue(signals.contains(MbCanSignal.LasModeSelection))
        assertTrue(signals.any { it.subscribeDataTypes.contains("eMBCAN_CFG_AUDIO") })
        assertTrue(signals.any { it.subscribeDataTypes.contains("eMBCAN_CFG_VEHICLE") })
        // Union is larger than any single section (Audio has 10 signals).
        assertTrue(signals.size > 20)
    }
}
