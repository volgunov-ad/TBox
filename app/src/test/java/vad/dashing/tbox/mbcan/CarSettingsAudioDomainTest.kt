package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarSettingsAudioDomainTest {
    @Test fun volumeSpeedMbCan_mapsZeroBasedRawValuesToSharedUiLevels() {
        assertEquals(1, CarSettingsAudioDomain.decodeVolumeSpeedMbCan(0))
        assertEquals(2, CarSettingsAudioDomain.decodeVolumeSpeedMbCan(1))
        assertEquals(3, CarSettingsAudioDomain.decodeVolumeSpeedMbCan(2))
        assertEquals(4, CarSettingsAudioDomain.decodeVolumeSpeedMbCan(3))
        assertNull(CarSettingsAudioDomain.decodeVolumeSpeedMbCan(4))

        assertEquals(0, CarSettingsAudioDomain.encodeVolumeSpeedMbCan(1))
        assertEquals(1, CarSettingsAudioDomain.encodeVolumeSpeedMbCan(2))
        assertEquals(2, CarSettingsAudioDomain.encodeVolumeSpeedMbCan(3))
        assertEquals(3, CarSettingsAudioDomain.encodeVolumeSpeedMbCan(4))
        assertNull(CarSettingsAudioDomain.encodeVolumeSpeedMbCan(0))
    }

    @Test fun volumeSpeedVhal_preservesOneBasedUiLevels() {
        for (level in CarSettingsAudioDomain.volumeSpeedUiRange) {
            assertEquals(level, CarSettingsAudioDomain.decodeVolumeSpeedVhal(level))
            assertEquals(level, CarSettingsAudioDomain.encodeVolumeSpeedVhal(level))
        }
        assertNull(CarSettingsAudioDomain.decodeVolumeSpeedVhal(0))
        assertNull(CarSettingsAudioDomain.encodeVolumeSpeedVhal(5))
    }

    @Test fun volumeSpeedBinaryState_usesTheBackendSpecificOffValue() {
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeVolumeSpeedMbCanRaw(0))
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeVolumeSpeedMbCanRaw(3))
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeVolumeSpeedVhalRaw(1))
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeVolumeSpeedVhalRaw(4))
    }

    @Test fun keyToneAndRadarAlarm_acceptOnlyStockA9RawValues() {
        val keyTone = MbCanAudioCommandRegistry
            .get(MbCanKnownAudioPropertyId.VOLUME_KEY)!!.policy as MbCanCommandPolicy.SetExact
        val radarAlarm = MbCanAudioCommandRegistry
            .get(MbCanKnownAudioPropertyId.VOLUME_RADAR)!!.policy as MbCanCommandPolicy.SetExact

        assertEquals(setOf(0, 1, 2, 3), keyTone.allowedValues)
        assertEquals(setOf(1, 2, 3), radarAlarm.allowedValues)
    }

    @Test fun eqModes_matchStockA9AudioViewModel() {
        assertEquals(CarSettingsAudioDomain.EQ_MODE_POP, CarSettingsAudioDomain.decodeEqMode(1))
        assertEquals(CarSettingsAudioDomain.EQ_MODE_ROCK, CarSettingsAudioDomain.decodeEqMode(2))
        assertEquals(CarSettingsAudioDomain.EQ_MODE_JAZZ, CarSettingsAudioDomain.decodeEqMode(3))
        assertEquals(CarSettingsAudioDomain.EQ_MODE_CLASSIC, CarSettingsAudioDomain.decodeEqMode(4))
        assertEquals(CarSettingsAudioDomain.EQ_MODE_VOICE, CarSettingsAudioDomain.decodeEqMode(5))
        assertEquals(CarSettingsAudioDomain.EQ_MODE_CUSTOM, CarSettingsAudioDomain.decodeEqMode(255))
        assertNull(CarSettingsAudioDomain.decodeEqMode(0))
    }

    @Test fun eqBands_andBalanceFader_enforceStockRangesAndOffset() {
        assertEquals(-7, CarSettingsAudioDomain.encodeEqBand(-7))
        assertEquals(7, CarSettingsAudioDomain.decodeEqBand(7))
        assertNull(CarSettingsAudioDomain.encodeEqBand(8))

        assertEquals(0, CarSettingsAudioDomain.decodeBalanceFader(7))
        assertEquals(-7, CarSettingsAudioDomain.decodeBalanceFader(0))
        assertEquals(7, CarSettingsAudioDomain.decodeBalanceFader(14))
        assertEquals(0, CarSettingsAudioDomain.encodeBalanceFader(-7))
        assertEquals(7, CarSettingsAudioDomain.encodeBalanceFader(0))
        assertEquals(14, CarSettingsAudioDomain.encodeBalanceFader(7))
        assertNull(CarSettingsAudioDomain.encodeBalanceFader(8))
    }
}
