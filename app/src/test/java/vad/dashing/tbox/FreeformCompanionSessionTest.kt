package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.freeform.FreeformCompanionSession
import vad.dashing.tbox.freeform.FreeformLaunchSide

class FreeformCompanionSessionTest {

    @Before
    fun clear() {
        FreeformCompanionSession.clear()
    }

    @Test
    fun set_storesSingleCompanionAndDisplaySize() {
        FreeformCompanionSession.set("com.a", FreeformLaunchSide.RIGHT, 40, 1920, 720)
        assertTrue(FreeformCompanionSession.isActive)
        assertTrue(FreeformCompanionSession.isActiveFor("com.a"))
        assertFalse(FreeformCompanionSession.isActiveFor("com.b"))
        assertEquals("com.a", FreeformCompanionSession.companionPackage())
        val state = FreeformCompanionSession.state.value!!
        assertEquals(FreeformLaunchSide.RIGHT, state.side)
        assertEquals(40, state.percent)
        assertEquals(1920, state.activityDisplayWidth)
        assertEquals(720, state.activityDisplayHeight)
    }

    @Test
    fun set_replacesPreviousCompanion() {
        FreeformCompanionSession.set("com.a", FreeformLaunchSide.LEFT, 50, 1000, 600)
        FreeformCompanionSession.set("com.b", FreeformLaunchSide.RIGHT, 35, 1920, 720)
        assertEquals("com.b", FreeformCompanionSession.companionPackage())
        assertFalse(FreeformCompanionSession.isActiveFor("com.a"))
        assertEquals(FreeformLaunchSide.RIGHT, FreeformCompanionSession.state.value!!.side)
        assertEquals(35, FreeformCompanionSession.state.value!!.percent)
        assertEquals(1920, FreeformCompanionSession.state.value!!.activityDisplayWidth)
    }

    @Test
    fun set_emptyPackage_clears() {
        FreeformCompanionSession.set("com.a", FreeformLaunchSide.TOP, 50, 1000, 600)
        FreeformCompanionSession.set("  ", FreeformLaunchSide.BOTTOM, 50, 1000, 600)
        assertFalse(FreeformCompanionSession.isActive)
        assertNull(FreeformCompanionSession.companionPackage())
    }

    @Test
    fun clear_resetsSession() {
        FreeformCompanionSession.set("com.a", FreeformLaunchSide.LEFT, 50, 1000, 600)
        FreeformCompanionSession.clear()
        assertFalse(FreeformCompanionSession.isActive)
        assertNull(FreeformCompanionSession.state.value)
    }
}
