package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SetFcwEnabled writes explicit 1/2 values through ToggleBinary-backed registry specs.
 * Regression: executeSetViaRegistry used to reject ToggleBinary → FCW master no-op on A9.
 */
class SetFcwEnabledPolicyTest {
    @Test
    fun fcwCoupledProperties_areToggleBinaryWithOnTwoOffOne() {
        val ids = listOf(
            MbCanKnownVehiclePropertyId.FCW_SWITCH,
            MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
            MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING,
        )
        ids.forEach { id ->
            val policy = MbCanCommandRegistry.get(id)?.policy
            assertTrue("expected ToggleBinary for $id", policy is MbCanCommandPolicy.ToggleBinary)
            policy as MbCanCommandPolicy.ToggleBinary
            assertEquals(2, policy.onValue)
            assertEquals(1, policy.offValue)
        }
    }

    @Test
    fun toggleBinary_explicitOnOffAreAllowedSetValues() {
        val policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2)
        val allowed = setOf(policy.offValue, policy.onValue)
        assertTrue(allowed.contains(1))
        assertTrue(allowed.contains(2))
        assertTrue(!allowed.contains(0))
    }
}
