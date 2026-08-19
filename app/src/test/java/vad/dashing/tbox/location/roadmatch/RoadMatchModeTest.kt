package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Test

class RoadMatchModeTest {

    @Test
    fun fromStorage_defaultsToOrdinary() {
        assertEquals(RoadMatchMode.ORDINARY, RoadMatchMode.fromStorage(null))
        assertEquals(RoadMatchMode.ORDINARY, RoadMatchMode.fromStorage(""))
        assertEquals(RoadMatchMode.ORDINARY, RoadMatchMode.fromStorage("ordinary"))
        assertEquals(RoadMatchMode.ORDINARY, RoadMatchMode.fromStorage("garbage"))
    }

    @Test
    fun fromStorage_acceptsFreeTurnsAliases() {
        assertEquals(RoadMatchMode.FREE_TURNS, RoadMatchMode.fromStorage("FREE_TURNS"))
        assertEquals(RoadMatchMode.FREE_TURNS, RoadMatchMode.fromStorage("freeturns"))
        assertEquals(RoadMatchMode.FREE_TURNS, RoadMatchMode.fromStorage("FREE"))
        assertEquals(RoadMatchMode.FREE_TURNS, RoadMatchMode.fromStorage(" free "))
    }
}
