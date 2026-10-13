package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorState

class AutomationRoadMatchSignalsTest {

    @Test
    fun interestIds_coverClassAndLimitOnly() {
        assertEquals(
            setOf(
                AutomationSignalId.ROAD_HIGHWAY_CLASS,
                AutomationSignalId.ROAD_SPEED_LIMIT_KMH,
            ),
            AutomationRoadMatchSignals.INTEREST_IDS,
        )
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_HIGHWAY_CLASS))
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_SPEED_LIMIT_KMH))
        assertFalse(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.CAR_SPEED))
        assertFalse(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.GEO_POSITION))
    }

    @Test
    fun highwayClass_unavailableWithoutEdge() {
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.highwayClassValue(RoadMatchAnchorState.EMPTY),
        )
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.highwayClassValue(
                RoadMatchAnchorState(highwayClass = "primary"),
            ),
        )
    }

    @Test
    fun highwayClass_normalizesMatchedEdge() {
        assertEquals(
            AutomationSignalValue.State("primary"),
            AutomationRoadMatchSignals.highwayClassValue(
                RoadMatchAnchorState(edgeId = 7L, highwayClass = " Primary "),
            ),
        )
    }

    @Test
    fun speedLimit_unavailableWithoutEdgeOrPostedLimit() {
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.speedLimitKmhValue(RoadMatchAnchorState.EMPTY),
        )
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.speedLimitKmhValue(
                RoadMatchAnchorState(edgeId = 1L, currentLimitKmh = null),
            ),
        )
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.speedLimitKmhValue(
                RoadMatchAnchorState(edgeId = 1L, currentLimitKmh = 0),
            ),
        )
    }

    @Test
    fun speedLimit_numberWhenMatched() {
        assertEquals(
            AutomationSignalValue.Number(60.0),
            AutomationRoadMatchSignals.speedLimitKmhValue(
                RoadMatchAnchorState(edgeId = 2L, currentLimitKmh = 60),
            ),
        )
    }

    @Test
    fun catalog_registersAppOnlyRoadSignals() {
        val highway = AutomationSignalCatalog.get(AutomationSignalId.ROAD_HIGHWAY_CLASS)
        assertEquals(setOf(AutomationSignalSource.APP), highway.sources)
        assertEquals(AutomationSignalValueType.STATE, highway.id.valueType)
        assertTrue(highway.stateOptions.contains("residential"))
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.ROAD_HIGHWAY_CLASS),
        )

        val limit = AutomationSignalCatalog.get(AutomationSignalId.ROAD_SPEED_LIMIT_KMH)
        assertEquals(setOf(AutomationSignalSource.APP), limit.sources)
        assertEquals(AutomationSignalValueType.NUMBER, limit.id.valueType)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.ROAD_SPEED_LIMIT_KMH),
        )
    }

    @Test
    fun highwayClassOptionLabels_coverCommonClasses() {
        assertEquals("Жилая зона", AutomationRoadMatchSignals.highwayClassOptionLabel("residential"))
        assertEquals("Автомагистраль", AutomationSignalCatalog.stateOptionLabel("motorway"))
    }
}
