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
        assertTrue(signals.contains(MbCanSignal.AudioVolumeSpeed))
        assertTrue(signals.contains(MbCanSignal.TjaIca))
        assertTrue(signals.contains(MbCanSignal.LasModeSelection))
        assertTrue(signals.contains(MbCanSignal.BodyComfort))
        assertTrue(signals.any { it.subscribeDataTypes.contains("eMBCAN_CFG_AUDIO") })
        assertTrue(signals.any { it.subscribeDataTypes.contains("eMBCAN_CFG_VEHICLE") })
        // Union is larger than any single section (Audio has 9 CAN signals after platform mixer).
        assertTrue(signals.size > 20)
    }

    @Test
    fun set_holdsThroughFailedDecodeLikeVhalLasMode() {
        val flow = MutableStateFlow<Int?>(MbCanKnownVehiclePropertyId.LAS_MODE_LKA)
        // Same shape as Android10VhalRepository poll: HoldLastKnown.set(flow, raw?.let(::decodeLasModeRaw))
        HoldLastKnown.set(flow, null?.let { MbCanSignalStateEngine.decodeLasModeRaw(it) })
        assertEquals(MbCanKnownVehiclePropertyId.LAS_MODE_LKA, flow.value)
        HoldLastKnown.set(flow, MbCanSignalStateEngine.decodeLasModeRaw(-1))
        assertEquals(MbCanKnownVehiclePropertyId.LAS_MODE_LKA, flow.value)
        HoldLastKnown.set(flow, MbCanSignalStateEngine.decodeLasModeRaw(MbCanKnownVehiclePropertyId.LAS_MODE_OFF))
        assertEquals(MbCanKnownVehiclePropertyId.LAS_MODE_OFF, flow.value)
    }
}
