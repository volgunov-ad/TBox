package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAppWidgetCellTest {

    @Test
    fun notReadyUntilBothSidesArePositive() {
        assertFalse(isExternalAppWidgetCellReady(0, 0))
        assertFalse(isExternalAppWidgetCellReady(120, 0))
        assertFalse(isExternalAppWidgetCellReady(0, 80))
        assertFalse(isExternalAppWidgetCellReady(-1, 80))
        assertTrue(isExternalAppWidgetCellReady(120, 80))
    }
}
