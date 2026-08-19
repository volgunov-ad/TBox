package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadMatchGnssTrustTest {
    @Test
    fun noLiveGnssMeansZeroTrust() {
        assertEquals(0f, RoadMatchGnssTrust.fromLive(liveGnss = false, accuracyM = 5f))
    }

    @Test
    fun poorAccuracyMeansZeroTrust() {
        assertEquals(0f, RoadMatchGnssTrust.fromLive(liveGnss = true, accuracyM = 25f))
    }

    @Test
    fun largeShadowGapMeansZeroTrust() {
        assertEquals(
            0f,
            RoadMatchGnssTrust.fromLive(
                liveGnss = true,
                accuracyM = 5f,
                shadowGnssGapM = RoadMatchGnssTrust.MAX_SHADOW_GAP_M + 1.0,
            ),
        )
    }

    @Test
    fun goodAccuracyYieldsPositiveTrust() {
        val trust = RoadMatchGnssTrust.fromLive(liveGnss = true, accuracyM = 5f)
        assertTrue(trust >= 0.9f)
    }

    @Test
    fun classPenaltyScaleAtFullTrust() {
        val scale = RoadMatchGnssTrust.classPenaltyScale(1f)
        assertEquals(1.0 - RoadMatchGnssTrust.CLASS_PENALTY_RELAX, scale, 1e-6)
    }

    @Test
    fun classPenaltyScaleAtZeroTrust() {
        assertEquals(1.0, RoadMatchGnssTrust.classPenaltyScale(0f), 1e-6)
    }
}
