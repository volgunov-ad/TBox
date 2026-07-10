package vad.dashing.tbox.mbcan

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_FOLD
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_UNFOLD

class MirrorFoldCommandTest {
    @Test
    fun mirrorFoldSwitchPolicy_allowsStockPulseValues() {
        val spec = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH)
        assertNotNull(spec)
        val policy = spec!!.policy as MbCanCommandPolicy.SetExact
        assertTrue(policy.allowedValues.contains(MIRROR_FOLD_SWITCH_VALUE_FOLD))
        assertTrue(policy.allowedValues.contains(MIRROR_FOLD_SWITCH_VALUE_UNFOLD))
    }

    @Test
    fun firmwareMapper_resolvesMirrorFoldWriteId() {
        val vhalId = FirmwareVehicleJsonMapper.resolveWritePropertyId(
            MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH
        )
        assertNotNull(vhalId)
        assertTrue(vhalId == 289412705)
    }
}
