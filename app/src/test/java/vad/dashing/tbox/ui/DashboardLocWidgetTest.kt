package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.LocValues

class DashboardLocWidgetTest {

    @Test
    fun satellitesTextUsesGnssCountsAndAlwaysShowsFraction() {
        assertEquals(
            "18/12",
            locWidgetSatellitesText(
                LocValues(visibleSatellites = 18, usingSatellites = 12),
            ),
        )
        assertEquals(
            "12/12",
            locWidgetSatellitesText(
                LocValues(visibleSatellites = 12, usingSatellites = 12),
            ),
        )
        assertEquals(
            "0/0",
            locWidgetSatellitesText(LocValues()),
        )
    }
}
