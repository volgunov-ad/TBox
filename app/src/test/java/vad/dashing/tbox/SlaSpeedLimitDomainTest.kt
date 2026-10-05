package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.SlaSignUiState
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
        assertEquals(130, SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(28))
    }

    @Test
    fun resolveSlaSignUiState_matchesStockAdasCard() {
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 1, slaStateRaw = 1, slaLimitRaw = 5),
        )
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 0, slaLimitRaw = 5),
        )
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 1, slaLimitRaw = 0),
        )
        assertEquals(
            SlaSignUiState.EndOfRestriction,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 1, slaLimitRaw = 1),
        )
        assertEquals(
            SlaSignUiState.EndOfRestriction,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 2, slaLimitRaw = 1),
        )
        assertEquals(
            SlaSignUiState.EndOfRestriction,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 3, slaLimitRaw = 1),
        )
        assertEquals(
            SlaSignUiState.Limit(60),
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 2, slaLimitRaw = 13),
        )
        assertEquals(
            SlaSignUiState.Limit(130),
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 1, slaLimitRaw = 27),
        )
        assertEquals(
            SlaSignUiState.Limit(130),
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 1, slaLimitRaw = 28),
        )
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 4, slaLimitRaw = 13),
        )
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 0, slaStateRaw = 1, slaLimitRaw = 5),
        )
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 3, slaStateRaw = 1, slaLimitRaw = 5),
        )
    }

    @Test
    fun resolveSlaSignUiState_nullInputsAreInactive() {
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = null, slaStateRaw = 1, slaLimitRaw = 5),
        )
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = null, slaLimitRaw = 5),
        )
        assertEquals(
            SlaSignUiState.Inactive,
            SlaSpeedLimitDomain.resolveSlaSignUiState(slaOnOffRaw = 2, slaStateRaw = 1, slaLimitRaw = null),
        )
    }

    @Test
    fun decodeSlaOnOffRaw_mapsBinaryStates() {
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSlaOnOffRaw(1))
        assertEquals(MbCanBinaryState.On, SlaSpeedLimitDomain.decodeSlaOnOffRaw(2))
        assertEquals(MbCanBinaryState.Unknown, SlaSpeedLimitDomain.decodeSlaOnOffRaw(0))
    }

    @Test
    fun encodeSlaSwitchOn_roundTripsThroughMbCanDecode() {
        assertEquals(
            MbCanBinaryState.On,
            SlaSpeedLimitDomain.decodeSlaOnOffRaw(SlaSpeedLimitDomain.encodeSlaSwitchOn(true)),
        )
        assertEquals(
            MbCanBinaryState.Off,
            SlaSpeedLimitDomain.decodeSlaOnOffRaw(SlaSpeedLimitDomain.encodeSlaSwitchOn(false)),
        )
    }

    @Test
    fun decodeSlaOnOffVhalRaw_mapsBinaryStates() {
        assertEquals(MbCanBinaryState.On, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(1))
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(0))
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(2))
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(3))
    }

    @Test
    fun encodeSlaSwitchOn_doesNotRoundTripThroughCurrentVhalDecode() {
        // Documents known A10 asymmetry: write uses mbCAN 1/2, VHAL status decode treats only raw==1 as On.
        // AdasCard / sign UI treat FCM OnOffsts==2 as enabled; toggle decode currently disagrees.
        val writtenOn = SlaSpeedLimitDomain.encodeSlaSwitchOn(true)
        assertEquals(2, writtenOn)
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSlaOnOffVhalRaw(writtenOn))
    }

    @Test
    fun decodeSpeedLimiterSwitchRaw_mapsBinaryStates() {
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSpeedLimiterSwitchRaw(1))
        assertEquals(MbCanBinaryState.On, SlaSpeedLimitDomain.decodeSpeedLimiterSwitchRaw(2))
        assertEquals(MbCanBinaryState.Unknown, SlaSpeedLimitDomain.decodeSpeedLimiterSwitchRaw(0))
    }

    @Test
    fun encodeSpeedLimiterSwitchOn_roundTripsThroughMbCanDecode() {
        assertEquals(
            MbCanBinaryState.On,
            SlaSpeedLimitDomain.decodeSpeedLimiterSwitchRaw(
                SlaSpeedLimitDomain.encodeSpeedLimiterSwitchOn(true),
            ),
        )
        assertEquals(
            MbCanBinaryState.Off,
            SlaSpeedLimitDomain.decodeSpeedLimiterSwitchRaw(
                SlaSpeedLimitDomain.encodeSpeedLimiterSwitchOn(false),
            ),
        )
    }

    @Test
    fun decodeSpeedLimiterSwitchVhalRaw_mapsBinaryStates() {
        assertEquals(MbCanBinaryState.On, SlaSpeedLimitDomain.decodeSpeedLimiterSwitchVhalRaw(1))
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSpeedLimiterSwitchVhalRaw(0))
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSpeedLimiterSwitchVhalRaw(2))
        assertEquals(MbCanBinaryState.Off, SlaSpeedLimitDomain.decodeSpeedLimiterSwitchVhalRaw(3))
    }

    @Test
    fun clampLimiterTargetKmh_roundsToStepAndAllowsZero() {
        assertEquals(0, SlaSpeedLimitDomain.clampLimiterTargetKmh(0))
        assertEquals(5, SlaSpeedLimitDomain.clampLimiterTargetKmh(3))
        assertEquals(5, SlaSpeedLimitDomain.clampLimiterTargetKmh(7))
        assertEquals(60, SlaSpeedLimitDomain.clampLimiterTargetKmh(62))
        assertEquals(150, SlaSpeedLimitDomain.clampLimiterTargetKmh(200))
        assertEquals(150, SlaSpeedLimitDomain.clampLimiterTargetKmh(148))
    }

    @Test
    fun stepLimiterTargetKmh_movesByFiveKmh() {
        assertEquals(55, SlaSpeedLimitDomain.stepLimiterTargetKmh(60, increase = false))
        assertEquals(65, SlaSpeedLimitDomain.stepLimiterTargetKmh(60, increase = true))
        assertEquals(0, SlaSpeedLimitDomain.stepLimiterTargetKmh(5, increase = false))
        assertEquals(150, SlaSpeedLimitDomain.stepLimiterTargetKmh(150, increase = true))
    }

    @Test
    fun nextLimiterTargetFromCan_bootstrapsWhenNoData() {
        assertEquals(
            SlaSpeedLimitDomain.SPEED_LIMITER_KMH_BOOTSTRAP,
            SlaSpeedLimitDomain.nextLimiterTargetFromCan(null, increase = true),
        )
        assertEquals(
            SlaSpeedLimitDomain.SPEED_LIMITER_KMH_BOOTSTRAP,
            SlaSpeedLimitDomain.nextLimiterTargetFromCan(null, increase = false),
        )
        assertEquals(35, SlaSpeedLimitDomain.nextLimiterTargetFromCan(30, increase = true))
        assertEquals(25, SlaSpeedLimitDomain.nextLimiterTargetFromCan(30, increase = false))
        assertEquals(0, SlaSpeedLimitDomain.nextLimiterTargetFromCan(5, increase = false))
        var live = 30
        repeat(3) {
            live = SlaSpeedLimitDomain.nextLimiterTargetFromCan(live, increase = true)
        }
        assertEquals(45, live)
    }

    @Test
    fun resolveLimiterTargetOrBootstrap_usesLiveOrBootstrap() {
        assertEquals(
            SlaSpeedLimitDomain.SPEED_LIMITER_KMH_BOOTSTRAP,
            SlaSpeedLimitDomain.resolveLimiterTargetOrBootstrap(null),
        )
        assertEquals(60, SlaSpeedLimitDomain.resolveLimiterTargetOrBootstrap(62))
        assertEquals(0, SlaSpeedLimitDomain.resolveLimiterTargetOrBootstrap(0))
    }
}
