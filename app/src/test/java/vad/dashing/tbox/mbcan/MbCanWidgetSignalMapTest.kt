package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.GAS_BRAKE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_AC_MAX_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HMA_WIDGET_DATA_KEY
import vad.dashing.tbox.LDW_WIDGET_DATA_KEY
import vad.dashing.tbox.LKA_WIDGET_DATA_KEY
import vad.dashing.tbox.TJA_ICA_WIDGET_DATA_KEY
import vad.dashing.tbox.TRUNK_DOOR_WIDGET_DATA_KEY

class MbCanWidgetSignalMapTest {
    @Test
    fun previouslyMissingA10GateKeys_mapToSignals() {
        assertEquals(MbCanSignal.LasModeSelection, MbCanWidgetSignalMap.signalFor(LDW_WIDGET_DATA_KEY))
        assertEquals(MbCanSignal.LasModeSelection, MbCanWidgetSignalMap.signalFor(LKA_WIDGET_DATA_KEY))
        assertEquals(MbCanSignal.TjaIca, MbCanWidgetSignalMap.signalFor(TJA_ICA_WIDGET_DATA_KEY))
        assertEquals(MbCanSignal.HmaSwitch, MbCanWidgetSignalMap.signalFor(HMA_WIDGET_DATA_KEY))
        assertEquals(MbCanSignal.HvacAcMax, MbCanWidgetSignalMap.signalFor(HVAC_AC_MAX_WIDGET_DATA_KEY))
        assertEquals(MbCanSignal.TrunkDoor, MbCanWidgetSignalMap.signalFor(TRUNK_DOOR_WIDGET_DATA_KEY))
        assertEquals(MbCanSignal.GasPedal, MbCanWidgetSignalMap.signalFor(GAS_BRAKE_WIDGET_DATA_KEY))
    }

    @Test
    fun panelNeedsCan_trueWhenLonePreviouslyGatedWidget() {
        listOf(
            LDW_WIDGET_DATA_KEY,
            LKA_WIDGET_DATA_KEY,
            TJA_ICA_WIDGET_DATA_KEY,
            HMA_WIDGET_DATA_KEY,
            HVAC_AC_MAX_WIDGET_DATA_KEY,
            TRUNK_DOOR_WIDGET_DATA_KEY,
        ).forEach { key ->
            assertTrue(key, MbCanWidgetSignalMap.panelNeedsCan(listOf(key)))
        }
    }

    @Test
    fun panelNeedsCan_falseForUnknownOrBlank() {
        assertFalse(MbCanWidgetSignalMap.panelNeedsCan(listOf("speedWidget")))
        assertFalse(MbCanWidgetSignalMap.panelNeedsCan(listOf("")))
        assertFalse(MbCanWidgetSignalMap.panelNeedsCan(listOf("null")))
    }

    @Test
    fun climatePanel_addsHvacFrontOffPiggyback() {
        val signals = MbCanWidgetSignalMap.signalsForNormalizedKeys(
            listOf(HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY),
        )
        assertTrue(signals.contains(MbCanSignal.HvacCustomMode))
        assertTrue(signals.contains(MbCanSignal.HvacFrontOff))
    }

    @Test
    fun gasBrakeWidget_subscribesGasAndBrake() {
        assertTrue(MbCanWidgetSignalMap.panelNeedsCan(listOf(GAS_BRAKE_WIDGET_DATA_KEY)))
        val signals = MbCanWidgetSignalMap.signalsForNormalizedKeys(listOf(GAS_BRAKE_WIDGET_DATA_KEY))
        assertTrue(signals.contains(MbCanSignal.GasPedal))
        assertTrue(signals.contains(MbCanSignal.BrakePedal))
    }
}
