package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorState

class AutomationRoadMatchSignalsTest {

    @Test
    fun interestIds_coverClassLimitTunnelAndSettlement() {
        assertEquals(
            setOf(
                AutomationSignalId.ROAD_HIGHWAY_CLASS,
                AutomationSignalId.ROAD_SPEED_LIMIT_KMH,
                AutomationSignalId.ROAD_TUNNEL,
                AutomationSignalId.ROAD_SETTLEMENT,
            ),
            AutomationRoadMatchSignals.INTEREST_IDS,
        )
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_HIGHWAY_CLASS))
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_SPEED_LIMIT_KMH))
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_TUNNEL))
        assertTrue(AutomationRoadMatchSignals.isRoadMatchSignal(AutomationSignalId.ROAD_SETTLEMENT))
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
    fun settlement_unavailableWithoutEdge() {
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.settlementValue(RoadMatchAnchorState.EMPTY),
        )
        assertEquals(
            AutomationSignalValue.Unavailable,
            AutomationRoadMatchSignals.settlementValue(
                RoadMatchAnchorState(settlement = true),
            ),
        )
    }

    @Test
    fun settlement_onOffWhenMatched() {
        assertEquals(
            AutomationSignalValue.State("on"),
            AutomationRoadMatchSignals.settlementValue(
                RoadMatchAnchorState(edgeId = 4L, settlement = true),
            ),
        )
        assertEquals(
            AutomationSignalValue.State("off"),
            AutomationRoadMatchSignals.settlementValue(
                RoadMatchAnchorState(edgeId = 4L, settlement = false),
            ),
        )
        // Missing flag on a matched edge → outside settlement (legacy packs).
        assertEquals(
            AutomationSignalValue.State("off"),
            AutomationRoadMatchSignals.settlementValue(
                RoadMatchAnchorState(edgeId = 4L, settlement = null),
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

        val settlement = AutomationSignalCatalog.get(AutomationSignalId.ROAD_SETTLEMENT)
        assertEquals(setOf(AutomationSignalSource.APP), settlement.sources)
        assertEquals(AutomationSignalValueType.STATE, settlement.id.valueType)
        assertEquals(listOf("off", "on"), settlement.stateOptions)
        assertEquals("Дорога: населённый пункт", settlement.label)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.ROAD_SETTLEMENT),
        )
    }

    @Test
    fun highwayClassOptionLabels_coverCommonClasses() {
        assertEquals("Жилая зона", AutomationRoadMatchSignals.highwayClassOptionLabel("residential"))
        assertEquals("Автомагистраль", AutomationSignalCatalog.stateOptionLabel("motorway"))
    }

    @Test
    fun tunnelOptionLabels_useInsideOutsideForCondition_andEnterExitForTrigger() {
        assertEquals(
            "В тоннеле / Inside",
            AutomationSignalCatalog.stateOptionLabel(AutomationSignalId.ROAD_TUNNEL, "on"),
        )
        assertEquals(
            "Вне тоннеля / Outside",
            AutomationSignalCatalog.stateOptionLabel(AutomationSignalId.ROAD_TUNNEL, "off"),
        )
        assertEquals(
            "Въезд в тоннель / Enter tunnel",
            AutomationSignalCatalog.triggerStateOptionLabel(AutomationSignalId.ROAD_TUNNEL, "on"),
        )
        assertEquals(
            "Выезд из тоннеля / Exit tunnel",
            AutomationSignalCatalog.triggerStateOptionLabel(AutomationSignalId.ROAD_TUNNEL, "off"),
        )
        assertEquals(
            "on",
            AutomationSignalCatalog.defaultTriggerStateOption(AutomationSignalId.ROAD_TUNNEL),
        )
        assertEquals(
            "Дорога: тоннель",
            AutomationParameterLabels.signalLabel(AutomationSignalId.ROAD_TUNNEL),
        )
    }

    @Test
    fun settlementOptionLabels_useInsideOutsideForCondition_andEnterExitForTrigger() {
        assertEquals(
            "В НП / Inside",
            AutomationSignalCatalog.stateOptionLabel(AutomationSignalId.ROAD_SETTLEMENT, "on"),
        )
        assertEquals(
            "Вне НП / Outside",
            AutomationSignalCatalog.stateOptionLabel(AutomationSignalId.ROAD_SETTLEMENT, "off"),
        )
        assertEquals(
            "Въезд в НП / Enter settlement",
            AutomationSignalCatalog.triggerStateOptionLabel(AutomationSignalId.ROAD_SETTLEMENT, "on"),
        )
        assertEquals(
            "Выезд из НП / Exit settlement",
            AutomationSignalCatalog.triggerStateOptionLabel(AutomationSignalId.ROAD_SETTLEMENT, "off"),
        )
        assertEquals(
            "on",
            AutomationSignalCatalog.defaultTriggerStateOption(AutomationSignalId.ROAD_SETTLEMENT),
        )
        assertEquals(
            "Дорога: населённый пункт",
            AutomationParameterLabels.signalLabel(AutomationSignalId.ROAD_SETTLEMENT),
        )
    }

    @Test
    fun anchorFrom_mapsTunnelAndSettlementFromDebugWhenMatched() {
        val demand = vad.dashing.tbox.location.roadmatch.RoadMatchDemand(
            matchNeeded = true,
            correctPose = false,
        )
        val withFlags = RoadMatchAnchorState.from(
            demand = demand,
            debug = vad.dashing.tbox.location.roadmatch.RoadMatchRuntime.DebugSnapshot(
                edgeId = 9L,
                highwayClass = "primary",
                tunnel = true,
                settlement = true,
            ),
            travelAgainstCoords = false,
        )
        assertEquals(true, withFlags.tunnel)
        assertEquals(true, withFlags.settlement)

        val missingFlag = RoadMatchAnchorState.from(
            demand = demand,
            debug = vad.dashing.tbox.location.roadmatch.RoadMatchRuntime.DebugSnapshot(
                edgeId = 9L,
                highwayClass = "primary",
                tunnel = null,
                settlement = null,
            ),
            travelAgainstCoords = false,
        )
        assertEquals(false, missingFlag.tunnel)
        assertEquals(false, missingFlag.settlement)

        val unmatched = RoadMatchAnchorState.from(
            demand = demand,
            debug = vad.dashing.tbox.location.roadmatch.RoadMatchRuntime.DebugSnapshot(
                tunnel = true,
                settlement = true,
            ),
            travelAgainstCoords = false,
        )
        assertEquals(null, unmatched.tunnel)
        assertEquals(null, unmatched.settlement)
    }
}
