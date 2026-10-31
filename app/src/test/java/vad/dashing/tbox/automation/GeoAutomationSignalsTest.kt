package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.location.GeoDisplayState
import vad.dashing.tbox.location.GeoSpeedSource
import vad.dashing.tbox.location.LocIndicatorState

class GeoAutomationSignalsTest {
    @Test
    fun hasDisplayData_liveAndRetainingOnly() {
        assertTrue(GeoAutomationSignals.hasDisplayData(liveState()))
        assertTrue(
            GeoAutomationSignals.hasDisplayData(
                liveState().copy(liveUsable = false, retaining = true, locateStatus = true),
            ),
        )
        assertEquals(
            LocIndicatorState.NONE,
            GeoDisplayState.EMPTY.indicator,
        )
        assertEquals(false, GeoAutomationSignals.hasDisplayData(GeoDisplayState.EMPTY))
        assertEquals(
            false,
            GeoAutomationSignals.hasDisplayData(
                liveState().copy(
                    liveUsable = false,
                    retaining = false,
                    locateStatus = true,
                    latitude = 55.0,
                    longitude = 37.0,
                ),
            ),
        )
    }

    @Test
    fun speedSourceKey_mapsEnumsAndNone() {
        assertEquals(
            "gnss",
            GeoAutomationSignals.speedSourceKey(liveState(speedSource = GeoSpeedSource.GNSS)),
        )
        assertEquals(
            "can",
            GeoAutomationSignals.speedSourceKey(liveState(speedSource = GeoSpeedSource.CAN)),
        )
        assertEquals(
            "retention",
            GeoAutomationSignals.speedSourceKey(liveState(speedSource = GeoSpeedSource.RETENTION)),
        )
        assertEquals("none", GeoAutomationSignals.speedSourceKey(GeoDisplayState.EMPTY))
    }

    @Test
    fun locationSourceKey_mapsAllSources() {
        assertEquals("tbox", GeoAutomationSignals.locationSourceKey(LocationSource.TBOX))
        assertEquals("companion", GeoAutomationSignals.locationSourceKey(LocationSource.ESP32))
        assertEquals("android", GeoAutomationSignals.locationSourceKey(LocationSource.ANDROID))
        assertEquals("usb", GeoAutomationSignals.locationSourceKey(LocationSource.USB))
        assertEquals("phone", GeoAutomationSignals.locationSourceKey(LocationSource.PHONE))
    }

    @Test
    fun enrichmentNumbers_unavailableWithoutDisplay() {
        assertEquals(AutomationSignalValue.Unavailable, GeoAutomationSignals.altitudeM(GeoDisplayState.EMPTY))
        assertEquals(AutomationSignalValue.Unavailable, GeoAutomationSignals.speedKmh(GeoDisplayState.EMPTY))
        assertEquals(AutomationSignalValue.Unavailable, GeoAutomationSignals.bearingDeg(GeoDisplayState.EMPTY))
        assertEquals(
            AutomationSignalValue.Unavailable,
            GeoAutomationSignals.satellitesVisible(GeoDisplayState.EMPTY),
        )
        assertEquals(
            AutomationSignalValue.Unavailable,
            GeoAutomationSignals.satellitesUsed(GeoDisplayState.EMPTY),
        )
    }

    @Test
    fun enrichmentNumbers_fromLiveSnapshot() {
        val state = liveState(
            altitude = 123.5,
            speedKmh = 48f,
            bearingDeg = 90f,
            visible = 12,
            used = 8,
        )
        assertEquals(AutomationSignalValue.Number(123.5), GeoAutomationSignals.altitudeM(state))
        assertEquals(AutomationSignalValue.Number(48.0), GeoAutomationSignals.speedKmh(state))
        assertEquals(AutomationSignalValue.Number(90.0), GeoAutomationSignals.bearingDeg(state))
        assertEquals(AutomationSignalValue.Number(12.0), GeoAutomationSignals.satellitesVisible(state))
        assertEquals(AutomationSignalValue.Number(8.0), GeoAutomationSignals.satellitesUsed(state))
        assertEquals(AutomationSignalValue.Unavailable, GeoAutomationSignals.bearingDeg(state.copy(bearingDeg = null)))
    }

    @Test
    fun gnssTruthfulState_mapsGnssTruthful() {
        assertEquals(
            AutomationSignalValue.State("on"),
            GeoAutomationSignals.gnssTruthfulState(liveState(gnssTruthful = true)),
        )
        assertEquals(
            AutomationSignalValue.State("off"),
            GeoAutomationSignals.gnssTruthfulState(liveState(gnssTruthful = false)),
        )
    }

    private fun liveState(
        altitude: Double = 100.0,
        speedKmh: Float = 40f,
        speedSource: GeoSpeedSource = GeoSpeedSource.GNSS,
        bearingDeg: Float? = 10f,
        visible: Int = 10,
        used: Int = 7,
        gnssTruthful: Boolean = true,
    ): GeoDisplayState = GeoDisplayState(
        liveUsable = true,
        retaining = false,
        locateStatus = true,
        latitude = 55.75,
        longitude = 37.62,
        altitude = altitude,
        speedKmh = speedKmh,
        speedSource = speedSource,
        bearingDeg = bearingDeg,
        hasReliableBearing = bearingDeg != null,
        visibleSatellites = visible,
        usingSatellites = used,
        gnssTruthful = gnssTruthful,
    )
}
