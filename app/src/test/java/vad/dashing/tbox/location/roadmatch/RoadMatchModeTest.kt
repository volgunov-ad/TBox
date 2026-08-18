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
    fun fromStorage_acceptsRailsAliases() {
        assertEquals(RoadMatchMode.RAILS, RoadMatchMode.fromStorage("RAILS"))
        assertEquals(RoadMatchMode.RAILS, RoadMatchMode.fromStorage("rails"))
        assertEquals(RoadMatchMode.RAILS, RoadMatchMode.fromStorage("RAIL"))
        assertEquals(RoadMatchMode.RAILS, RoadMatchMode.fromStorage(" rail "))
    }
}
