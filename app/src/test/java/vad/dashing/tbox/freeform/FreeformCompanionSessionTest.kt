package vad.dashing.tbox.freeform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.MainScreenWindowModeGeometry

class FreeformCompanionSessionTest {

    @Before
    fun clearSession() {
        FreeformCompanionSession.clear()
        MainScreenWindowOverlayLayout.clear()
    }

    @Test
    fun overlayCrop_defaultsFalse() {
        FreeformCompanionSession.set(
            packageName = "com.example.app",
            side = FreeformLaunchSide.LEFT,
            percent = 40,
            activityDisplayWidth = 1280,
            activityDisplayHeight = 720,
            activityDisplayId = 0,
        )
        assertTrue(FreeformCompanionSession.isActive)
        assertFalse(FreeformCompanionSession.isOverlayCrop)
    }

    @Test
    fun overlayCrop_flagTracked() {
        FreeformCompanionSession.set(
            packageName = "com.example.app",
            side = FreeformLaunchSide.RIGHT,
            percent = 50,
            activityDisplayWidth = 1280,
            activityDisplayHeight = 720,
            activityDisplayId = 5,
            overlayCrop = true,
        )
        assertTrue(FreeformCompanionSession.isOverlayCrop)
        assertTrue(FreeformCompanionSession.isActiveFor("com.example.app"))
        FreeformCompanionSession.clear()
        assertFalse(FreeformCompanionSession.isOverlayCrop)
        assertFalse(FreeformCompanionSession.isActive)
    }
}

class MainScreenWindowOverlayLayoutTest {

    @Before
    fun clear() {
        MainScreenWindowOverlayLayout.clear()
    }

    @Test
    fun update_setsCropViewportAndContentOffset() {
        MainScreenWindowOverlayLayout.update(
            cropEnabled = true,
            fullWidthPx = 1000,
            fullHeightPx = 600,
            geometry = MainScreenWindowModeGeometry(400, 0, 600, 600),
        )
        val state = MainScreenWindowOverlayLayout.state.value
        assertTrue(state.cropEnabled)
        assertEquals(1000, state.fullWidthPx)
        assertEquals(600, state.fullHeightPx)
        assertEquals(400, state.originXPx)
        assertEquals(0, state.originYPx)
        assertEquals(-400, MainScreenWindowOverlayLayout.contentOffsetX(state))
        assertEquals(0, MainScreenWindowOverlayLayout.contentOffsetY(state))
    }

    @Test
    fun cropViewportForCompanion_rightApp_leftOverlayOriginZero() {
        val crop = MainScreenWindowOverlayLayout.cropViewportForCompanion(
            activityWidthPx = 1320,
            activityHeightPx = 856,
            side = FreeformLaunchSide.RIGHT,
            percent = 50,
        )
        assertEquals(0, crop.originXPx)
        assertEquals(0, crop.originYPx)
        assertEquals(1320, crop.fullWidthPx)
        assertEquals(0, MainScreenWindowOverlayLayout.contentOffsetX(crop))
    }

    @Test
    fun cropViewportForCompanion_leftApp_rightOverlayShiftsBySplit() {
        val crop = MainScreenWindowOverlayLayout.cropViewportForCompanion(
            activityWidthPx = 1320,
            activityHeightPx = 856,
            side = FreeformLaunchSide.LEFT,
            percent = 70,
        )
        // Companion 70% → split 924; TBox from x=924
        assertEquals(924, crop.originXPx)
        assertEquals(0, crop.originYPx)
        assertEquals(-924, MainScreenWindowOverlayLayout.contentOffsetX(crop))
    }

    @Test
    fun clear_resetsState() {
        MainScreenWindowOverlayLayout.update(
            cropEnabled = true,
            fullWidthPx = 800,
            fullHeightPx = 480,
            geometry = MainScreenWindowModeGeometry(100, 50, 200, 200),
        )
        MainScreenWindowOverlayLayout.clear()
        val state = MainScreenWindowOverlayLayout.state.value
        assertFalse(state.cropEnabled)
        assertEquals(0, state.originXPx)
        assertEquals(0, state.originYPx)
    }
}
