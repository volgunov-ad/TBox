package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadarSensorDomainTest {
    @Test
    fun decodeWorking_offAndOn() {
        assertEquals(false, RadarSensorDomain.decodeWorking(0))
        assertEquals(true, RadarSensorDomain.decodeWorking(1))
        assertNull(RadarSensorDomain.decodeWorking(2))
        assertNull(RadarSensorDomain.decodeWorking(-1))
    }

    @Test
    fun decodeObjectPresent_oneAndTwoAreTheSame() {
        assertEquals(false, RadarSensorDomain.decodeObjectPresent(0))
        assertEquals(true, RadarSensorDomain.decodeObjectPresent(1))
        assertEquals(true, RadarSensorDomain.decodeObjectPresent(2))
        assertNull(RadarSensorDomain.decodeObjectPresent(3))
    }

    @Test
    fun decodeBeepSounding_doesNotRankRates() {
        assertEquals(false, RadarSensorDomain.decodeBeepSounding(0))
        assertEquals(true, RadarSensorDomain.decodeBeepSounding(1))
        assertEquals(true, RadarSensorDomain.decodeBeepSounding(2))
        assertEquals(true, RadarSensorDomain.decodeBeepSounding(4))
        assertNull(RadarSensorDomain.decodeBeepSounding(-1))
    }

    @Test
    fun decodeDistanceRaw_frontAndRearSentinels() {
        assertNull(RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHF, 175))
        assertNull(RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.RHMF, 175))
        assertEquals(60, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHF, 60))
        assertEquals(35, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.RHF, 35))
        assertNull(RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHR, 335))
        assertNull(RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHMR, 335))
        assertEquals(145, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHR, 145))
        assertEquals(65, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.RHMR, 65))
        assertNull(RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHF, -1))
    }

    @Test
    fun decodeDistanceRaw_sideSensorsKeepTheirOwnScale() {
        assertNull(RadarSensorDomain.noTargetRaw(RadarSensorDomain.Sensor.LHSF))
        assertEquals(1, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHSF, 1))
        assertEquals(24, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.RHSR, 24))
        assertEquals(175, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.LHSR, 175))
        assertEquals(335, RadarSensorDomain.decodeDistanceRaw(RadarSensorDomain.Sensor.RHSF, 335))
    }
}
