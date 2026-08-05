package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarSettingsLocksLightsDomainTest {
    @Test fun followMeHome_normalizesBothBackends() {
        assertEquals(FollowMeHomeMode.Sec30, FollowMeHomeMode.fromMbCanRaw(30))
        assertEquals(FollowMeHomeMode.Sec60, FollowMeHomeMode.fromVhalRaw(2))
        assertEquals(FollowMeHomeMode.Off, FollowMeHomeMode.fromVhalRaw(3))
        assertNull(FollowMeHomeMode.fromMbCanRaw(1))
    }

    @Test fun vhalFeedback_isNormalizedToUiValues() {
        assertEquals(1, CarSettingsLocksLightsDomain.decodeLowBeamHeightVhal(3))
        assertEquals(4, CarSettingsLocksLightsDomain.decodeLowBeamHeightVhal(0))
        assertEquals(4, CarSettingsLocksLightsDomain.encodeLowBeamHeightVhal(1))
        assertEquals(1, CarSettingsLocksLightsDomain.decodeTurnFlashCountVhal(0))
        assertEquals(3, CarSettingsLocksLightsDomain.decodeRemoteLockFeedbackVhal(2))
        assertEquals(1, CarSettingsLocksLightsDomain.decodeRemoteLockFeedbackVhal(0))
        assertEquals(2, CarSettingsLocksLightsDomain.encodeRemoteLockFeedbackVhal(2))
        assertNull(CarSettingsLocksLightsDomain.encodeRemoteLockFeedbackVhal(0))
    }
}
