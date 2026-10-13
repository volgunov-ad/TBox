package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorState

class AutomationRoadMatchSignalsTest {

    @Test
    fun interestIds_coverClassLimitAndTunnel() {
        assertEquals(
            setOf(
                AutomationSignalId.ROAD_HIGHWAY_CLASS,
                AutomationSignalId.ROAD_SPEED_LIMIT_KMH,
                AutomationSignalId.ROAD_TUNNEL,
            ),
            AutomationRoadMatchSignals.INTEREST_IDS,
        )
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_HIGHWAY_CLASS))
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_SPEED_LIMIT_KMH))
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_TUNNEL))
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
    fun tunnel_unavailableWithoutEdge() {
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.tunnelValue(RoadMatchAnchorState.EMPTY),
        )
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.tunnelValue(
                RoadMatchAnchorState(tunnel = true),
            ),
        )
    }

    @Test
    fun tunnel_onOffWhenMatched() {
        assertEquals(
            AutomationSignalValue.State("on"),
            AutomationRoadMatchSignals.tunnelValue(
                RoadMatchAnchorState(edgeId = 3L, tunnel = true),
            ),
        )
        assertEquals(
            AutomationSignalValue.State("off"),
            AutomationRoadMatchSignals.tunnelValue(
                RoadMatchAnchorState(edgeId = 3L, tunnel = false),
            ),
        )
        // Missing flag on a matched edge → outside tunnel (legacy packs).
        assertEquals(
            AutomationSignalValue.State("off"),
            AutomationRoadMatchSignals.tunnelValue(
                RoadMatchAnchorState(edgeId = 3L, tunnel = null),
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

        val tunnel = AutomationSignalCatalog.get(AutomationSignalId.ROAD_TUNNEL)
        assertEquals(setOf(AutomationSignalSource.APP), tunnel.sources)
        assertEquals(AutomationSignalValueType.STATE, tunnel.id.valueType)
        assertEquals(listOf("off", "on"), tunnel.stateOptions)
        assertEquals("Дорога: тоннель", tunnel.label)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.ROAD_TUNNEL),
        )
    }

    @Test
    fun highwayClassOptionLabels_coverCommonClasses() {
        assertEquals("Жилая зона", AutomationRoadMatchSignals.highwayClassOptionLabel("residential"))
        assertEquals("Автомагистраль", AutomationSignalCatalog.stateOptionLabel("motorway"))
    }

    @Test
    fun tunnelOptionLabels_useInsideOutsideCopy() {
        assertEquals(
            "В тоннеле / Inside",
            AutomationSignalCatalog.stateOptionLabel(AutomationSignalId.ROAD_TUNNEL, "on"),
        )
        assertEquals(
            "Вне тоннеля / Outside",
            AutomationSignalCatalog.stateOptionLabel(AutomationSignalId.ROAD_TUNNEL, "off"),
        )
        assertEquals(
            "Дорога: тоннель",
            AutomationParameterLabels.signalLabel(AutomationSignalId.ROAD_TUNNEL),
        )
    }

    @Test
    fun anchorFrom_mapsTunnelFromDebugWhenMatched() {
        val demand = vad.dashing.tbox.location.roadmatch.RoadMatchDemand(
            matchNeeded = true,
            correctPose = false,
        )
        val withTunnel = RoadMatchAnchorState.from(
            demand = demand,
            debug = vad.dashing.tbox.location.roadmatch.RoadMatchRuntime.DebugSnapshot(
                edgeId = 9L,
                highwayClass = "primary",
                tunnel = true,
            ),
            travelAgainstCoords = false,
        )
        assertEquals(true, withTunnel.tunnel)

        val missingFlag = RoadMatchAnchorState.from(
            demand = demand,
            debug = vad.dashing.tbox.location.roadmatch.RoadMatchRuntime.DebugSnapshot(
                edgeId = 9L,
                highwayClass = "primary",
                tunnel = null,
            ),
            travelAgainstCoords = false,
        )
        assertEquals(false, missingFlag.tunnel)

        val unmatched = RoadMatchAnchorState.from(
            demand = demand,
            debug = vad.dashing.tbox.location.roadmatch.RoadMatchRuntime.DebugSnapshot(
                tunnel = true,
            ),
            travelAgainstCoords = false,
        )
        assertEquals(null, unmatched.tunnel)
    }
}
