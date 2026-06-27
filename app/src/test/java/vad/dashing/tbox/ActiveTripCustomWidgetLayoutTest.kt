package vad.dashing.tbox

import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.ActiveTripCustomWidgetLayout

class ActiveTripCustomWidgetLayoutTest {

    @Test
    fun default_hasAllFieldsEnabled() {
        val layout = ActiveTripCustomWidgetLayout.default()
        assertTrue(layout.rows.all { it.enabled })
    }
}
