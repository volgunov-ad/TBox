package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.SlaSpeedLimitDomain

class SlaSpeedLimitDomainTest {
    @Test
    fun decodeRecognizedSpeedKmh_mapsFormula() {
        assertNull(SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(0))
        assertNull(SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(1))
        assertEquals(5, SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(2))
        assertEquals(10, SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(3))
        assertEquals(30, SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(7))
        assertEquals(50, SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(11))
        assertEquals(110, SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(23))
    }

    @Test
    fun decodeSlaOnOffRaw_mapsBinaryStates() {
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSlaOnOffRaw(1))
        assertEquals(MbCanBinaryState.On, SlaSpeedLimitDomain.decodeSlaOnOffRaw(2))
        assertEquals(MbCanBinaryState.Unknown, SlaSpeedLimitDomain.decodeSlaOnOffRaw(0))
    }

    @Test
    fun decodeSlaOnOffVhalRaw_mapsBinaryStates() {
        assertEquals(MbCanBinaryState.On, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(1))
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(0))
        assertEquals(MbCanBinaryState.Unknown, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(2))
    }

    @Test
    fun clampLimiterTargetKmh_roundsToStepAndAllowsZero() {
        assertEquals(0, SlaSpeedLimitDomain.clampLimiterTargetKmh(0))
        assertEquals(5, SlaSpeedLimitDomain.clampLimiterTargetKmh(3))
        assertEquals(60, SlaSpeedLimitDomain.clampLimiterTargetKmh(62))
        assertEquals(150, SlaSpeedLimitDomain.clampLimiterTargetKmh(200))
        assertEquals(150, SlaSpeedLimitDomain.clampLimiterTargetKmh(148))
    }

    @Test
    fun stepLimiterTargetKmh_movesByFiveKmh() {
        assertEquals(55, SlaSpeedLimitDomain.stepLimiterTargetKmh(60, increase = false))
        assertEquals(65, SlaSpeedLimitDomain.stepLimiterTargetKmh(60, increase = true))
        assertEquals(0, SlaSpeedLimitDomain.stepLimiterTargetKmh(5, increase = false))
    }
}
