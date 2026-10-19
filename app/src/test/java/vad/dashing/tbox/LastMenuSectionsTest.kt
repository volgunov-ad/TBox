package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.ui.LastMenuSections
import vad.dashing.tbox.ui.LeftMenuTabField

class LastMenuSectionsTest {

    @Test
    fun resolveIndex_missingOrUnknown_returnsFirst() {
        val keys = listOf("Data", "Settings")
        assertEquals(0, LastMenuSections.resolveIndex(null, keys))
        assertEquals(0, LastMenuSections.resolveIndex("", keys))
        assertEquals(0, LastMenuSections.resolveIndex("Missing", keys))
        assertEquals(0, LastMenuSections.resolveIndex("Settings", emptyList()))
    }

    @Test
    fun resolveIndex_knownKey_returnsMatchingIndex() {
        val keys = listOf("General", "Mock", "Cameras", "DataDebug")
        assertEquals(0, LastMenuSections.resolveIndex("General", keys))
        assertEquals(1, LastMenuSections.resolveIndex("Mock", keys))
        assertEquals(3, LastMenuSections.resolveIndex("DataDebug", keys))
    }

    @Test
    fun serializeParse_roundTrip_preservesEntries() {
        val map = mapOf(
            LeftMenuTabField.MODEM.id to "Settings",
            LeftMenuTabField.GEOPOSITION.id to "Mock",
        )
        val parsed = LastMenuSections.parse(LastMenuSections.serialize(map))
        assertEquals(map, parsed)
    }

    @Test
    fun parse_blankOrInvalid_returnsEmpty() {
        assertTrue(LastMenuSections.parse(null).isEmpty())
        assertTrue(LastMenuSections.parse("").isEmpty())
        assertTrue(LastMenuSections.parse("not-json").isEmpty())
    }
}
