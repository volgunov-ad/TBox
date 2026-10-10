package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalDecoderValuesTest {
    @Test
    fun drlAndTrunkHazard_fromAfternoonTrunkOpen() {
        assertEquals(true, BcmLightStatusDomain.decodeDrlOn(2))
        assertEquals(false, BcmLightStatusDomain.decodeDrlOn(1))
        assertNull(BcmLightStatusDomain.decodeDrlOn(0))
        assertEquals(true, BcmLightStatusDomain.decodeTrunkOpenHazard(3, 1))
        assertEquals(false, BcmLightStatusDomain.decodeTrunkOpenHazard(0, 0))
        assertNull(BcmLightStatusDomain.decodeTrunkOpenHazard(2, 2))
        assertNull(BcmLightStatusDomain.decodeTrunkOpenHazard(1, 1))
    }

    @Test
    fun rcta_rightWarningIsRawTwo() {
        assertEquals(false, RctaAlarmDomain.decodeWarning(1))
        assertEquals(true, RctaAlarmDomain.decodeWarning(2))
        assertNull(RctaAlarmDomain.decodeWarning(0))
    }

    @Test
    fun avh_holdPhaseWhileFunctionStaysOn() {
        assertEquals(AvhHoldPhase.Holding, AvhDomain.decodeHoldPhase(1))
        assertEquals(AvhHoldPhase.Standby, AvhDomain.decodeHoldPhase(2))
        assertNull(AvhDomain.decodeHoldPhase(0))
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeAvhHdcStatusRaw(1))
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeAvhHdcStatusRaw(2))
    }

    @Test
    fun wpc_incompatiblePhoneIsNotACharge() {
        assertEquals(false, WpcStatusDomain.decodeWirelessPhoneDetected(0))
        assertNull(WpcStatusDomain.decodeWirelessPhoneDetected(1))
        assertEquals(false, WpcStatusDomain.decodeCharging(9))
        assertNull(WpcStatusDomain.decodeCharging(1))
    }

    @Test
    fun driveMode_epsFollowsObservedPairs() {
        assertEquals(1, DriveModeFollowDomain.epsRawFollowingDriveMode(0))
        assertEquals(2, DriveModeFollowDomain.epsRawFollowingDriveMode(2))
        assertNull(DriveModeFollowDomain.epsRawFollowingDriveMode(1))
    }

    @Test
    fun accTimeGap_requestFourIsNotAGap() {
        assertEquals(AccTimeGap.Medium, CarSettingsAdasDomain.decodeAccTimeGapRequest(1))
        assertEquals(AccTimeGap.Far, CarSettingsAdasDomain.decodeAccTimeGapRequest(2))
        assertEquals(AccTimeGap.Near, CarSettingsAdasDomain.decodeAccTimeGapRequest(3))
        assertNull(CarSettingsAdasDomain.decodeAccTimeGapRequest(4))
        assertEquals(AccTimeGap.Medium, CarSettingsAdasDomain.decodeAccTimeGapStatus(0))
        assertEquals(AccTimeGap.Far, CarSettingsAdasDomain.decodeAccTimeGapStatus(1))
        assertEquals(AccTimeGap.Near, CarSettingsAdasDomain.decodeAccTimeGapStatus(2))
        assertNull(CarSettingsAdasDomain.decodeAccTimeGapStatus(3))
    }
}
