package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VhalPushRatePolicyTest {
    @Test
    fun discrete_onChangeOnly_neverEscalatesTo1or5Hz() {
        val rates = VhalPushRatePolicy.candidates(VhalPushRatePolicy.ON_CHANGE_HZ)
        assertEquals(listOf(0.0f), rates)
        assertFalse(rates.any { it >= 1.0f })
    }

    @Test
    fun continuous_keepsHzFallback_withoutOnChange() {
        val rates = VhalPushRatePolicy.candidates(VhalPushRatePolicy.CONTINUOUS_HZ)
        assertEquals(listOf(1.0f, 5.0f), rates)
        assertTrue(rates.all { it > 0f })
    }
}
