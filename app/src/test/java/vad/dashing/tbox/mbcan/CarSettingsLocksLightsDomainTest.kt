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
        assertEquals(
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT_HORN,
            CarSettingsLocksLightsDomain.decodeRemoteLockFeedbackVhal(0),
        )
        assertEquals(
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT,
            CarSettingsLocksLightsDomain.decodeRemoteLockFeedbackVhal(1),
        )
        assertEquals(
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_HORN,
            CarSettingsLocksLightsDomain.decodeRemoteLockFeedbackVhal(2),
        )
        assertEquals(2, CarSettingsLocksLightsDomain.encodeRemoteLockFeedbackVhal(
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT,
        ))
        assertEquals(3, CarSettingsLocksLightsDomain.encodeRemoteLockFeedbackVhal(
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_HORN,
        ))
        assertEquals(1, CarSettingsLocksLightsDomain.encodeRemoteLockFeedbackVhal(
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT_HORN,
        ))
        assertNull(CarSettingsLocksLightsDomain.encodeRemoteLockFeedbackVhal(0))
        assertNull(CarSettingsLocksLightsDomain.decodeRemoteLockFeedbackVhal(3))
    }

    @Test fun turnFlashCount_stockLabelsAreThreeFiveSeven() {
        assertEquals(3, CarSettingsLocksLightsDomain.turnFlashCountBlinks(1))
        assertEquals(5, CarSettingsLocksLightsDomain.turnFlashCountBlinks(2))
        assertEquals(7, CarSettingsLocksLightsDomain.turnFlashCountBlinks(3))
        assertNull(CarSettingsLocksLightsDomain.turnFlashCountBlinks(0))
        assertEquals(1, CarSettingsLocksLightsDomain.decodeTurnFlashCountVhal(0))
        assertEquals(3, CarSettingsLocksLightsDomain.decodeTurnFlashCountVhal(2))
    }
}
