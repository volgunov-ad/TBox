package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchTuningTest {
    @Test
    fun emptyOrBrokenJsonUsesProductionDefaults() {
        assertEquals(RoadMatchTuning.DEFAULT, RoadMatchTuning.fromJson(""))
        assertEquals(RoadMatchTuning.DEFAULT, RoadMatchTuning.fromJson("{broken"))
    }

    @Test
    fun roundTripPersistsOnlyOverrides() {
        val configured = RoadMatchTuning.DEFAULT
            .with(RoadMatchTuningKey.CROSS_BLEND, 0.65)
            .with(RoadMatchTuningKey.FREE_UNBIND_BEFORE_M, 48.0)

        val json = configured.toJson()
        val restored = RoadMatchTuning.fromJson(json)

        assertEquals(0.65, restored[RoadMatchTuningKey.CROSS_BLEND], 1e-6)
        assertEquals(48.0, restored[RoadMatchTuningKey.FREE_UNBIND_BEFORE_M], 1e-6)
        assertFalse(restored.isDefault())
        assertTrue(json.contains("\"crossBlend\""))
        assertFalse(json.contains("\"pathTriggerM\""))
    }

    @Test
    fun groupResetKeepsOtherGroups() {
        val configured = RoadMatchTuning.DEFAULT
            .with(RoadMatchTuningKey.CROSS_BLEND, 0.65)
            .with(RoadMatchTuningKey.FREE_UNBIND_BEFORE_M, 48.0)

        val reset = configured.reset(RoadMatchTuningGroup.FREE_TURNS)

        assertEquals(
            RoadMatchTuningKey.FREE_UNBIND_BEFORE_M.defaultValue,
            reset[RoadMatchTuningKey.FREE_UNBIND_BEFORE_M],
            1e-6,
        )
        assertEquals(0.65, reset[RoadMatchTuningKey.CROSS_BLEND], 1e-6)
        assertTrue(reset.isDefault(RoadMatchTuningGroup.FREE_TURNS))
        assertFalse(reset.isDefault(RoadMatchTuningGroup.COMMON))
    }

    @Test
    fun valuesAreClampedAndStepped() {
        val tuning = RoadMatchTuning.DEFAULT
            .with(RoadMatchTuningKey.CROSS_BLEND, 9.0)
            .with(RoadMatchTuningKey.MATCH_CADENCE_MS, 473.0)

        assertEquals(0.8, tuning[RoadMatchTuningKey.CROSS_BLEND], 1e-6)
        assertEquals(450.0, tuning[RoadMatchTuningKey.MATCH_CADENCE_MS], 1e-6)
    }

    @Test
    fun resetAllReturnsDefaultAndEmptyJsonOverrides() {
        val reset = RoadMatchTuning.DEFAULT
            .with(RoadMatchTuningKey.RAILS_SOFT_BLEND, 0.7)
            .reset()

        assertEquals(RoadMatchTuning.DEFAULT, reset)
        assertTrue(reset.isDefault())
        assertEquals(1, org.json.JSONObject(reset.toJson()).length())
    }

    @Test
    fun booleanKeysNormalizeToZeroOrOne() {
        val on = RoadMatchTuning.DEFAULT.withBool(RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED, true)
        val off = on.withBool(RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED, false)
        assertTrue(on.bool(RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED))
        assertFalse(off.bool(RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED))
        assertTrue(off.isDefault(RoadMatchTuningGroup.FREE_TURNS) || !off.bool(RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED))
    }

    @Test
    fun turnSignalDefaultsMatchProductionConstants() {
        assertEquals(
            -RoadMapMatcher.TURN_SIGNAL_TOWARD_BONUS,
            RoadMatchTuningKey.TS_TOWARD_BONUS.defaultValue,
            0.0,
        )
        assertEquals(
            RoadMapMatcher.TURN_SIGNAL_TOWARD_MIN_DEG.toDouble(),
            RoadMatchTuningKey.TS_TOWARD_MIN_DEG.defaultValue,
            0.0,
        )
        assertEquals(
            RoadMapMatcher.TURN_SIGNAL_STRAIGHT_PENALTY,
            RoadMatchTuningKey.TS_STRAIGHT_PENALTY.defaultValue,
            0.0,
        )
    }

    @Test
    fun shippedDefaultsMatchCurrentFreeTurnsProductionValues() {
        assertEquals(
            RoadMatchFreeTurnsMath.UNBIND_BEFORE_M,
            RoadMatchTuningKey.FREE_UNBIND_BEFORE_M.defaultValue,
            0.0,
        )
        assertEquals(
            RoadMatchFreeTurnsMath.REBIND_AFTER_M,
            RoadMatchTuningKey.FREE_REBIND_AFTER_M.defaultValue,
            0.0,
        )
        assertEquals(
            RoadMatchFreeTurnsMath.MAX_BEARING_STEP_CATCHUP_DEG.toDouble(),
            RoadMatchTuningKey.FREE_BEARING_CATCHUP_DEG.defaultValue,
            0.0,
        )
    }

    @Test
    fun pathOdoSyncDefaultsMatchProductionConstants() {
        assertEquals(1.0, RoadMatchTuningKey.PATH_ODO_SYNC_ENABLED.defaultValue, 0.0)
        assertEquals(
            RoadMatchRuntime.PATH_ODO_SYNC_MIN_GAP_M,
            RoadMatchTuningKey.PATH_ODO_SYNC_DEAD_M.defaultValue,
            0.0,
        )
        assertEquals(
            RoadMatchRuntime.PATH_ODO_SYNC_MAX_STEP_M,
            RoadMatchTuningKey.PATH_ODO_SYNC_MAX_STEP_M.defaultValue,
            0.0,
        )
    }
}
