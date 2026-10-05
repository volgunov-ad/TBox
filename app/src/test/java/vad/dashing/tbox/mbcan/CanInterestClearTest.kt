package vad.dashing.tbox.mbcan

import org.junit.Assert.assertTrue
import org.junit.Test

class CanInterestClearTest {
    @Test
    fun uiDisposeDebounceIsShortNotMinutes() {
        assertTrue(CanInterestClear.UI_DISPOSE_DEBOUNCE_MS in 500L..5_000L)
    }
}
