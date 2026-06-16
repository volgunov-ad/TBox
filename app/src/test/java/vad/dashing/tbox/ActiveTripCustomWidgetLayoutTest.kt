package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.trip.ActiveTripCustomWidgetLayout

class ActiveTripCustomWidgetLayoutTest {

    @Test
    fun default_labelColumnWidthPercent_isSixty() {
        assertEquals(
            ActiveTripCustomWidgetLayout.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT,
            ActiveTripCustomWidgetLayout.default().labelColumnWidthPercent,
        )
        assertEquals(
            ActiveTripCustomWidgetLayout.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT,
            ActiveTripCustomWidgetLayout.defaultSimplified().labelColumnWidthPercent,
        )
    }

    @Test
    fun normalizeLabelColumnWidthPercent_clampsToRange() {
        assertEquals(20, ActiveTripCustomWidgetLayout.normalizeLabelColumnWidthPercent(5))
        assertEquals(60, ActiveTripCustomWidgetLayout.normalizeLabelColumnWidthPercent(60))
        assertEquals(80, ActiveTripCustomWidgetLayout.normalizeLabelColumnWidthPercent(95))
    }
}
