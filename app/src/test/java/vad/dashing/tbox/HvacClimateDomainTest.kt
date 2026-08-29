package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.HvacBlowMode
import vad.dashing.tbox.mbcan.HvacClimateDomain
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class HvacClimateDomainTest {

    @Test
    fun mbCanTempRawToCelsius_convertsTenths() {
        assertEquals(22.0f, HvacClimateDomain.mbCanTempRawToCelsius(220)!!, 0.001f)
        assertNull(HvacClimateDomain.mbCanTempRawToCelsius(150))
    }

    @Test
    fun vhalTempRawToCelsius_convertsHalves() {
        assertEquals(22.0f, HvacClimateDomain.vhalTempRawToCelsius(44)!!, 0.001f)
        assertNull(HvacClimateDomain.vhalTempRawToCelsius(20))
    }

    @Test
    fun celsiusRoundTrip_mbCanAndVhal() {
        val celsius = 23.5f
        val mbCan = HvacClimateDomain.celsiusToMbCanTempRaw(celsius)
        val vhal = HvacClimateDomain.mbCanTempRawToVhalWrite(mbCan)!!
        assertEquals(celsius, HvacClimateDomain.vhalTempRawToCelsius(vhal)!!, 0.001f)
    }

    @Test
    fun blowModeMapping_mbCanToVhalAndBack() {
        val modes = listOf(
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE to 0,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT to 2,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT to 1,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST to 4,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT to 3,
        )
        modes.forEach { (mbCan, vhal) ->
            assertEquals(vhal, HvacClimateDomain.mbCanBlowModeToVhalWrite(mbCan))
            assertEquals(mbCan, HvacClimateDomain.vhalBlowModeToMbCan(vhal))
        }
    }

    @Test
    fun blowModeCycle_order() {
        val first = HvacBlowMode.nextInCycle(null)
        assertEquals(HvacBlowMode.Face, first)
        val second = HvacBlowMode.nextInCycle(first)
        assertEquals(HvacBlowMode.Foot, second)
    }

    @Test
    fun decodeHvacFrontOffSts_climateOnIsOffState() {
        assertEquals(MbCanBinaryState.Off, HvacClimateDomain.decodeHvacFrontOffMbCanRaw(2))
        assertEquals(MbCanBinaryState.On, HvacClimateDomain.decodeHvacFrontOffMbCanRaw(1))
    }

    @Test
    fun decodeHvacFrontOffVhalRaw_matchesStockZeroIsOn() {
        assertEquals(MbCanBinaryState.On, HvacClimateDomain.decodeHvacFrontOffVhalRaw(0))
        assertEquals(MbCanBinaryState.Off, HvacClimateDomain.decodeHvacFrontOffVhalRaw(1))
        assertEquals(MbCanBinaryState.Off, HvacClimateDomain.decodeHvacFrontOffVhalRaw(2))
        assertEquals(MbCanBinaryState.Off, HvacClimateDomain.decodeHvacFrontOffVhalRaw(3))
    }

    @Test
    fun decodeHvacSyncVhalRaw_matchesStockOneIsOn() {
        assertEquals(MbCanBinaryState.On, HvacClimateDomain.decodeHvacSyncVhalRaw(1))
        assertEquals(MbCanBinaryState.Off, HvacClimateDomain.decodeHvacSyncVhalRaw(0))
        assertEquals(MbCanBinaryState.Off, HvacClimateDomain.decodeHvacSyncVhalRaw(2))
        assertEquals(MbCanBinaryState.Off, HvacClimateDomain.decodeHvacSyncVhalRaw(3))
    }

    @Test
    fun encodeHvacFrontOffWrite_matchesStatusEncoding() {
        assertEquals(2, HvacClimateDomain.encodeHvacFrontOffMbCanWrite(targetClimateOn = true))
        assertEquals(1, HvacClimateDomain.encodeHvacFrontOffMbCanWrite(targetClimateOn = false))
    }

    @Test
    fun adjustCelsius_halfStepKeepsHalfDegreeGrid() {
        assertEquals(23.0f, HvacClimateDomain.adjustCelsius(22.5f, increase = true), 0.001f)
        assertEquals(22.0f, HvacClimateDomain.adjustCelsius(22.5f, increase = false), 0.001f)
        assertEquals(22.5f, HvacClimateDomain.adjustCelsius(22.0f, increase = true), 0.001f)
    }

    @Test
    fun adjustCelsius_wholeStepSnapsHalfDegreeThenMovesByOne() {
        val step = 10
        assertEquals(23.0f, HvacClimateDomain.adjustCelsius(22.5f, increase = true, step), 0.001f)
        assertEquals(22.0f, HvacClimateDomain.adjustCelsius(22.5f, increase = false, step), 0.001f)
        assertEquals(24.0f, HvacClimateDomain.adjustCelsius(23.0f, increase = true, step), 0.001f)
        assertEquals(21.0f, HvacClimateDomain.adjustCelsius(22.0f, increase = false, step), 0.001f)
    }

    @Test
    fun adjustCelsius_clampsToAllowedRange() {
        val step = 10
        assertEquals(16.0f, HvacClimateDomain.adjustCelsius(16.0f, increase = false, step), 0.001f)
        assertEquals(16.0f, HvacClimateDomain.adjustCelsius(16.5f, increase = false, step), 0.001f)
        assertEquals(30.0f, HvacClimateDomain.adjustCelsius(30.0f, increase = true, step), 0.001f)
        assertEquals(30.0f, HvacClimateDomain.adjustCelsius(29.5f, increase = true, step), 0.001f)
        assertEquals(16.0f, HvacClimateDomain.adjustCelsius(null, increase = false), 0.001f)
        assertEquals(16.5f, HvacClimateDomain.adjustCelsius(null, increase = true), 0.001f)
    }

    @Test
    fun normalizeTempStepTenths_unknownFallsBackToHalf() {
        assertEquals(5, HvacClimateDomain.normalizeTempStepTenths(99))
        assertEquals(10, HvacClimateDomain.normalizeTempStepTenths(10))
        assertEquals(5, HvacClimateDomain.normalizeTempStepTenths(5))
    }
}
