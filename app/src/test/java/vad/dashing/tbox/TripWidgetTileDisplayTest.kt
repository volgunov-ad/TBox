package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.trip.TripWidgetTileDisplay

class TripWidgetTileDisplayTest {

    @Test
    fun floatingDashboardWidgetConfig_tripDisplayDefaults() {
        val cfg = FloatingDashboardWidgetConfig(dataKey = "activeTripWidgetCustom")
        assertEquals(TripWidgetTileDisplay.DEFAULT_SHOW_ROW_DIVIDERS, cfg.tripWidgetShowRowDividers)
        assertEquals(
            TripWidgetTileDisplay.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT,
            cfg.tripWidgetLabelColumnWidthPercent,
        )
    }

    @Test
    fun normalizeLabelColumnWidthPercent_clampsToRange() {
        assertEquals(20, TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(5))
        assertEquals(60, TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(60))
        assertEquals(80, TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(95))
    }
}
