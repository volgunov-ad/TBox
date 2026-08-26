package vad.dashing.tbox.freeform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeformSupportEvaluationTest {

    @Test
    fun advertised_alone_is_enough() {
        assertTrue(
            FreeformSupportDecision.evaluate(
                advertised = true,
                adayoOrAndroid10Hu = false,
                canBuildActivityOptions = false,
            ),
        )
    }

    @Test
    fun adayoA10_withActivityOptions_is_supported() {
        assertTrue(
            FreeformSupportDecision.evaluate(
                advertised = false,
                adayoOrAndroid10Hu = true,
                canBuildActivityOptions = true,
            ),
        )
    }

    @Test
    fun adayoA10_withoutActivityOptions_is_unsupported() {
        assertFalse(
            FreeformSupportDecision.evaluate(
                advertised = false,
                adayoOrAndroid10Hu = true,
                canBuildActivityOptions = false,
            ),
        )
    }

    @Test
    fun noFlags_noAdayo_is_unsupported() {
        assertFalse(
            FreeformSupportDecision.evaluate(
                advertised = false,
                adayoOrAndroid10Hu = false,
                canBuildActivityOptions = true,
            ),
        )
    }
}
