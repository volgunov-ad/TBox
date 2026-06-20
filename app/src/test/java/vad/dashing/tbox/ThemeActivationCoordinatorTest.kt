package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeActivationCoordinatorTest {

    @Test
    fun markMainScreenUiReady_setsUiReadyFlag() {
        ThemeActivationCoordinator.markMainScreenUiReady()
        assertTrue(ThemeActivationCoordinator.mainScreenUiReadyFlow.value)
    }

    @Test
    fun themeActivationInProgress_startsFalse() {
        assertFalse(ThemeActivationCoordinator.themeActivationInProgress)
    }
}
