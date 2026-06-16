package vad.dashing.tbox

import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.ActiveTripCustomWidgetLayout

class ActiveTripCustomWidgetLayoutTest {

    @Test
    fun default_showRowDividers_isTrue() {
        assertTrue(ActiveTripCustomWidgetLayout.default().showRowDividers)
        assertTrue(ActiveTripCustomWidgetLayout.defaultSimplified().showRowDividers)
    }
}
