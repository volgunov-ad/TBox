package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.location.roadmatch.RoadMatchGnssTrust

/**
 * Investigation pins for GNSS truth-loss / retention (field note: crashes while DR
 * runs without fix). Production code is not changed.
 *
 * GNSS loss does not load more road tiles; it does flip mock injection under
 * [MockPowerState.WHEN_NO_FIX] and grows published accuracy while retaining.
 */
class GnssTruthLossRetentionContractTest {

    @Test
    fun whenNoFix_injectsOnlyWhileGnssNotTruthful() {
        assertTrue(MockLocationJob.shouldInjectWhenNoFix(gnssTruthful = false))
        assertFalse(MockLocationJob.shouldInjectWhenNoFix(gnssTruthful = true))
    }

    @Test
    fun whenNoFix_pushMockStaysArmedForTboxSource() {
        // Provider is armed; inject cadence still gated by shouldInjectWhenNoFix.
        assertTrue(
            MockLocationJob.shouldPushMock(MockPowerState.WHEN_NO_FIX, LocationSource.TBOX),
        )
    }

    @Test
    fun truthLoss_flapModel_countsProviderTransitions() {
        // Urban canyon: alternate truthful / not every 2 s for 60 s.
        var truthful = true
        var transitions = 0
        var lastInject: Boolean? = null
        repeat(30) {
            truthful = !truthful
            val inject = MockLocationJob.shouldInjectWhenNoFix(truthful)
            if (lastInject != null && lastInject != inject) transitions++
            lastInject = inject
        }
        assertEquals(29, transitions)
    }

    @Test
    fun retentionAccuracy_growsDuringTruthLoss_defaultCeiling() {
        val base = 5f
        val at0 = MockRetentionAccuracy.horizontalM(base, 0L)
        val at60 = MockRetentionAccuracy.horizontalM(base, 60_000L)
        val at210 = MockRetentionAccuracy.horizontalM(base, 210_000L)
        assertEquals(base, at0, 1e-3f)
        assertTrue(at60 > at0)
        assertEquals(MockRetentionAccuracy.DEFAULT_CEILING_M, at210, 0.5f)
    }

    @Test
    fun retentionAccuracy_oneMeterCeiling_staysTooTightForNavigatorLoosen() {
        // #274 allows 1 m; navigators then keep treating DR as near-live.
        val ceiling = 1f
        assertEquals(1f, MockRetentionAccuracy.horizontalM(5f, 0L, ceilingM = ceiling), 0f)
        assertEquals(1f, MockRetentionAccuracy.horizontalM(5f, 210_000L, ceilingM = ceiling), 0f)
    }

    @Test
    fun roadMatchGnssTrust_isZeroWhileRetaining() {
        assertEquals(
            0f,
            RoadMatchGnssTrust.fromLive(liveGnss = false, accuracyM = 3f),
            0f,
        )
        assertTrue(
            RoadMatchGnssTrust.fromLive(liveGnss = true, accuracyM = 3f) > 0f,
        )
    }

    @Test
    fun fixRetentionWindow_isTenMinutes() {
        assertEquals(600_000L, MockLocationJob.FIX_RETENTION_MS)
    }
}
