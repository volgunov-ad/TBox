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
    fun encodeHvacFrontOffWrite_matchesStatusEncoding() {
        assertEquals(2, HvacClimateDomain.encodeHvacFrontOffMbCanWrite(targetClimateOn = true))
        assertEquals(1, HvacClimateDomain.encodeHvacFrontOffMbCanWrite(targetClimateOn = false))
    }
}
